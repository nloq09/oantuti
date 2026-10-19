CREATE TABLE worker_registration_tokens (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    remaining_uses INTEGER NOT NULL CHECK (remaining_uses BETWEEN 0 AND 100),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE workers (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    worker_version VARCHAR(64) NOT NULL,
    protocol_version INTEGER NOT NULL CHECK (protocol_version = 1),
    status VARCHAR(24) NOT NULL CHECK (status IN ('STARTING','ONLINE','BUSY','DRAINING','DEGRADED','OFFLINE','DISABLED')),
    approval_status VARCHAR(24) NOT NULL CHECK (approval_status IN ('PENDING_APPROVAL','APPROVED','REJECTED')),
    trust_level VARCHAR(16) NOT NULL CHECK (trust_level IN ('UNTRUSTED','COMMUNITY','TRUSTED')),
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE worker_credentials (
    worker_id UUID PRIMARY KEY REFERENCES workers(id),
    credential_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE worker_identity_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(40) NOT NULL,
    resource_id UUID NOT NULL,
    actor_user_id UUID REFERENCES users(id),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
