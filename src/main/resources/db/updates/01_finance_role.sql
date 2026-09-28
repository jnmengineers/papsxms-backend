-- Run AUTOMATICALLY once by the app on start-up (config/DataUpdateRunner). Do not run by hand.
-- The runner manages the transaction; results of SELECT/SHOW lines are ignored.

-- ═══════════════════════════════════════════════════════════════════════════════
--  PAPSXMS — Finance: allow the new ACCOUNTANT (bursar) role
--
--  Your users.role column is a MySQL ENUM listing the allowed roles. Hibernate does
--  NOT add new values to an existing ENUM, so without this, creating a bursar login
--  fails with "Data truncated for column 'role'".
--
--  Run ONCE (local now, live when you deploy). Safe: existing users are unchanged.
-- ═══════════════════════════════════════════════════════════════════════════════
ALTER TABLE users
    MODIFY role ENUM('ADMIN', 'CLERK', 'TEACHER', 'ACCOUNTANT') NOT NULL;

-- Check: should list the four roles
SHOW COLUMNS FROM users LIKE 'role';
