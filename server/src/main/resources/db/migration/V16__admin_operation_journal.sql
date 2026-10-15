CREATE TABLE admin_operation_events (
    id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES users(id),
    actor_role VARCHAR(16) NOT NULL,
    resource_type VARCHAR(24) NOT NULL,
    resource_id VARCHAR(160),
    action VARCHAR(80) NOT NULL,
    result VARCHAR(16) NOT NULL CHECK (result IN ('SUCCESS','FAILURE')),
    http_status INTEGER NOT NULL CHECK (http_status BETWEEN 100 AND 599),
    reason VARCHAR(500),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_admin_operations_time ON admin_operation_events(occurred_at);
