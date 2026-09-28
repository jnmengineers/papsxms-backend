-- Run AUTOMATICALLY once by the app on start-up (config/DataUpdateRunner). Do not run by hand.
-- The runner manages the transaction; results of SELECT/SHOW lines are ignored.

-- Class teachers can tick learners for Lunch and Porridge on their Meals & Transport page.
-- (Runs once; afterwards the bursar can switch it off again in Finance → Extra Charges.)
UPDATE optional_charges
SET teacher_can_tick = 1
WHERE name IN ('Lunch', 'Porridge') AND active = 1 AND charge_id > 0;

SELECT name, sections, teacher_can_tick FROM optional_charges WHERE name IN ('Lunch', 'Porridge');
