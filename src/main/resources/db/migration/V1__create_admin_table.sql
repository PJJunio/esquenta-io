CREATE TABLE admin (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(30)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL
);
