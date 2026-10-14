CREATE TABLE admin_audit_events (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES users(id),
    target_type VARCHAR(16) NOT NULL CHECK (target_type IN ('USER','BOT')),
    target_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL CHECK (LENGTH(TRIM(reason)) > 0),
    before_value VARCHAR(32),
    after_value VARCHAR(32),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_admin_audit_target ON admin_audit_events(target_type,target_id,occurred_at);
ALTER TABLE bots ADD COLUMN admin_disabled BOOLEAN NOT NULL DEFAULT FALSE;
