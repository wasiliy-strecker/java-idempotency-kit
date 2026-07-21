CREATE TABLE customer_order (
    id UUID PRIMARY KEY,
    customer_reference VARCHAR(80) NOT NULL,
    sku VARCHAR(80) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19, 2) NOT NULL CHECK (unit_price > 0),
    total_price NUMERIC(19, 2) NOT NULL CHECK (total_price > 0),
    status VARCHAR(32) NOT NULL CHECK (status IN ('CREATED')),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX customer_order_reference_idx
    ON customer_order (customer_reference);
