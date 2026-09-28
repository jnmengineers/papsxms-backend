-- Run AUTOMATICALLY once by the app on start-up (config/DataUpdateRunner). Do not run by hand.
-- The runner manages the transaction; results of SELECT/SHOW lines are ignored.

-- ═══════════════════════════════════════════════════════════════════════════════
--  PAPSXMS — Step 2: link teacher LOGINS to their TEACHER records
--
--  Run AFTER deploying the step-2 backend once (Hibernate adds users.teacher_id).
--  Run the SELECTs first and CHECK them. Only then run the UPDATEs.
--  Take a backup first:
--    docker exec <mysql-container> mysqldump -u root -p <database> > before_step2.sql
--
--  If MySQL Workbench refuses an UPDATE ("safe update mode"), run first:
--    SET SQL_SAFE_UPDATES = 0;
-- ═══════════════════════════════════════════════════════════════════════════════


-- ── 1. PREVIEW: which teacher does each teacher login belong to? ──────────────
--    Logins created automatically have USERNAME = the teacher's PHONE, so
--    by_phone is the most reliable match. by_linked_id uses the old LinkedId.
--    Check each row. If both columns are filled they should be the same person.
SELECT u.user_id, u.username,
       CONCAT(tp.first_name, ' ', tp.last_name) AS by_phone,
       CONCAT(tl.first_name, ' ', tl.last_name) AS by_linked_id,
       lc.class_name                            AS login_class
FROM users u
LEFT JOIN teacher tp ON TRIM(tp.phone) = u.username
LEFT JOIN teacher tl ON tl.teacher_id  = u.linked_id
LEFT JOIN classes lc ON lc.class_id    = u.linked_class_id
WHERE u.role = 'TEACHER'
ORDER BY u.username;


-- ── 2. MUST BE EMPTY before step 3b: two logins pointing at the same teacher ───
--    If anything shows, decide which login is correct and clear the other's
--    linked_id (UPDATE users SET linked_id = NULL WHERE user_id = ...).
SELECT u.linked_id, GROUP_CONCAT(u.username) AS logins
FROM users u
WHERE u.role = 'TEACHER' AND u.linked_id IS NOT NULL
GROUP BY u.linked_id
HAVING COUNT(*) > 1;


-- ── 3a. LINK BY PHONE (most reliable): username = teacher's phone ─────────────
UPDATE users u
JOIN teacher t ON TRIM(t.phone) = u.username
SET u.teacher_id = t.teacher_id, u.linked_id = t.teacher_id
WHERE u.role = 'TEACHER' AND u.teacher_id IS NULL;

-- ── 3b. LINK BY OLD LinkedId: only logins still unlinked, and only teachers
--        no other login has taken (the derived table avoids MySQL error 1093)
UPDATE users u
JOIN teacher t ON t.teacher_id = u.linked_id
SET u.teacher_id = t.teacher_id
WHERE u.role = 'TEACHER' AND u.teacher_id IS NULL
  AND t.teacher_id NOT IN (SELECT teacher_id FROM (SELECT teacher_id FROM users WHERE teacher_id IS NOT NULL) AS taken);


-- ── 4. STILL UNLINKED teacher logins → link these on the Users page ───────────
SELECT user_id, username FROM users
WHERE role = 'TEACHER' AND teacher_id IS NULL;


-- ── 5. DISAGREEMENTS between the login's class and the class-teacher setting ───
--    login_class        = what the Users page says
--    class_teacher_of   = what the Classes page says
--    From now on the Classes page is the one that counts.
SELECT u.username,
       CONCAT(t.first_name, ' ', t.last_name) AS teacher,
       lc.class_name AS login_class,
       (SELECT GROUP_CONCAT(c.class_name) FROM classes c WHERE c.class_teacher_id = u.teacher_id) AS class_teacher_of
FROM users u
JOIN teacher t       ON t.teacher_id = u.teacher_id
LEFT JOIN classes lc ON lc.class_id  = u.linked_class_id
WHERE u.role = 'TEACHER'
HAVING login_class IS NULL
    OR class_teacher_of IS NULL
    OR FIND_IN_SET(login_class, class_teacher_of) = 0;


-- ── 6. OPTIONAL FIX: where a class has NO class teacher but a linked login
--       manages it, make that teacher the class teacher.
--       (Classes that already have a class teacher are NOT changed.)
UPDATE classes c
JOIN users u ON u.linked_class_id = c.class_id
SET c.class_teacher_id = u.teacher_id
WHERE u.role = 'TEACHER'
  AND u.teacher_id IS NOT NULL
  AND c.class_teacher_id IS NULL;

-- Anything still listed by query 5 afterwards: fix on the Classes page
-- (assign the right class teacher) — the login will follow automatically.
