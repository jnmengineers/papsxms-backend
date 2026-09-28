-- Run AUTOMATICALLY once by the app on start-up (config/DataUpdateRunner). Do not run by hand.
-- The runner manages the transaction; results of SELECT/SHOW lines are ignored.

-- ════════════════════════════════════════════════════════════════════════════
-- Junior School meals: replace the single "Meals" charge with Lunch + Porridge
-- (same as primary). Safe to run more than once.
--
--   1. Lunch and Porridge are extended to Junior School.
--   2. Every JSS learner on "Meals" (not cancelled) gets Lunch + Porridge for the
--      same term and class, at the Lunch and Porridge prices (unless they already
--      have that charge for that term).
--   3. Their "Meals" entries are cancelled (kept on record, reason noted).
--   4. The "Meals" charge is switched off.
-- Afterwards, check the result lists at the bottom.
-- ════════════════════════════════════════════════════════════════════════════

-- 1. Lunch and Porridge also apply to Junior School
UPDATE optional_charges
SET sections = CONCAT(sections, ',JUNIOR_SCHOOL')
WHERE name IN ('Lunch', 'Porridge') AND kind = 'PER_TERM'
  AND FIND_IN_SET('JUNIOR_SCHOOL', sections) = 0
  AND sections = 'PRE_SCHOOL,LOWER_PRIMARY,UPPER_PRIMARY';

-- 2. Lunch + Porridge for everyone currently on JSS Meals
INSERT INTO charge_entries (student_id, charge_id, class_id, name, amount, year_label, term, created_by, created_at, cancelled)
SELECT e.student_id, c.charge_id, e.class_id, c.name, c.amount, e.year_label, e.term, 'jss-meals-split', NOW(), 0
FROM charge_entries e
JOIN optional_charges m ON m.charge_id = e.charge_id AND m.name = 'Meals' AND m.sections = 'JUNIOR_SCHOOL'
JOIN optional_charges c ON c.name IN ('Lunch', 'Porridge') AND c.kind = 'PER_TERM' AND FIND_IN_SET('JUNIOR_SCHOOL', c.sections) > 0
WHERE e.cancelled = 0
  AND NOT EXISTS (SELECT 1 FROM charge_entries x
                  WHERE x.student_id = e.student_id AND x.charge_id = c.charge_id
                    AND x.year_label = e.year_label AND x.term = e.term AND x.cancelled = 0);

-- 3. Cancel the old Meals entries
UPDATE charge_entries e
JOIN optional_charges m ON m.charge_id = e.charge_id AND m.name = 'Meals' AND m.sections = 'JUNIOR_SCHOOL'
SET e.cancelled = 1, e.cancel_reason = 'Split into Lunch + Porridge', e.cancelled_by = 'jss-meals-split', e.cancelled_at = NOW()
WHERE e.cancelled = 0;

-- 4. Switch off the Meals charge
UPDATE optional_charges SET active = 0 WHERE name = 'Meals' AND sections = 'JUNIOR_SCHOOL';


-- ── Check ──
SELECT name, amount, sections, active FROM optional_charges WHERE name IN ('Lunch', 'Porridge', 'Meals');
SELECT c.name, e.year_label, e.term, COUNT(*) AS learners, SUM(e.amount) AS total
FROM charge_entries e JOIN optional_charges c ON c.charge_id = e.charge_id
WHERE e.cancelled = 0 AND c.name IN ('Lunch', 'Porridge', 'Meals')
GROUP BY c.name, e.year_label, e.term ORDER BY e.year_label, e.term, c.name;
