-- init.sql: runs once when the PostgreSQL data volume is first created.
-- Hibernate ddl-auto=update handles table creation, but we ensure the
-- database and schema exist with proper settings.

-- Enable citext extension for case-insensitive email if needed in future
-- CREATE EXTENSION IF NOT EXISTS citext;

-- Grant full privileges on public schema to the application user
GRANT ALL PRIVILEGES ON DATABASE friendsdb TO friends_user;
GRANT ALL ON SCHEMA public TO friends_user;

