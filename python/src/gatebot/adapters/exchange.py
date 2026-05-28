import abc

from gate_api import FuturesApi, FuturesCandlestick, FuturesOrder
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
# Kotlin DEFAULT_CROSS_LEVERAGE_LIMIT — Gate.io update_position_leverage 호출 시 그대로 전달.
# isolated 모드 leverage 설정 패턴 (Kotlin GateClient.updateLeverage 미러)
_DEFAULT_CROSS_LEVERAGE_LIMIT = "0"


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
    def create_order(self, contract: str, size: int, leverage: int) -> OrderResult:
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
        sdk_pos = self._call(
            lambda: self._futures_api.get_position(self._settle, contract),
            f"get_position(contract={contract})",
        )
        position = self._to_position(sdk_pos)
        # size=0(또는 None→0)은 "포지션 없음"으로 본다 (Kotlin GateClient 동일)
        return position if position.size != 0 else None

    def create_order(self, contract: str, size: int, leverage: int) -> OrderResult:
        # Kotlin GateExchangeAdapter 패턴: leverage 먼저 설정 후 주문.
        # update_position_leverage 응답은 사용하지 않지만 실패 시 예외 전파.
        self._call(
            lambda: self._futures_api.update_position_leverage(
                self._settle,
                contract,
                str(leverage),
                cross_leverage_limit=_DEFAULT_CROSS_LEVERAGE_LIMIT,
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

    # ---- helpers ----

    def _call(self, action, context: str):
        try:
            return action()
        except GateApiException as e:
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

    def create_order(self, contract: str, size: int, leverage: int) -> OrderResult:
        print(
            f"[DRY-RUN] create_order(contract={contract}, size={size}, leverage={leverage}) "
            f"— 실제 SDK 호출 안 함"
        )
        return _dry_run_order(contract, size)

    def close_position(self, contract: str) -> OrderResult:
        print(f"[DRY-RUN] close_position(contract={contract}) — 실제 SDK 호출 안 함")
        return _dry_run_order(contract, 0)


def _dry_run_order(contract: str, size: int) -> OrderResult:
    return OrderResult(
        id=0,
        contract=contract,
        size=size,
        price="0",
        status="dry-run",
        fill_price="0",
        create_time=0.0,
    )
