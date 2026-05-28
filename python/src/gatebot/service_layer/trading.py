import logging

from gatebot.adapters.exchange import AbstractExchange
from gatebot.domain.exceptions import OrderError, PositionError
from gatebot.domain.model import OrderResult

logger = logging.getLogger(__name__)

# 학습 단계 안전장치 — 코드 버그가 큰 손실로 번지는 걸 막는 하드 캡.
# 실제로 더 큰 값이 필요해지면 명시적으로 인자로 풀어줘야 한다.
DEFAULT_MAX_SIZE = 5
DEFAULT_MAX_LEVERAGE = 3


def open_long(
    exchange: AbstractExchange,
    contract: str,
    size: int,
    leverage: int = DEFAULT_MAX_LEVERAGE,
    max_size: int = DEFAULT_MAX_SIZE,
    max_leverage: int = DEFAULT_MAX_LEVERAGE,
) -> OrderResult:
    _validate_caps(size, leverage, max_size, max_leverage)
    _require_no_position(exchange, contract)
    logger.info("롱 진입: contract=%s, size=%d, leverage=%d", contract, size, leverage)
    return exchange.create_order(contract, size, leverage)


def close(exchange: AbstractExchange, contract: str) -> OrderResult:
    position = exchange.get_position(contract)
    if position is None:
        raise PositionError(f"청산할 포지션 없음: contract={contract}")
    direction = "롱" if position.size > 0 else "숏"
    logger.info(
        "%s 포지션 청산: contract=%s, size=%d, entry=%s",
        direction,
        contract,
        position.size,
        position.entry_price,
    )
    return exchange.close_position(contract)


def _validate_caps(size: int, leverage: int, max_size: int, max_leverage: int) -> None:
    if size <= 0:
        raise OrderError(f"size는 양수여야 합니다: {size}")
    if size > max_size:
        raise OrderError(f"size({size})가 안전 캡({max_size}) 초과 — 학습 단계 거부")
    if leverage <= 0 or leverage > max_leverage:
        raise OrderError(f"leverage({leverage})가 허용 범위(1~{max_leverage}) 밖")


def _require_no_position(exchange: AbstractExchange, contract: str) -> None:
    current = exchange.get_position(contract)
    if current is not None:
        raise PositionError(
            f"이미 포지션이 존재: contract={contract}, size={current.size}, "
            f"entry={current.entry_price}, leverage={current.leverage}"
        )
