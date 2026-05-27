import os
from dataclasses import dataclass

DEFAULT_HOST = "https://api.gateio.ws/api/v4"


@dataclass(frozen=True)
class Settings:
    key: str = ""
    secret: str = ""
    host: str = DEFAULT_HOST
    settle: str = "usdt"

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            key=os.getenv("GATE_API_KEY", ""),
            secret=os.getenv("GATE_API_SECRET", ""),
            host=os.getenv("GATE_API_HOST", DEFAULT_HOST),
            settle=os.getenv("GATE_SETTLE", "usdt"),
        )

    @property
    def is_testnet(self) -> bool:
        return "testnet" in self.host
