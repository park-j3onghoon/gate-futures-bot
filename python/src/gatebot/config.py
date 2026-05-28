import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

DEFAULT_HOST = "https://api.gateio.ws/api/v4"

# python/.env 자동 로드용 경로 (config.py에서 3단계 위 = python/).
# python/src/gatebot/config.py → python/
_ENV_FILE = Path(__file__).resolve().parent.parent.parent / ".env"


@dataclass(frozen=True)
class Settings:
    key: str = ""
    secret: str = ""
    host: str = DEFAULT_HOST
    settle: str = "usdt"

    @classmethod
    def from_env(cls) -> "Settings":
        # python/.env 가 있으면 OS 환경변수에 합쳐 로드 (override=False — 이미 export된 값이 우선).
        # 학습 단계 편의 — 매 셸마다 export 없이 .env에 키 한 번만 두면 됨.
        if _ENV_FILE.exists():
            load_dotenv(_ENV_FILE, override=False)
        return cls(
            key=os.getenv("GATE_API_KEY", ""),
            secret=os.getenv("GATE_API_SECRET", ""),
            host=os.getenv("GATE_API_HOST", DEFAULT_HOST),
            settle=os.getenv("GATE_SETTLE", "usdt"),
        )

    @property
    def is_testnet(self) -> bool:
        return "testnet" in self.host
