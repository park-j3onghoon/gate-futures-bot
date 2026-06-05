from gatebot.adapters.exchange import DryRunExchange
from gatebot.domain.exceptions import AuthenticationError, OrderError
from gatebot.entrypoints import cli
from gatebot.entrypoints.cli import _report_error, main


def test_report_error_reports_domain_error_to_stderr_with_exit_code(capsys):
    """도메인 예외는 traceback 없이 종료 코드 1 + stderr 한 줄로 보고한다."""
    # when
    code = _report_error(OrderError("주문 실패"))

    # then
    captured = capsys.readouterr()
    assert code == 1
    assert "OrderError" in captured.err
    assert "주문 실패" in captured.err
    assert captured.out == ""  # 정상 출력(stdout)은 오염하지 않는다


def test_report_error_adds_testnet_key_hint_only_for_auth_error(capsys):
    """testnet/메인넷 키 분리 힌트는 AuthenticationError에만 붙는다 (예외 타입이 분기를 결정)."""
    # given / when — 메시지는 동일, 예외 타입만 다르게 하여 분기만 뒤집는다
    assert _report_error(AuthenticationError("Invalid key provided")) == 1
    auth_err = capsys.readouterr().err

    assert _report_error(OrderError("Invalid key provided")) == 1
    other_err = capsys.readouterr().err

    # then
    assert "Futures TestNet" in auth_err
    assert "Futures TestNet" not in other_err


# ---- order 서브커맨드 통합 ----

def _patch_exchange(monkeypatch, exchange, dry_inner=None):
    def build(dry_run=False):
        if dry_run and dry_inner is not None:
            return DryRunExchange(dry_inner)
        return exchange

    monkeypatch.setattr(cli, "build_exchange", build)


def test_order_places_order_and_registers_triggers(make_exchange, monkeypatch, capsys):
    """order 명령 → 진입 + SL/TP 트리거, 종료코드 0."""
    exchange = make_exchange()
    _patch_exchange(monkeypatch, exchange)

    code = main(["order", "btcusdt / 시장가 / 롱 / 3배 / tp:110 / sl:90 / size:1"])

    out = capsys.readouterr().out
    assert code == 0
    assert ("create_order", "BTC_USDT", 1, 3) in exchange.orders
    assert "999" in out and "998" in out  # SL/TP 트리거 id


def test_order_echoes_summary_before_real_submit(make_exchange, monkeypatch, capsys):
    """[Sec11] 실주문 직전 파싱 결과 1줄 echo — 오타 심볼 사람 확인."""
    exchange = make_exchange()
    _patch_exchange(monkeypatch, exchange)

    main(["order", "btcusdt / 롱 / 3배 / size:1"])

    out = capsys.readouterr().out
    assert "BTC_USDT" in out and "long" in out.lower()


def test_order_warns_on_naked_entry(make_exchange, monkeypatch, capsys):
    """[Sec9] sl 없이 진입하면 네이키드 경고."""
    exchange = make_exchange()
    _patch_exchange(monkeypatch, exchange)

    main(["order", "btcusdt / 롱 / 3배 / tp:110 / size:1"])

    assert "네이키드" in capsys.readouterr().out


def test_order_reports_unfilled_distinctly(make_exchange, monkeypatch, capsys):
    """[SR-1] IOC 미체결은 네이키드가 아니라 '미체결'로 분리 표기."""
    exchange = make_exchange(entry_fill_price="0")
    _patch_exchange(monkeypatch, exchange)

    main(["order", "btcusdt / 롱 / 3배 / tp:110 / sl:90 / size:1"])

    out = capsys.readouterr().out
    assert "미체결" in out
    assert not any(o[0] == "create_trigger_order" for o in exchange.orders)


def test_order_dry_run_marks_triggers_as_dry_run(make_exchange, monkeypatch, capsys):
    """[C3] dry-run이면 트리거 id가 'dry-run'으로 표기 (sentinel -1)."""
    inner = make_exchange()
    _patch_exchange(monkeypatch, inner, dry_inner=inner)

    main(["--dry-run", "order", "btcusdt / 롱 / 3배 / tp:110 / sl:90 / size:1"])

    out = capsys.readouterr().out
    # 배너의 'DRY-RUN'이 아니라 트리거 id 라벨이 'dry-run'으로 찍히는지 (sentinel -1 → _fmt_trigger)
    assert "SL 트리거: dry-run" in out
    assert "TP 트리거: dry-run" in out


def test_order_parse_error_exits_nonzero(make_exchange, monkeypatch, capsys):
    """파싱 실패(size 누락)는 종료코드 1 + stderr."""
    exchange = make_exchange()
    _patch_exchange(monkeypatch, exchange)

    code = main(["order", "btcusdt / 롱 / 3배"])

    assert code == 1
    assert capsys.readouterr().err != ""
