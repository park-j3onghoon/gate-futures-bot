CREATE TABLE IF NOT EXISTS domain_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(64) NOT NULL,
    contract VARCHAR(32) NOT NULL,
    payload CLOB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_events_contract ON domain_events(contract);
CREATE INDEX IF NOT EXISTS idx_events_type ON domain_events(type);
CREATE INDEX IF NOT EXISTS idx_events_occurred_at ON domain_events(occurred_at DESC);
