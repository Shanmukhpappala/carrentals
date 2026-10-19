CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    email VARCHAR(255),
    password VARCHAR(255),
    phone VARCHAR(255),
    role VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS car (
    id BIGSERIAL PRIMARY KEY,
    brand VARCHAR(255),
    model VARCHAR(255),
    registration_number VARCHAR(255),
    year INTEGER,
    fuel_type VARCHAR(255),
    transmission VARCHAR(255),
    seating_capacity INTEGER,
    price_per_day NUMERIC(38, 2),
    location VARCHAR(255),
    status VARCHAR(255),
    image_url VARCHAR(255),
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS booking (
    id BIGSERIAL PRIMARY KEY,
    pickup_date DATE,
    return_date DATE,
    total_amount NUMERIC(38, 2),
    status VARCHAR(255),
    created_at DATE,
    user_id BIGINT REFERENCES app_user(id),
    car_id BIGINT REFERENCES car(id)
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM app_user
        GROUP BY lower(btrim(email))
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot add case-insensitive email uniqueness: duplicate normalized emails exist';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM car
        GROUP BY upper(btrim(registration_number))
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot add case-insensitive registration uniqueness: duplicate normalized registrations exist';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_app_user_email_case_insensitive
    ON app_user (lower(btrim(email)));

CREATE UNIQUE INDEX IF NOT EXISTS ux_car_registration_case_insensitive
    ON car (upper(btrim(registration_number)));