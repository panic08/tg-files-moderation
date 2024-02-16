CREATE TABLE IF NOT EXISTS withdrawals_table(
        id SERIAL PRIMARY KEY,
        user_id BIGINT NOT NULL,
        amount DOUBLE PRECISION NOT NULL
);