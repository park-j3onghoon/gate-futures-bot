import pytest

from gatebot.domain.exceptions import CommandError
from gatebot.domain.model import OrderCommand, Side


def _cmd(
    *,
    contract: str = "BTC_USDT",
    side: Side = Side.LONG,
    size: int = 1,
    leverage: int = 3,
    take_profit: float | None = None,
    stop_loss: float | None = None,
) -> OrderCommand:
    return OrderCommand(contract, side, size, leverage, take_profit, stop_loss)


def test_long_tp_above_sl_is_valid():
    """롱: tp > sl 이면 정상 생성."""
    cmd = _cmd(side=Side.LONG, take_profit=77777.0, stop_loss=66666.0)
    assert cmd.take_profit == 77777.0
    assert cmd.stop_loss == 66666.0


def test_long_tp_not_above_sl_raises():
    """롱: tp <= sl 이면 CommandError (등호 포함)."""
    with pytest.raises(CommandError):
        _cmd(side=Side.LONG, take_profit=66666.0, stop_loss=77777.0)
    with pytest.raises(CommandError):
        _cmd(side=Side.LONG, take_profit=70000.0, stop_loss=70000.0)


def test_short_tp_below_sl_is_valid():
    """숏: tp < sl 이면 정상 생성 (방향 반대)."""
    cmd = _cmd(side=Side.SHORT, take_profit=66666.0, stop_loss=77777.0)
    assert cmd.side is Side.SHORT


def test_short_tp_not_below_sl_raises():
    """숏: tp >= sl 이면 CommandError."""
    with pytest.raises(CommandError):
        _cmd(side=Side.SHORT, take_profit=77777.0, stop_loss=66666.0)
    with pytest.raises(CommandError):
        _cmd(side=Side.SHORT, take_profit=70000.0, stop_loss=70000.0)


def test_one_sided_tp_or_sl_skips_ordering_check():
    """한쪽만 주어지면 순서 불변식 검사 안 함 (진입가 비교는 service 몫)."""
    assert _cmd(side=Side.LONG, take_profit=77777.0).stop_loss is None
    assert _cmd(side=Side.SHORT, stop_loss=66666.0).take_profit is None


def test_no_tp_sl_is_valid():
    """tp/sl 둘 다 없어도 도메인 불변식 통과 (네이키드 경고는 service/CLI 책임)."""
    cmd = _cmd()
    assert cmd.take_profit is None and cmd.stop_loss is None


def test_non_positive_size_raises():
    """size <= 0 거부."""
    with pytest.raises(CommandError):
        _cmd(size=0)
    with pytest.raises(CommandError):
        _cmd(size=-1)


def test_non_positive_leverage_raises():
    """leverage <= 0 거부."""
    with pytest.raises(CommandError):
        _cmd(leverage=0)
    with pytest.raises(CommandError):
        _cmd(leverage=-1)
