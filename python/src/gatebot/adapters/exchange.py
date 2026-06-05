import abc
from decimal import Decimal

from gate_api import (
    FuturesApi,
    FuturesCandlestick,
    FuturesInitialOrder,
    FuturesOrder,
    FuturesPriceTrigger,
    FuturesPriceTriggeredOrder,
    TriggerOrderResponse,
)
from gate_api import Position as SdkPosition
from gate_api.exceptions import ApiException, GateApiException

from gatebot.domain.exceptions import (
    AuthenticationError,
    GateError,
    InsufficientBalanceError,
    MarketDataError,
    OrderError,
    RateLimitError,
)
from gatebot.domain.model import Candle, Interval, OrderResult, Position

_MARKET_PRICE = "0"

# 가격 트리거 주문(TP/SL) SDK 상수 — 값의 의미는 gate_api 모델에 고정돼 있어 매직넘버로 두지 않는다.
_STRATEGY_PRICE = 0  # FuturesPriceTrigger.strategy_type: 0=가격 트리거(유일 지원)
_PRICE_TYPE_LAST = 0  # price_type: 0=최종체결가 / 1=mark / 2=index
_RULE_GTE = 1  # 계산가 ≥ trigger price (등록 시점 trigger price > last_price 필요)
_RULE_LTE = 2  # 계산가 ≤ trigger price (등록 시점 trigger price < last_price 필요)
_CLOSE_LONG = "close-long-position"  # 롱 포지션 전량 청산 트리거
_CLOSE_SHORT = "close-short-position"  # 숏 포지션 전량 청산 트리거
_DRY_RUN_TRIGGER_ID = -1  # dry-run 트리거 sentinel — 실제 id로 오인 방지, 롤백 cancel 스킵


class AbstractExchange(abc.ABC):
    @abc.abstractmethod
    def get_candles(
        self,
        contract: str,
        interval: Interval,
        limit: int = 0,
        from_sec: int = 0,
        to_sec: int = 0,
    ) -> list[Candle]:
        raise NotImplementedError

    @abc.abstractmethod
    def get_position(self, contract: str) -> Position | None:
        raise NotImplementedError

    @abc.abstractmethod
    def get_last_price(self, contract: str) -> str:
        raise NotImplementedError

    @abc.abstractmethod
    def create_order(self, contract: str, size: int, leverage: int) -> OrderResult:
        raise NotImplementedError

    @abc.abstractmethod
    def create_trigger_order(
        self,
        contract: str,
        trigger_price: float,
        is_long: bool,
        is_take_profit: bool,
        price_type: int = _PRICE_TYPE_LAST,
    ) -> int:
        raise NotImplementedError

    @abc.abstractmethod
    def cancel_trigger_order(self, trigger_id: int) -> None:
        raise NotImplementedError

    @abc.abstractmethod
    def close_position(self, contract: str) -> OrderResult:
        raise NotImplementedError


class GateExchange(AbstractExchange):
    """gate-api SDK를 격리하는 어댑터. SDK 타입·예외는 이 경계를 넘지 않는다."""

    def __init__(self, futures_api: FuturesApi, settle: str) -> None:
        self._futures_api = futures_api
        self._settle = settle

    def get_candles(
        self,
        contract: str,
        interval: Interval,
        limit: int = 0,
        from_sec: int = 0,
        to_sec: int = 0,
    ) -> list[Candle]:
        # limit는 from/to와 상호배타 (둘 중 하나라도 주면 SDK가 limit 요청을 거부)
        kwargs: dict[str, object] = {"interval": interval.value}
        if limit:
            kwargs["limit"] = limit
        if from_sec:
            kwargs["_from"] = from_sec
        if to_sec:
            kwargs["to"] = to_sec
        raw = self._call(
            lambda: self._futures_api.list_futures_candlesticks(self._settle, contract, **kwargs),
            f"get_candles(contract={contract})",
        )
        return [self._to_candle(c) for c in raw]

    def get_position(self, contract: str) -> Position | None:
        # Gate는 미보유 컨트랙트에 POSITION_NOT_FOUND(400)를 던진다(빈 size=0 Position이 아니라).
        # Kotlin은 size=0만 처리 → 이 호스트(api-testnet.gateapi.io)에선 예외로 샌다. 둘 다 None.
        sdk_pos = self._call(
            lambda: self._futures_api.get_position(self._settle, contract),
            f"get_position(contract={contract})",
            none_on_labels=("POSITION_NOT_FOUND",),
        )
        if sdk_pos is None:
            return None
        position = self._to_position(sdk_pos)
        return position if position.size != 0 else None

    def create_order(self, contract: str, size: int, leverage: int) -> OrderResult:
        # Kotlin GateExchangeAdapter 패턴: leverage 먼저 설정 후 주문.
        # update_position_leverage 응답은 사용하지 않지만 실패 시 예외 전파.
        # isolated 마진(leverage>0)에선 cross_leverage_limit을 비워야 한다 (Gate API 규칙).
        # 함께 보내면 testnet이 "cross_leverage_limit only for cross-margin"으로 거부한다.
        # Kotlin 레퍼런스(GateClient.updateLeverage)는 "0"을 넘기지만 — 동일 버그라 미러하지 않음.
        self._call(
            lambda: self._futures_api.update_position_leverage(
                self._settle,
                contract,
                str(leverage),
            ),
            f"update_leverage(contract={contract}, leverage={leverage})",
        )
        order = FuturesOrder(
            contract=contract,
            size=str(size),  # Python SDK는 size를 str로 받음 (Kotlin은 Long)
            price=_MARKET_PRICE,
            tif="ioc",
        )
        result = self._call(
            lambda: self._futures_api.create_futures_order(self._settle, order),
            f"create_order(contract={contract}, size={size})",
        )
        return self._to_order_result(result)

    def close_position(self, contract: str) -> OrderResult:
        # close=True면 size는 무시되지만 SDK 모델이 None이면 직렬화에서 누락될 수 있어 "0" 명시
        order = FuturesOrder(
            contract=contract,
            size="0",
            price=_MARKET_PRICE,
            tif="ioc",
            close=True,
        )
        result = self._call(
            lambda: self._futures_api.create_futures_order(self._settle, order),
            f"close_position(contract={contract})",
        )
        return self._to_order_result(result)

    def get_last_price(self, contract: str) -> str:
        # 트리거 rule 불변식이 last_price 기준이라(rule=1→trigger>last, 2→trigger<last)
        # 캔들 마감가가 아니라 ticker.last를 권위 가격으로 쓴다.
        tickers = self._call(
            lambda: self._futures_api.list_futures_tickers(self._settle, contract=contract),
            f"get_last_price(contract={contract})",
        ) or []
        last = tickers[0].last if tickers else None
        # last="0"은 미보유/비정상 — 검증을 무력화(0이면 항상 통과)하므로 거부
        if last in (None, "", "0"):
            raise MarketDataError(f"ticker.last 사용 불가: contract={contract}, last={last!r}")
        try:
            float(last)
        except (TypeError, ValueError) as e:
            raise MarketDataError(f"ticker.last 변환 실패: contract={contract}, last={last!r}") from e
        return last

    def create_trigger_order(
        self,
        contract: str,
        trigger_price: float,
        is_long: bool,
        is_take_profit: bool,
        price_type: int = _PRICE_TYPE_LAST,
    ) -> int:
        # 포지션 전량 청산 트리거: initial.size=0 + order_type=close-*-position (one-way 전제).
        order = FuturesPriceTriggeredOrder(
            initial=FuturesInitialOrder(
                contract=contract,
                size=0,
                price=_MARKET_PRICE,
                tif="ioc",
            ),
            trigger=FuturesPriceTrigger(
                strategy_type=_STRATEGY_PRICE,
                price_type=price_type,
                price=self._format_price(trigger_price),
                rule=self._trigger_rule(is_long, is_take_profit),
            ),
            order_type=self._close_order_type(is_long),
        )
        result = self._call(
            lambda: self._futures_api.create_price_triggered_order(self._settle, order),
            f"create_trigger_order(contract={contract}, price={trigger_price}, "
            f"is_long={is_long}, is_tp={is_take_profit})",
        )
        return self._to_trigger_id(result)

    def cancel_trigger_order(self, trigger_id: int) -> None:
        self._call(
            lambda: self._futures_api.cancel_price_triggered_order(self._settle, trigger_id),
            f"cancel_trigger_order(trigger_id={trigger_id})",
        )

    # ---- helpers ----

    def _call(self, action, context: str, *, none_on_labels: tuple[str, ...] = ()):
        try:
            return action()
        except GateApiException as e:
            # 일부 라벨은 에러가 아니라 "결과 없음" (get_position의 POSITION_NOT_FOUND)
            if e.label in none_on_labels:
                return None
            raise self._map_gate_exception(e, context) from e
        except ApiException as e:
            raise OrderError(f"[{context}] Gate.io API 호출 실패: {e}") from e

    @staticmethod
    def _map_gate_exception(e: GateApiException, context: str) -> GateError:
        # Kotlin GateClient.mapGateException 미러 — 라벨 기반 도메인 예외 분기.
        label = e.label or ""
        message = e.message or str(e)
        prefix = f"[{context}] {message}"
        if label == "INVALID_KEY":
            return AuthenticationError(prefix)
        if label == "BALANCE_NOT_ENOUGH":
            return InsufficientBalanceError(prefix)
        if label == "RATE_LIMIT":
            return RateLimitError(prefix)
        return OrderError(prefix)

    @staticmethod
    def _to_candle(c: FuturesCandlestick) -> Candle:
        if None in (c.t, c.o, c.h, c.l, c.c):
            raise MarketDataError(f"캔들 응답 필수 필드 누락: {c}")
        try:
            timestamp = int(c.t)
            volume = int(c.v) if c.v is not None else 0
        except (TypeError, ValueError) as e:
            raise MarketDataError(f"캔들 수치 변환 실패: {c}") from e
        return Candle(
            timestamp=timestamp,
            open=c.o,
            high=c.h,
            low=c.l,
            close=c.c,
            volume=volume,
        )

    @staticmethod
    def _to_position(p: SdkPosition) -> Position:
        try:
            size = int(p.size) if p.size is not None else 0
            # leverage SDK 응답이 "5" 또는 "5.0" 가능 — float 경유 후 int
            leverage = int(float(p.leverage)) if p.leverage is not None else 0
        except (TypeError, ValueError) as e:
            raise MarketDataError(f"포지션 수치 변환 실패: size={p.size}, leverage={p.leverage}") from e
        return Position(
            contract=p.contract or "",
            size=size,
            entry_price=p.entry_price or "0",
            leverage=leverage,
            unrealised_pnl=p.unrealised_pnl or "0",
            realised_pnl=p.realised_pnl or "0",
        )

    @staticmethod
    def _to_order_result(o: FuturesOrder) -> OrderResult:
        if o.id is None or o.contract is None or o.size is None:
            raise OrderError("주문 응답 필수 필드 누락: id/contract/size")
        try:
            size = int(o.size)
        except (TypeError, ValueError) as e:
            raise OrderError(f"주문 size 변환 실패: {o.size}") from e
        return OrderResult(
            id=int(o.id),
            contract=o.contract,
            size=size,
            price=o.price or "0",
            status=o.status or "unknown",
            fill_price=o.fill_price or "0",
            create_time=float(o.create_time) if o.create_time is not None else 0.0,
        )

    @staticmethod
    def _trigger_rule(is_long: bool, is_take_profit: bool) -> int:
        # 롱TP·숏SL=가격 상승 도달(GTE), 롱SL·숏TP=가격 하락 도달(LTE)
        return _RULE_GTE if is_long == is_take_profit else _RULE_LTE

    @staticmethod
    def _close_order_type(is_long: bool) -> str:
        return _CLOSE_LONG if is_long else _CLOSE_SHORT

    @staticmethod
    def _to_trigger_id(result: TriggerOrderResponse | None) -> int:
        if result is None or result.id is None:
            raise OrderError("트리거 주문 응답에 id 없음")
        return int(result.id)

    @staticmethod
    def _format_price(price: float) -> str:
        # normalize=꼬리0 제거(77777.0→77777), format(,'f')=정수 과학표기(1E+5) 방지
        return format(Decimal(str(price)).normalize(), "f")


class DryRunExchange(AbstractExchange):
    """write 호출은 출력만, 실제 SDK 호출은 하지 않는 데코레이터.

    read 호출(get_candles, get_position)은 inner로 위임 — dry-run에서도
    실제 시장·포지션 상태를 봐야 의미 있는 검증이 된다.
    """

    def __init__(self, inner: AbstractExchange) -> None:
        self._inner = inner

    def get_candles(
        self,
        contract: str,
        interval: Interval,
        limit: int = 0,
        from_sec: int = 0,
        to_sec: int = 0,
    ) -> list[Candle]:
        return self._inner.get_candles(contract, interval, limit, from_sec, to_sec)

    def get_position(self, contract: str) -> Position | None:
        return self._inner.get_position(contract)

    def get_last_price(self, contract: str) -> str:
        return self._inner.get_last_price(contract)

    def create_order(self, contract: str, size: int, leverage: int) -> OrderResult:
        print(
            f"[DRY-RUN] create_order(contract={contract}, size={size}, leverage={leverage}) "
            f"— 실제 SDK 호출 안 함"
        )
        # 체결을 시뮬레이션(fill_price=현재가)해 후속 TP/SL 트리거 흐름까지 보여준다.
        return _dry_run_order(contract, size, fill_price=self._inner.get_last_price(contract))

    def create_trigger_order(
        self,
        contract: str,
        trigger_price: float,
        is_long: bool,
        is_take_profit: bool,
        price_type: int = _PRICE_TYPE_LAST,
    ) -> int:
        kind = "TP" if is_take_profit else "SL"
        print(
            f"[DRY-RUN] create_trigger_order({kind} contract={contract}, price={trigger_price}, "
            f"rule={GateExchange._trigger_rule(is_long, is_take_profit)}, price_type={price_type}, "
            f"{GateExchange._close_order_type(is_long)}) — 실제 SDK 호출 안 함"
        )
        return _DRY_RUN_TRIGGER_ID

    def cancel_trigger_order(self, trigger_id: int) -> None:
        print(f"[DRY-RUN] cancel_trigger_order(trigger_id={trigger_id}) — 실제 SDK 호출 안 함")

    def close_position(self, contract: str) -> OrderResult:
        print(f"[DRY-RUN] close_position(contract={contract}) — 실제 SDK 호출 안 함")
        return _dry_run_order(contract, 0)


def _dry_run_order(contract: str, size: int, fill_price: str = "0") -> OrderResult:
    return OrderResult(
        id=0,
        contract=contract,
        size=size,
        price="0",
        status="dry-run",
        fill_price=fill_price,
        create_time=0.0,
    )
