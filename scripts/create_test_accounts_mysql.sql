-- MySQL Test Accounts Creation Script
-- Run as root user

-- Development Team
CREATE USER IF NOT EXISTS 'dev_alice'@'%' IDENTIFIED BY 'Test1234!';
CREATE USER IF NOT EXISTS 'dev_bob'@'%' IDENTIFIED BY 'Test1234!';
CREATE USER IF NOT EXISTS 'dev_charlie'@'%' IDENTIFIED BY 'Test1234!';
CREATE USER IF NOT EXISTS 'dev_diana'@'%' IDENTIFIED BY 'Test1234!';
CREATE USER IF NOT EXISTS 'dev_evan'@'%' IDENTIFIED BY 'Test1234!';

-- QA Team
CREATE USER IF NOT EXISTS 'qa_frank'@'%' IDENTIFIED BY 'Test1234!';
CREATE USER IF NOT EXISTS 'qa_grace'@'%' IDENTIFIED BY 'Test1234!';
CREATE USER IF NOT EXISTS 'qa_henry'@'%' IDENTIFIED BY 'Test1234!';

-- Data Team
CREATE USER IF NOT EXISTS 'data_iris'@'%' IDENTIFIED BY 'Test1234!';
CREATE USER IF NOT EXISTS 'data_jack'@'%' IDENTIFIED BY 'Test1234!';
CREATE USER IF NOT EXISTS 'data_kate'@'%' IDENTIFIED BY 'Test1234!';

-- Operations Team
CREATE USER IF NOT EXISTS 'ops_leo'@'%' IDENTIFIED BY 'Test1234!';
CREATE USER IF NOT EXISTS 'ops_mia'@'%' IDENTIFIED BY 'Test1234!';

-- Analytics Team
CREATE USER IF NOT EXISTS 'analytics_noah'@'%' IDENTIFIED BY 'Test1234!';
CREATE USER IF NOT EXISTS 'analytics_olivia'@'%' IDENTIFIED BY 'Test1234!';

-- Service Accounts
CREATE USER IF NOT EXISTS 'svc_api'@'%' IDENTIFIED BY 'Service1234!';
CREATE USER IF NOT EXISTS 'svc_batch'@'%' IDENTIFIED BY 'Service1234!';
CREATE USER IF NOT EXISTS 'svc_report'@'%' IDENTIFIED BY 'Service1234!';
CREATE USER IF NOT EXISTS 'svc_backup'@'%' IDENTIFIED BY 'Service1234!';

-- Read-only Accounts
CREATE USER IF NOT EXISTS 'readonly_peter'@'%' IDENTIFIED BY 'Test1234!';
CREATE USER IF NOT EXISTS 'readonly_quinn'@'%' IDENTIFIED BY 'Test1234!';

-- Test Accounts
CREATE USER IF NOT EXISTS 'test_user1'@'%' IDENTIFIED BY 'Test1234!';
CREATE USER IF NOT EXISTS 'test_user2'@'%' IDENTIFIED BY 'Test1234!';
CREATE USER IF NOT EXISTS 'test_user3'@'%' IDENTIFIED BY 'Test1234!';
CREATE USER IF NOT EXISTS 'test_user4'@'%' IDENTIFIED BY 'Test1234!';
CREATE USER IF NOT EXISTS 'test_user5'@'%' IDENTIFIED BY 'Test1234!';

-- Locked account for testing
CREATE USER IF NOT EXISTS 'locked_user'@'%' IDENTIFIED BY 'Test1234!' ACCOUNT LOCK;

-- Expired password account for testing
CREATE USER IF NOT EXISTS 'expired_user'@'%' IDENTIFIED BY 'Test1234!' PASSWORD EXPIRE;

-- Create test databases
CREATE DATABASE IF NOT EXISTS dev_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS qa_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS data_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant permissions to Development Team
GRANT ALL PRIVILEGES ON dev_db.* TO 'dev_alice'@'%';
GRANT ALL PRIVILEGES ON dev_db.* TO 'dev_bob'@'%';
GRANT SELECT, INSERT, UPDATE ON dev_db.* TO 'dev_charlie'@'%';
GRANT SELECT, INSERT, UPDATE ON dev_db.* TO 'dev_diana'@'%';
GRANT SELECT ON dev_db.* TO 'dev_evan'@'%';

-- Grant permissions to QA Team
GRANT ALL PRIVILEGES ON qa_db.* TO 'qa_frank'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON qa_db.* TO 'qa_grace'@'%';
GRANT SELECT ON qa_db.* TO 'qa_henry'@'%';

-- Grant permissions to Data Team
GRANT ALL PRIVILEGES ON data_db.* TO 'data_iris'@'%';
GRANT SELECT, INSERT ON data_db.* TO 'data_jack'@'%';
GRANT SELECT ON data_db.* TO 'data_kate'@'%';

-- Grant read-only access
GRANT SELECT ON dev_db.* TO 'readonly_peter'@'%';
GRANT SELECT ON qa_db.* TO 'readonly_peter'@'%';
GRANT SELECT ON data_db.* TO 'readonly_quinn'@'%';

-- Service account permissions
GRANT SELECT, INSERT, UPDATE ON dev_db.* TO 'svc_api'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON dev_db.* TO 'svc_batch'@'%';
GRANT SELECT ON dev_db.* TO 'svc_report'@'%';
GRANT SELECT, LOCK TABLES, SHOW VIEW ON dev_db.* TO 'svc_backup'@'%';

-- Create test tables in dev_db
USE dev_db;

CREATE TABLE IF NOT EXISTS products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10,2),
    stock INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS orders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    product_id INT,
    customer_name VARCHAR(100),
    quantity INT,
    order_date DATE DEFAULT (CURRENT_DATE),
    FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS customers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(100) UNIQUE,
    phone VARCHAR(20),
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(50),
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Insert sample data
INSERT IGNORE INTO products (name, price, stock) VALUES
    ('Laptop Pro', 1299.99, 50),
    ('Wireless Mouse', 29.99, 200),
    ('USB Hub', 49.99, 150),
    ('Monitor 27"', 399.99, 75),
    ('Keyboard Mech', 149.99, 100);

INSERT IGNORE INTO customers (name, email, phone) VALUES
    ('Alice Johnson', 'alice@example.com', '555-0101'),
    ('Bob Smith', 'bob@example.com', '555-0102'),
    ('Carol White', 'carol@example.com', '555-0103'),
    ('David Brown', 'david@example.com', '555-0104');

INSERT IGNORE INTO orders (product_id, customer_name, quantity) VALUES
    (1, 'Alice Johnson', 1),
    (2, 'Bob Smith', 3),
    (3, 'Carol White', 2),
    (4, 'David Brown', 1),
    (2, 'Alice Johnson', 5);

-- Flush privileges
FLUSH PRIVILEGES;

-- Summary
SELECT 'MySQL Test Accounts Created Successfully' as status;
SELECT User as username, Host, account_locked, password_expired
FROM mysql.user
WHERE User NOT IN ('root', 'mysql.sys', 'mysql.session', 'mysql.infoschema', 'debian-sys-maint')
ORDER BY User;
