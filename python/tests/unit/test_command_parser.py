import pytest

from gatebot.domain.exceptions import ParseError
from gatebot.domain.model import Side
from gatebot.entrypoints.command_parser import parse_order_command


def test_parses_full_long_command():
    cmd = parse_order_command("Btcusdt / 시장가 / 롱 / 9배 / tp:77777 / sl:66666 / size:2")
    assert cmd.contract == "BTC_USDT"
    assert cmd.side is Side.LONG
    assert cmd.leverage == 9
    assert cmd.size == 2
    assert cmd.take_profit == 77777.0
    assert cmd.stop_loss == 66666.0


def test_parses_short_with_english_synonyms():
    cmd = parse_order_command("ETHUSDT / market / sell / 5x / size:1")
    assert cmd.contract == "ETH_USDT"
    assert cmd.side is Side.SHORT
    assert cmd.leverage == 5
    assert cmd.size == 1
    assert cmd.take_profit is None and cmd.stop_loss is None


@pytest.mark.parametrize(
    "raw_contract, expected",
    [
        ("BTCUSDT", "BTC_USDT"),
        ("Btcusdt", "BTC_USDT"),
        ("BTC_USDT", "BTC_USDT"),
        ("1000PEPEUSDT", "1000PEPE_USDT"),
    ],
)
def test_normalizes_contract(raw_contract, expected):
    cmd = parse_order_command(f"{raw_contract} / 롱 / 3배 / size:1")
    assert cmd.contract == expected


def test_position_independent_and_whitespace_tolerant():
    cmd = parse_order_command("size:1/3배/롱/btcusdt/tp : 110/sl : 90")
    assert cmd.contract == "BTC_USDT"
    assert cmd.side is Side.LONG
    assert cmd.leverage == 3
    assert cmd.size == 1
    assert cmd.take_profit == 110.0
    assert cmd.stop_loss == 90.0


def test_buy_is_long_synonym():
    assert parse_order_command("btcusdt / buy / 2배 / size:1").side is Side.LONG


def test_missing_size_raises():
    with pytest.raises(ParseError):
        parse_order_command("btcusdt / 롱 / 3배")


def test_missing_direction_raises():
    with pytest.raises(ParseError):
        parse_order_command("btcusdt / 3배 / size:1")


def test_size_zero_raises():
    with pytest.raises(ParseError):
        parse_order_command("btcusdt / 롱 / 3배 / size:0")


def test_duplicate_token_raises():
    with pytest.raises(ParseError):
        parse_order_command("btcusdt / 롱 / 3배 / size:1 / size:2")


def test_non_numeric_tp_raises():
    with pytest.raises(ParseError):
        parse_order_command("btcusdt / 롱 / 3배 / size:1 / tp:abc")


def test_fractional_leverage_raises():
    with pytest.raises(ParseError):
        parse_order_command("btcusdt / 롱 / 9.5배 / size:1")


def test_limit_order_type_raises():
    with pytest.raises(ParseError):
        parse_order_command("btcusdt / 지정가 / 롱 / 3배 / size:1")


@pytest.mark.parametrize("bad_token", ["sl;66666", "tp=77777", "레버리지9배"])
def test_unconsumed_token_raises(bad_token):
    """[Sec10] 어느 카테고리에도 안 맞는 토큰이 섞이면 거부 — 손절 침묵 누락 방지."""
    with pytest.raises(ParseError):
        parse_order_command(f"btcusdt / 롱 / 3배 / size:1 / {bad_token}")
