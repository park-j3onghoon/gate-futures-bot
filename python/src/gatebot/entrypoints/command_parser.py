import math
import re

from gatebot.domain.exceptions import ParseError
from gatebot.domain.model import OrderCommand, Side

# 슬래시 DSL 토큰을 위치 무관하게 분류하는 정규식. 모두 선형(중첩 수량자 없음) — ReDoS 무관.
_CONTRACT = re.compile(r"^([A-Za-z0-9]+?)_?USDT$", re.IGNORECASE)
_MARKET = re.compile(r"^(?:시장가|시장|market)$", re.IGNORECASE)
_LIMIT = re.compile(r"^(?:지정가|limit)$", re.IGNORECASE)
_LONG = re.compile(r"^(?:롱|long|buy)$", re.IGNORECASE)
_SHORT = re.compile(r"^(?:숏|short|sell)$", re.IGNORECASE)
_LEVERAGE = re.compile(r"^(\d+)\s*(?:배|x)?$", re.IGNORECASE)  # 소수(9.5배)는 거부
_TP = re.compile(r"^tp\s*:\s*(.+)$", re.IGNORECASE)
_SL = re.compile(r"^sl\s*:\s*(.+)$", re.IGNORECASE)
_SIZE = re.compile(r"^size\s*:\s*(.+)$", re.IGNORECASE)

_REQUIRED = ("contract", "direction", "leverage", "size")


def parse_order_command(raw: str) -> OrderCommand:
    fields: dict[str, object] = {}
    for token in (t.strip() for t in raw.split("/")):
        if not token:
            continue
        matches = _classify(token)
        if not matches:
            raise ParseError(f"알 수 없는 토큰: {token!r}")
        if len(matches) > 1:  # 방어 — 현재 정규식 집합에선 도달 불가하나 침묵 오분류는 막는다
            raise ParseError(f"토큰이 여러 의미로 해석됨: {token!r} → {[c for c, _ in matches]}")
        category, value = matches[0]
        if category == "limit":
            raise ParseError("시장가만 지원합니다 (지정가 미지원)")
        if category == "market":
            continue  # 시장가는 기본 동작 — 확인용 토큰으로 소비만
        if category in fields:
            raise ParseError(f"중복된 항목: {category} ({fields[category]!r}, {value!r})")
        fields[category] = value

    missing = [c for c in _REQUIRED if c not in fields]
    if missing:
        raise ParseError(f"필수 항목 누락: {missing}")

    # _classify가 카테고리별 타입을 보장하므로 경계에서 좁힌다 (type: ignore 떡칠 대신 명시 검증).
    contract, side = fields["contract"], fields["direction"]
    size, leverage = fields["size"], fields["leverage"]
    take_profit, stop_loss = fields.get("tp"), fields.get("sl")
    assert isinstance(contract, str) and isinstance(side, Side)
    assert isinstance(size, int) and isinstance(leverage, int)
    assert take_profit is None or isinstance(take_profit, float)
    assert stop_loss is None or isinstance(stop_loss, float)
    return OrderCommand(contract, side, size, leverage, take_profit, stop_loss)


def _classify(token: str) -> list[tuple[str, object]]:
    matches: list[tuple[str, object]] = []
    contract = _CONTRACT.match(token)
    if contract:
        matches.append(("contract", f"{contract.group(1).upper()}_USDT"))
    if _MARKET.match(token):
        matches.append(("market", token))
    if _LIMIT.match(token):
        matches.append(("limit", token))
    if _LONG.match(token):
        matches.append(("direction", Side.LONG))
    if _SHORT.match(token):
        matches.append(("direction", Side.SHORT))
    leverage = _LEVERAGE.match(token)
    if leverage:
        matches.append(("leverage", int(leverage.group(1))))
    tp = _TP.match(token)
    if tp:
        matches.append(("tp", _to_float(tp.group(1), "tp")))
    sl = _SL.match(token)
    if sl:
        matches.append(("sl", _to_float(sl.group(1), "sl")))
    size = _SIZE.match(token)
    if size:
        matches.append(("size", _to_size(size.group(1))))
    return matches


def _to_float(raw: str, field: str) -> float:
    try:
        value = float(raw.strip())
    except ValueError:
        raise ParseError(f"{field} 값이 숫자가 아닙니다: {raw!r}") from None
    # inf/nan은 float()를 통과하지만 모든 비교를 무력화 → SDK에 'Infinity'/'NaN'으로 새지 않게 거부
    if not math.isfinite(value):
        raise ParseError(f"{field} 값이 유효한 가격이 아닙니다: {raw!r}")
    return value


def _to_size(raw: str) -> int:
    try:
        value = int(raw.strip())
    except ValueError:
        raise ParseError(f"size 값이 정수가 아닙니다: {raw!r}") from None
    if value <= 0:
        raise ParseError(f"size는 양의 정수여야 합니다: {value}")
    return value
