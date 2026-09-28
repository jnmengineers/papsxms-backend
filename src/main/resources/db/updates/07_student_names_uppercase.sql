-- Run AUTOMATICALLY once by the app on start-up (config/DataUpdateRunner). Do not run by hand.
-- The runner manages the transaction; results of SELECT/SHOW lines are ignored.

-- ════════════════════════════════════════════════════════════════════════════
-- Learner names in CAPITALS (e.g. "Jane Wanjiku" → "JANE WANJIKU").
-- Run in MySQL Workbench on papsexamms_local. Safe to run more than once.
-- New and edited learners are saved in capitals automatically by the app.
-- ════════════════════════════════════════════════════════════════════════════

-- 1. CHECK the table name. This should list `students`. If it shows a different
--    name, replace `students` below with that name before running the rest.
SHOW TABLES LIKE '%student%';

-- 2. PREVIEW: learners whose names will change (nothing is changed yet)
SELECT admission_number, first_name, last_name,
       UPPER(TRIM(first_name)) AS new_first_name, UPPER(TRIM(last_name)) AS new_last_name
FROM students
WHERE BINARY first_name <> BINARY UPPER(TRIM(first_name))
   OR BINARY last_name  <> BINARY UPPER(TRIM(last_name));

-- 3. CHANGE them (Workbench's "safe update" mode blocks whole-table updates, so it's
--    switched off just for this and back on afterwards)
UPDATE students
SET first_name = UPPER(TRIM(first_name)),
    last_name  = UPPER(TRIM(last_name))
WHERE BINARY first_name <> BINARY UPPER(TRIM(first_name))
   OR BINARY last_name  <> BINARY UPPER(TRIM(last_name));

-- 4. CHECK: should show 0
SELECT COUNT(*) AS names_not_yet_in_capitals
FROM students
WHERE BINARY first_name <> BINARY UPPER(first_name)
   OR BINARY last_name  <> BINARY UPPER(last_name);
