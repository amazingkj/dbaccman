-- PostgreSQL Test Accounts Creation Script
-- Run as superuser (postgres)

-- Development Team
CREATE USER dev_alice WITH PASSWORD 'Test1234!' LOGIN;
CREATE USER dev_bob WITH PASSWORD 'Test1234!' LOGIN;
CREATE USER dev_charlie WITH PASSWORD 'Test1234!' LOGIN;
CREATE USER dev_diana WITH PASSWORD 'Test1234!' LOGIN;
CREATE USER dev_evan WITH PASSWORD 'Test1234!' LOGIN;

-- QA Team
CREATE USER qa_frank WITH PASSWORD 'Test1234!' LOGIN;
CREATE USER qa_grace WITH PASSWORD 'Test1234!' LOGIN;
CREATE USER qa_henry WITH PASSWORD 'Test1234!' LOGIN;

-- Data Team
CREATE USER data_iris WITH PASSWORD 'Test1234!' LOGIN;
CREATE USER data_jack WITH PASSWORD 'Test1234!' LOGIN;
CREATE USER data_kate WITH PASSWORD 'Test1234!' LOGIN;

-- Operations Team
CREATE USER ops_leo WITH PASSWORD 'Test1234!' LOGIN;
CREATE USER ops_mia WITH PASSWORD 'Test1234!' LOGIN;

-- Analytics Team
CREATE USER analytics_noah WITH PASSWORD 'Test1234!' LOGIN;
CREATE USER analytics_olivia WITH PASSWORD 'Test1234!' LOGIN;

-- Service Accounts
CREATE USER svc_api WITH PASSWORD 'Service1234!' LOGIN;
CREATE USER svc_batch WITH PASSWORD 'Service1234!' LOGIN;
CREATE USER svc_report WITH PASSWORD 'Service1234!' LOGIN;
CREATE USER svc_backup WITH PASSWORD 'Service1234!' LOGIN;

-- Read-only Accounts
CREATE USER readonly_peter WITH PASSWORD 'Test1234!' LOGIN;
CREATE USER readonly_quinn WITH PASSWORD 'Test1234!' LOGIN;

-- Test Accounts with various states
CREATE USER test_user1 WITH PASSWORD 'Test1234!' LOGIN;
CREATE USER test_user2 WITH PASSWORD 'Test1234!' LOGIN;
CREATE USER test_user3 WITH PASSWORD 'Test1234!' LOGIN;
CREATE USER test_user4 WITH PASSWORD 'Test1234!' LOGIN;
CREATE USER test_user5 WITH PASSWORD 'Test1234!' LOGIN;

-- Create some schemas for testing
CREATE SCHEMA IF NOT EXISTS dev_schema;
CREATE SCHEMA IF NOT EXISTS qa_schema;
CREATE SCHEMA IF NOT EXISTS data_schema;

-- Grant schema usage to teams
GRANT USAGE ON SCHEMA dev_schema TO dev_alice, dev_bob, dev_charlie, dev_diana, dev_evan;
GRANT CREATE ON SCHEMA dev_schema TO dev_alice, dev_bob;

GRANT USAGE ON SCHEMA qa_schema TO qa_frank, qa_grace, qa_henry;
GRANT CREATE ON SCHEMA qa_schema TO qa_frank;

GRANT USAGE ON SCHEMA data_schema TO data_iris, data_jack, data_kate;
GRANT CREATE ON SCHEMA data_schema TO data_iris;

-- Create test tables in dev_schema
SET search_path TO dev_schema;

CREATE TABLE IF NOT EXISTS products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
    id SERIAL PRIMARY KEY,
    product_id INTEGER REFERENCES products(id),
    quantity INTEGER,
    order_date DATE DEFAULT CURRENT_DATE
);

CREATE TABLE IF NOT EXISTS customers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(100),
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert sample data
INSERT INTO products (name, price) VALUES
    ('Widget A', 19.99),
    ('Widget B', 29.99),
    ('Gadget X', 49.99),
    ('Gadget Y', 99.99),
    ('Tool Z', 14.99);

INSERT INTO customers (name, email) VALUES
    ('John Doe', 'john@example.com'),
    ('Jane Smith', 'jane@example.com'),
    ('Bob Wilson', 'bob@example.com');

INSERT INTO orders (product_id, quantity) VALUES
    (1, 5), (2, 3), (3, 2), (1, 10), (4, 1);

-- Grant table permissions
GRANT SELECT ON ALL TABLES IN SCHEMA dev_schema TO dev_alice, dev_bob, dev_charlie, dev_diana, dev_evan;
GRANT INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA dev_schema TO dev_alice, dev_bob;
GRANT SELECT ON ALL TABLES IN SCHEMA dev_schema TO readonly_peter, readonly_quinn;

-- Reset search path
SET search_path TO public;

-- Summary
SELECT 'PostgreSQL Test Accounts Created Successfully' as status;
SELECT usename as username,
       CASE WHEN usesuper THEN 'SUPERUSER' ELSE 'USER' END as role_type
FROM pg_user
WHERE usename NOT IN ('postgres', 'pg_monitor', 'pg_read_all_settings', 'pg_read_all_stats', 'pg_stat_scan_tables', 'pg_signal_backend')
ORDER BY usename;
