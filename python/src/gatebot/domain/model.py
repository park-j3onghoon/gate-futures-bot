from dataclasses import dataclass
from enum import Enum

from gatebot.domain.exceptions import CommandError


class Side(str, Enum):
    LONG = "long"
    SHORT = "short"


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


@dataclass(frozen=True)
class OrderCommand:
    contract: str
    side: Side
    size: int  # 양의 magnitude; 부호(숏=음수)는 service _signed_size가 부여
    leverage: int
    take_profit: float | None = None
    stop_loss: float | None = None

    def __post_init__(self) -> None:
        if not isinstance(self.size, int) or self.size <= 0:
            raise CommandError(f"size는 양의 정수여야 합니다: {self.size}")
        if not isinstance(self.leverage, int) or self.leverage <= 0:
            raise CommandError(f"leverage는 양의 정수여야 합니다: {self.leverage}")
        tp, sl = self.take_profit, self.stop_loss
        # 둘 다 있을 때만 순수 순서 검사. 진입가 끼인 3자 비교(롱 tp>entry>sl)는 service 책임.
        if tp is not None and sl is not None:
            if self.side is Side.LONG and tp <= sl:
                raise CommandError(f"롱은 tp({tp}) > sl({sl}) 여야 합니다")
            if self.side is Side.SHORT and tp >= sl:
                raise CommandError(f"숏은 tp({tp}) < sl({sl}) 여야 합니다")


@dataclass(frozen=True)
class PlacedOrder:
    entry: OrderResult
    filled: bool  # IOC 체결 여부 — 미체결(False)이면 트리거 미등록
    sl_trigger_id: int | None
    tp_trigger_id: int | None
