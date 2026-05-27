class MarketDataError(Exception):
    """캔들/시세 조회 실패 (빈 데이터·필수 필드 누락·SDK 오류)."""
