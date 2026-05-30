import argparse
import logging
import sys
from datetime import datetime, timezone

from gatebot.adapters.exchange import AbstractExchange
from gatebot.bootstrap import build_exchange
from gatebot.domain.exceptions import AuthenticationError, GateError
from gatebot.domain.model import Candle, Interval, Position
from gatebot.service_layer import market_data, trading

_INTERVAL_CODES = [i.value for i in Interval]


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)-5s %(name)s - %(message)s",
    )
    if args.dry_run:
        print("⚠️  DRY-RUN 모드 — write 호출은 실제로 전송되지 않습니다.")
    exchange = build_exchange(dry_run=args.dry_run)

    handlers = {
        "fetch": _cmd_fetch,
        "pos": _cmd_pos,
        "buy": _cmd_buy,
        "close": _cmd_close,
    }
    # 도메인 예외는 버그가 아니라 예상된 운영 상황(인증·잔고·레이트리밋 등).
    # traceback 대신 사용자용 메시지 + 비정상 종료 코드로 보고한다.
    try:
        return handlers[args.cmd](exchange, args)
    except GateError as e:
        return _report_error(e)


def _parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(prog="gatebot", description="Gate.io 선물 봇")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="write 호출(매수·청산·leverage)을 실제로 보내지 않고 출력만",
    )
    sub = parser.add_subparsers(dest="cmd", required=True, metavar="명령")

    p_fetch = sub.add_parser("fetch", help="캔들 조회 (인증 불필요)")
    p_fetch.add_argument("contract", nargs="?", default="BTC_USDT")
    p_fetch.add_argument("interval", nargs="?", default="1m", choices=_INTERVAL_CODES)
    p_fetch.add_argument("--limit", type=int, default=10)

    p_pos = sub.add_parser("pos", help="현재 포지션 조회 (인증 필요)")
    p_pos.add_argument("contract", nargs="?", default="BTC_USDT")

    p_buy = sub.add_parser("buy", help="롱 진입 — 실거래 (인증 필요)")
    p_buy.add_argument("contract", nargs="?", default="BTC_USDT")
    p_buy.add_argument("--size", type=int, required=True, help="계약 수 (양수, 안전 캡 5)")
    p_buy.add_argument("--leverage", type=int, default=3, help="레버리지 (기본 3, 안전 캡 3)")

    p_close = sub.add_parser("close", help="포지션 청산 (인증 필요)")
    p_close.add_argument("contract", nargs="?", default="BTC_USDT")

    return parser.parse_args(argv)


def _cmd_fetch(exchange: AbstractExchange, args: argparse.Namespace) -> int:
    candles = market_data.get_candles(
        exchange, args.contract, Interval(args.interval), limit=args.limit
    )
    _print_candles(args.contract, args.interval, candles)
    return 0


def _cmd_pos(exchange: AbstractExchange, args: argparse.Namespace) -> int:
    pos = exchange.get_position(args.contract)
    if pos is None:
        print(f"포지션 없음: {args.contract}")
        return 0
    _print_position(pos)
    return 0


def _cmd_buy(exchange: AbstractExchange, args: argparse.Namespace) -> int:
    result = trading.open_long(exchange, args.contract, args.size, args.leverage)
    print(f"주문 응답: id={result.id}, status={result.status}, fill_price={result.fill_price}")
    # 매수 직후 포지션 검증 — 의도한 size·entry로 들어갔는지 즉시 확인
    pos = exchange.get_position(args.contract)
    if pos:
        _print_position(pos)
    else:
        print("⚠️  주문 응답은 받았으나 포지션이 조회되지 않습니다 (체결 실패·지연 가능).")
    return 0


def _cmd_close(exchange: AbstractExchange, args: argparse.Namespace) -> int:
    result = trading.close(exchange, args.contract)
    print(f"청산 응답: id={result.id}, status={result.status}, fill_price={result.fill_price}")
    pos = exchange.get_position(args.contract)
    if pos is None:
        print(f"포지션 없음 확인: {args.contract}")
    else:
        print(f"⚠️  청산 후에도 포지션 잔존: size={pos.size}")
    return 0


def _print_candles(contract: str, interval: str, candles: list[Candle]) -> None:
    if not candles:
        print(f"(빈 응답) {contract} {interval}")
        return
    print(f"# {contract} {interval} — {len(candles)}개")
    print(f"{'time(UTC)':<20}{'open':>12}{'high':>12}{'low':>12}{'close':>12}{'vol':>10}")
    for c in candles:
        ts = datetime.fromtimestamp(c.timestamp, tz=timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
        print(f"{ts:<20}{c.open:>12}{c.high:>12}{c.low:>12}{c.close:>12}{c.volume:>10}")


def _print_position(pos: Position) -> None:
    direction = "롱" if pos.size > 0 else "숏"
    print(f"# 포지션: {pos.contract}")
    print(f"  방향: {direction} (size={pos.size})")
    print(f"  entry_price: {pos.entry_price}")
    print(f"  leverage: {pos.leverage}x")
    print(f"  unrealised_pnl: {pos.unrealised_pnl}")
    print(f"  realised_pnl: {pos.realised_pnl}")


def _report_error(e: GateError) -> int:
    print(f"❌ {type(e).__name__}: {e}", file=sys.stderr)
    if isinstance(e, AuthenticationError):
        print(
            "   → testnet과 메인넷은 API 키 저장소가 분리돼 있습니다. 현재 host 환경에서 "
            "발급한 키인지 확인하세요. (testnet 키는 'Futures TestNet APIKeys' 탭에서 발급)",
            file=sys.stderr,
        )
    return 1
