import abc

from gate_api import FuturesApi, FuturesCandlestick
from gate_api.exceptions import ApiException, GateApiException

from gatebot.domain.exceptions import MarketDataError
from gatebot.domain.model import Candle, Interval


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
        # limit는 from/to와 상호배타 (둘 중 하나라도 주면 SDK가 limit 요청을 거부) — 호출자가 선택
        kwargs: dict[str, object] = {"interval": interval.value}
        if limit:
            kwargs["limit"] = limit
        if from_sec:
            kwargs["_from"] = from_sec
        if to_sec:
            kwargs["to"] = to_sec
        try:
            raw = self._futures_api.list_futures_candlesticks(self._settle, contract, **kwargs)
        except (GateApiException, ApiException) as e:
            raise MarketDataError(
                f"캔들 조회 실패: contract={contract}, interval={interval.value}"
            ) from e
        return [self._to_candle(c) for c in raw]

    @staticmethod
    def _to_candle(c: FuturesCandlestick) -> Candle:
        if None in (c.t, c.o, c.h, c.l, c.c):
            raise MarketDataError(f"캔들 응답 필수 필드 누락: {c}")
        # SDK는 t=float, v=str로 주는데 도메인은 정수. 변환 실패(빈 문자열·비정수)도
        # SDK 경계를 넘지 않도록 도메인 예외로 감싼다.
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
