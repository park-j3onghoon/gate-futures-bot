from dataclasses import dataclass
from enum import Enum


class Interval(str, Enum):
    SEC_10 = "10s"
    MIN_1 = "1m"
    MIN_5 = "5m"
    MIN_15 = "15m"
    MIN_30 = "30m"
    HOUR_1 = "1h"
    HOUR_4 = "4h"
    HOUR_8 = "8h"
    DAY_1 = "1d"
    DAY_7 = "7d"
    DAY_30 = "30d"


@dataclass(frozen=True)
class Candle:
    # OHLC는 정밀도 보존을 위해 str로 둔다 (SDK 응답 그대로). 계산은 close_price에서 변환.
    timestamp: int
    open: str
    high: str
    low: str
    close: str
    volume: int

    @property
    def close_price(self) -> float:
        return float(self.close)


@dataclass(frozen=True)
class Position:
    contract: str
    size: int  # 양수=롱, 음수=숏. SDK는 str로 주지만 도메인은 정수.
    entry_price: str  # 정밀도 보존
    leverage: int
    unrealised_pnl: str
    realised_pnl: str


@dataclass(frozen=True)
class OrderResult:
    id: int
    contract: str
    size: int
    price: str
    status: str
    fill_price: str
    create_time: float
