from gatebot.domain.exceptions import AuthenticationError, OrderError
from gatebot.entrypoints.cli import _report_error


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
