CREATE TABLE IF NOT EXISTS wallets (
    id UUID PRIMARY KEY,
    wallet_number VARCHAR(255),
    user_id UUID NOT NULL,
    wallet_type VARCHAR(50) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    balance_amount NUMERIC(19, 2) NOT NULL,
    balance_currency VARCHAR(10) NOT NULL,
    available_balance_amount NUMERIC(19, 2) NOT NULL,
    available_balance_currency VARCHAR(10) NOT NULL,
    frozen_balance_amount NUMERIC(19, 2) NOT NULL,
    frozen_balance_currency VARCHAR(10) NOT NULL,
    daily_limit_amount NUMERIC(19, 2) NOT NULL,
    daily_limit_currency VARCHAR(10) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wallets_wallet_number ON wallets(wallet_number);
CREATE INDEX IF NOT EXISTS idx_wallets_user_id ON wallets(user_id);
