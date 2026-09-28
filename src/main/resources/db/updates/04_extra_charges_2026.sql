-- Run AUTOMATICALLY once by the app on start-up (config/DataUpdateRunner). Do not run by hand.
-- The runner manages the transaction; results of SELECT/SHOW lines are ignored.

-- ═══════════════════════════════════════════════════════════════════════════════
--  PAPSXMS — Load the extra (optional / one-off) charges from the 2026 fee documents
--
--  BEFORE RUNNING: restart the backend once with the Extra Charges files, so the
--  table optional_charges exists.
--
--  PER_TERM = ticked each term for learners who take the service
--  ONCE     = charged once to the learners it applies to
--
--  Safe to run again: charges with the same name + sections are not duplicated.
--  Amounts can be changed later on Finance → Extra Charges.
-- ═══════════════════════════════════════════════════════════════════════════════

INSERT INTO optional_charges (name, amount, kind, sections, active)
SELECT * FROM (
    -- ── Primary (Pre-School, Lower Primary, Upper Primary) ──
    SELECT 'Lunch'                                              AS name, 3200.00 AS amount, 'PER_TERM' AS kind, 'PRE_SCHOOL,LOWER_PRIMARY,UPPER_PRIMARY' AS sections, 1 AS active UNION ALL
    SELECT 'Porridge',                                                   1000.00,           'PER_TERM',         'PRE_SCHOOL,LOWER_PRIMARY,UPPER_PRIMARY',           1 UNION ALL
    SELECT 'Interview',                                                   500.00,           'ONCE',             'PRE_SCHOOL,LOWER_PRIMARY,UPPER_PRIMARY',           1 UNION ALL
    SELECT 'Admission',                                                   600.00,           'ONCE',             'PRE_SCHOOL,LOWER_PRIMARY,UPPER_PRIMARY',           1 UNION ALL
    SELECT 'Newcomer: Diary, Assessment Book & Development (Term 2/3)',  1500.00,           'ONCE',             'PRE_SCHOOL,LOWER_PRIMARY,UPPER_PRIMARY',           1 UNION ALL
    -- ── Junior School ──
    SELECT 'Meals',                                                      4200.00,           'PER_TERM',         'JUNIOR_SCHOOL',                                    1 UNION ALL
    SELECT 'Interview',                                                   600.00,           'ONCE',             'JUNIOR_SCHOOL',                                    1 UNION ALL
    SELECT 'Admission',                                                  1000.00,           'ONCE',             'JUNIOR_SCHOOL',                                    1 UNION ALL
    SELECT 'Newcomer: Assessment Book, Development, Diary & Advent Melody (Term 2/3)', 1650.00, 'ONCE',         'JUNIOR_SCHOOL',                                    1 UNION ALL
    -- ── Both documents ──
    SELECT 'Advent Melody (newcomers, Grade 1, replacement)',             200.00,           'ONCE',             'PRE_SCHOOL,LOWER_PRIMARY,UPPER_PRIMARY,JUNIOR_SCHOOL', 1
) AS new_charges
WHERE NOT EXISTS (
    SELECT 1 FROM optional_charges oc
    WHERE oc.name = new_charges.name AND oc.sections = new_charges.sections
);

-- ── CHECK: should list 10 charges ──
SELECT name, amount, kind, sections FROM optional_charges ORDER BY sections, kind DESC, name;
