CREATE TABLE IF NOT EXISTS requests_table(
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount DOUBLE PRECISION,
    status VARCHAR(255) NOT NULL,
    file_path VARCHAR(255),
    created_at BIGINT NOT NULL,

    FOREIGN KEY (user_id) REFERENCES users_table (id)
);