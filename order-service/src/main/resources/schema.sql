CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY,
    user_id VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_method VARCHAR(40) NOT NULL,
    shipping_name VARCHAR(120) NOT NULL,
    shipping_phone VARCHAR(40) NOT NULL,
    shipping_address VARCHAR(200) NOT NULL,
    shipping_city VARCHAR(80) NOT NULL,
    shipping_postal_code VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    price NUMERIC(12, 2) NOT NULL,
    quantity INTEGER NOT NULL
);
