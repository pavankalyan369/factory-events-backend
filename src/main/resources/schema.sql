CREATE TABLE IF NOT EXISTS event (
                                     id BIGSERIAL PRIMARY KEY,
                                     event_id VARCHAR(255) NOT NULL,
    factory_id VARCHAR(255) NOT NULL,
    line_id VARCHAR(255) NOT NULL,
    machine_id VARCHAR(255) NOT NULL,
    event_time TIMESTAMPTZ NOT NULL,
    received_time TIMESTAMPTZ NOT NULL,
    duration_ms BIGINT NOT NULL,
    defect_count INT NOT NULL
    );

-- Unique constraint behavior via unique index (works with IF NOT EXISTS)
CREATE UNIQUE INDEX IF NOT EXISTS uk_event_event_id ON event (event_id);

-- Indexes for query performance
CREATE INDEX IF NOT EXISTS idx_event_machine_time
    ON event (machine_id, event_time);

CREATE INDEX IF NOT EXISTS idx_event_factory_line_time
    ON event (factory_id, line_id, event_time);
