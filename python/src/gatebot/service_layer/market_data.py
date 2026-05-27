import logging

from gatebot.adapters.exchange import AbstractExchange
from gatebot.domain.exceptions import MarketDataError
from gatebot.domain.model import Candle, Interval

logger = logging.getLogger(__name__)


def get_candles(
    exchange: AbstractExchange,
    contract: str,
    interval: Interval,
    limit: int = 0,
    from_sec: int = 0,
    to_sec: int = 0,
) -> list[Candle]:
    candles = exchange.get_candles(contract, interval, limit, from_sec, to_sec)
    if not candles:
        logger.warning(
            "빈 캔들 응답: contract=%s, interval=%s, limit=%s",
            contract,
            interval.value,
            limit,
        )
    return candles


def get_latest_price(
    exchange: AbstractExchange,
    contract: str,
    interval: Interval = Interval.MIN_1,
) -> float:
    candles = exchange.get_candles(contract, interval, limit=1)
    if not candles:
        raise MarketDataError(
            f"캔들 데이터가 없습니다: contract={contract}, interval={interval.value}"
        )
    return candles[-1].close_price
