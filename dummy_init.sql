-- Create databases if they do not exist
SELECT 'CREATE DATABASE your_value'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'your_value')\gexec

SELECT 'CREATE DATABASE your_value'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'your_value')\gexec

DO $$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'your_value') THEN
            CREATE USER your_value WITH PASSWORD 'your_value';
        END IF;

        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'your_value') THEN
            CREATE USER your_value WITH PASSWORD 'your_value';
        END IF;
    END
$$;

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE your_value TO your_value;
GRANT ALL PRIVILEGES ON DATABASE your_value TO your_value;