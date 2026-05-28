class GateError(Exception):
    """Gate.io 도메인 예외 베이스 (Kotlin sealed class GateFuturesException 미러)."""


class AuthenticationError(GateError):
    """인증 실패 — Gate 라벨 INVALID_KEY."""


class InsufficientBalanceError(GateError):
    """잔고 부족 — Gate 라벨 BALANCE_NOT_ENOUGH."""


class RateLimitError(GateError):
    """레이트 리밋 — Gate 라벨 RATE_LIMIT."""


class OrderError(GateError):
    """주문 처리 실패 (서비스 가드 위반, 분류되지 않은 SDK 오류 포함)."""


class PositionError(GateError):
    """포지션 상태 위반 (기존 포지션 충돌·청산할 포지션 없음·잘못된 방향 등)."""


class MarketDataError(GateError):
    """캔들/시세 데이터 오류 (빈 데이터·필수 필드 누락·수치 변환 실패)."""
