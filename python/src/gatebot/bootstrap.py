from gate_api import ApiClient, Configuration, FuturesApi

from gatebot.adapters.exchange import AbstractExchange, GateExchange
from gatebot.config import Settings


def build_exchange(settings: Settings | None = None) -> AbstractExchange:
    settings = settings or Settings.from_env()
    config = Configuration(
        host=settings.host,
        key=settings.key or None,
        secret=settings.secret or None,
    )
    futures_api = FuturesApi(ApiClient(config))
    return GateExchange(futures_api, settings.settle)
