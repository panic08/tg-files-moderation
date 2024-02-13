CREATE TABLE IF NOT EXISTS users_table(
    id SERIAL PRIMARY KEY,
    telegram_user_id BIGINT UNIQUE NOT NULL,
    role VARCHAR(255) NOT NULL,
    balance DOUBLE PRECISION NOT NULL,
    registered_at BIGINT NOT NULL
);