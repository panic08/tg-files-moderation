CREATE TABLE IF NOT EXISTS withdrawals_table(
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,

    FOREIGN KEY (user_id) REFERENCES users_table (id)
);