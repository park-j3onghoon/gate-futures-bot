import argparse
import logging
from datetime import datetime, timezone

from gatebot.bootstrap import build_exchange
from gatebot.domain.model import Candle, Interval
from gatebot.service_layer import market_data

_INTERVAL_CODES = [i.value for i in Interval]


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)-5s %(name)s - %(message)s",
    )
    exchange = build_exchange()
    candles = market_data.get_candles(
        exchange, args.contract, Interval(args.interval), limit=args.limit
    )
    _print_candles(args.contract, args.interval, candles)
    return 0


def _parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(prog="gatebot", description="Gate.io 선물 캔들 차트 조회")
    parser.add_argument("contract", nargs="?", default="BTC_USDT", help="계약 (예: BTC_USDT)")
    parser.add_argument(
        "interval", nargs="?", default="1m", choices=_INTERVAL_CODES, help="캔들 간격"
    )
    parser.add_argument("--limit", type=int, default=10, help="최근 N개 (기본 10)")
    return parser.parse_args(argv)


def _print_candles(contract: str, interval: str, candles: list[Candle]) -> None:
    if not candles:
        print(f"(빈 응답) {contract} {interval}")
        return
    print(f"# {contract} {interval} — {len(candles)}개")
    print(f"{'time(UTC)':<20}{'open':>12}{'high':>12}{'low':>12}{'close':>12}{'vol':>10}")
    for c in candles:
        ts = datetime.fromtimestamp(c.timestamp, tz=timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
        print(f"{ts:<20}{c.open:>12}{c.high:>12}{c.low:>12}{c.close:>12}{c.volume:>10}")
