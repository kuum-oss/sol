-- Flyway migration V1: создание таблицы items и начальные данные
CREATE TABLE IF NOT EXISTS items
(
    id   SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

INSERT INTO items (id, name)
VALUES (1, 'Item 1'),
       (2, 'Item 2'),
       (3, 'Item 3')
ON CONFLICT (id) DO NOTHING;
