import logging

from gatebot.adapters.exchange import AbstractExchange
from gatebot.domain.exceptions import GateError, OrderError, PositionError
from gatebot.domain.model import OrderCommand, OrderResult, PlacedOrder, Side

logger = logging.getLogger(__name__)

# 학습 단계 안전장치 — 코드 버그가 큰 손실로 번지는 걸 막는 하드 캡.
DEFAULT_MAX_SIZE = 5
DEFAULT_LEVERAGE = 3  # 미지정 시 기본 레버리지
DEFAULT_MAX_LEVERAGE = 10  # 허용 상한 (기본값과 분리 — "기본이 조용히 상한이 되는" 잠복버그 차단)


def open_position(
    exchange: AbstractExchange,
    command: OrderCommand,
    *,
    max_size: int = DEFAULT_MAX_SIZE,
    max_leverage: int = DEFAULT_MAX_LEVERAGE,
) -> PlacedOrder:
    _validate_caps(command.size, command.leverage, max_size, max_leverage)
    _require_no_position(exchange, command.contract)
    entry = float(exchange.get_last_price(command.contract))
    if entry <= 0:
        raise OrderError(f"현재가가 유효하지 않습니다: contract={command.contract}, entry={entry}")
    _validate_tp_sl(command.side, entry, command.take_profit, command.stop_loss)

    signed = _signed_size(command.side, command.size)
    logger.info(
        "진입: contract=%s, side=%s, size=%d, leverage=%d",
        command.contract,
        command.side.value,
        signed,
        command.leverage,
    )
    entry_result = exchange.create_order(command.contract, signed, command.leverage)

    # 체결 판정은 주문 응답(fill_price)이 권위 — get_position은 거래소 전파 지연으로
    # "체결인데 None"을 반환할 수 있어 트리거 누락(조용한 네이키드 포지션)을 부른다.
    if not _is_filled(entry_result):
        logger.warning(
            "IOC 미체결 — 트리거 미등록: contract=%s, fill_price=%s",
            command.contract,
            entry_result.fill_price,
        )
        return PlacedOrder(entry_result, filled=False, sl_trigger_id=None, tp_trigger_id=None)

    # 등록 직전 재조회 — SDK rule이 등록시점 last_price 기준이라 슬리피지로 tp/sl이 교차하면 거부된다.
    # 흔한 거부를 선제 차단하는 최적화이고, 최종 안전망은 아래 트리거 실패 시 롤백.
    last_price = float(exchange.get_last_price(command.contract))
    crossed = _tp_sl_violation(command.side, last_price, command.take_profit, command.stop_loss)
    if crossed:
        _rollback(exchange, command.contract, [])
        raise OrderError(f"체결 후 가격 변동으로 tp/sl 교차 — 롤백 완료: {crossed}")

    sl_trigger_id: int | None = None
    tp_trigger_id: int | None = None
    try:
        if command.stop_loss is not None:
            sl_trigger_id = _place_exit_trigger(
                exchange, command.contract, command.side, command.stop_loss, is_tp=False
            )
        if command.take_profit is not None:
            tp_trigger_id = _place_exit_trigger(
                exchange, command.contract, command.side, command.take_profit, is_tp=True
            )
    except GateError as e:
        _rollback(exchange, command.contract, [sl_trigger_id, tp_trigger_id])
        raise OrderError(f"보호 트리거 등록 실패 — 롤백 완료: {e}") from e

    return PlacedOrder(
        entry_result, filled=True, sl_trigger_id=sl_trigger_id, tp_trigger_id=tp_trigger_id
    )


def open_long(
    exchange: AbstractExchange,
    contract: str,
    size: int,
    leverage: int = DEFAULT_LEVERAGE,
    max_size: int = DEFAULT_MAX_SIZE,
    max_leverage: int = DEFAULT_MAX_LEVERAGE,
) -> OrderResult:
    # 롱 전용 진입 — 음수 size가 숏으로 새지 않도록 양수 가드 (부호 일반화는 open_position에만).
    if size <= 0:
        raise OrderError(f"open_long size는 양수여야 합니다: {size}")
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


def _signed_size(side: Side, size: int) -> int:
    return size if side is Side.LONG else -size


def _validate_caps(size: int, leverage: int, max_size: int, max_leverage: int) -> None:
    if size == 0:
        raise OrderError("size는 0일 수 없습니다")
    if abs(size) > max_size:
        raise OrderError(f"size({size}) 절대값이 안전 캡({max_size}) 초과 — 학습 단계 거부")
    if leverage <= 0 or leverage > max_leverage:
        raise OrderError(f"leverage({leverage})가 허용 범위(1~{max_leverage}) 밖")


def _validate_tp_sl(
    side: Side, price: float, take_profit: float | None, stop_loss: float | None
) -> None:
    violation = _tp_sl_violation(side, price, take_profit, stop_loss)
    if violation:
        raise PositionError(violation)


def _tp_sl_violation(
    side: Side, price: float, take_profit: float | None, stop_loss: float | None
) -> str | None:
    # 진입가 기준 방향 검사. 둘 다 있으면 3자(롱 tp>price>sl), 단측이면 해당 쪽만 검사.
    # 등호는 거부 — SDK rule이 "trigger price가 last_price와 strict 대소"를 요구하기 때문.
    if take_profit is not None:
        if side is Side.LONG and take_profit <= price:
            return f"롱 tp({take_profit})는 현재가({price})보다 높아야 합니다"
        if side is Side.SHORT and take_profit >= price:
            return f"숏 tp({take_profit})는 현재가({price})보다 낮아야 합니다"
    if stop_loss is not None:
        if side is Side.LONG and stop_loss >= price:
            return f"롱 sl({stop_loss})은 현재가({price})보다 낮아야 합니다"
        if side is Side.SHORT and stop_loss <= price:
            return f"숏 sl({stop_loss})은 현재가({price})보다 높아야 합니다"
    return None


def _require_no_position(exchange: AbstractExchange, contract: str) -> None:
    current = exchange.get_position(contract)
    if current is not None:
        raise PositionError(
            f"이미 포지션이 존재: contract={contract}, size={current.size}, "
            f"entry={current.entry_price}, leverage={current.leverage}"
        )


def _is_filled(entry_result: OrderResult) -> bool:
    # IOC 시장가 응답의 fill_price가 곧 체결 여부의 권위 신호 ("0"/빈값=미체결).
    return entry_result.fill_price not in ("0", "")


def _place_exit_trigger(
    exchange: AbstractExchange, contract: str, side: Side, price: float, is_tp: bool
) -> int:
    return exchange.create_trigger_order(
        contract, price, is_long=(side is Side.LONG), is_take_profit=is_tp
    )


def _rollback(exchange: AbstractExchange, contract: str, trigger_ids: list[int | None]) -> None:
    try:
        for trigger_id in trigger_ids:
            if trigger_id is not None and trigger_id >= 0:  # dry-run sentinel(-1) 스킵
                exchange.cancel_trigger_order(trigger_id)
        try:
            exchange.close_position(contract)
        except PositionError:
            pass  # 이미 청산됨(POSITION_NOT_FOUND) → 롤백 성공 간주. cancel 실패는 흡수 안 함.
    except GateError as ce:
        raise OrderError(
            f"네이키드 포지션 가능 — 수동 개입 필요: contract={contract}, "
            f"롤백실패={ce}, 등록됐던 trigger_ids={trigger_ids}"
        ) from ce
