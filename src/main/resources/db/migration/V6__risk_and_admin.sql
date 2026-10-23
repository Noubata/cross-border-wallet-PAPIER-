CREATE SCHEMA risk;
CREATE SCHEMA admin;

CREATE TABLE risk.assessments (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('APPROVE', 'FLAG', 'REJECT')),
    reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
    assessed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX risk_assessments_transfer_id_idx ON risk.assessments(transfer_id);
CREATE INDEX risk_assessments_customer_time_idx ON risk.assessments(customer_id, assessed_at DESC);

CREATE TABLE admin.transfer_reviews (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL,
    admin_id UUID NOT NULL,
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (transfer_id)
);

CREATE INDEX admin_transfer_reviews_admin_time_idx ON admin.transfer_reviews(admin_id, created_at DESC);