-- Run AUTOMATICALLY once by the app on start-up (config/DataUpdateRunner). Do not run by hand.
-- The runner manages the transaction; results of SELECT/SHOW lines are ignored.

-- ═══════════════════════════════════════════════════════════════════════════════
--  PAPSXMS — Load the 2026 fee structures (from the school's 2026 fee documents)
--
--  BEFORE RUNNING: the backend must have been started once with the Finance files,
--  so the tables fee_structures and fee_structure_items exist.
--
--  Loads 12 structures: 4 sections x 3 terms. Items are EXACTLY as printed;
--  the system works out totals from the items.
--
--  ⚠ CHECK BEFORE BILLING TERM 3 — two printed totals don't match their items:
--     Pre-School Term 3:    items = 5,250 but printed total = 5,400
--     Lower Primary Term 3: items = 7,550 but printed total = 7,400
--  Correct them on Finance → Fee Structures once the school confirms.
--
--  Not included (they are not charged to every learner every term):
--  lunch/porridge/meals (optional), interview, admission, advent melody,
--  and the newcomer fees for 2nd/3rd term joiners.
--
--  Safe to run again: it first removes any 2026 structures, then loads fresh.
--  Invoices already issued are NOT touched (they keep their own copies).
-- ═══════════════════════════════════════════════════════════════════════════════


-- Remove any 2026 structures entered earlier (e.g. while testing)
DELETE i FROM fee_structure_items i
JOIN fee_structures s ON s.structure_id = i.structure_id
WHERE s.year_label = '2026';
DELETE FROM fee_structures WHERE year_label = '2026';


-- ── PRE-SCHOOL (Play Group to PP2) ──────────────────────────────────────────

-- Term 1  (total 8,300)
INSERT INTO fee_structures (section, year_label, term) VALUES ('PRE_SCHOOL', '2026', 1);
SET @sid = LAST_INSERT_ID();
INSERT INTO fee_structure_items (structure_id, line_no, item_name, amount) VALUES
  (@sid, 0, 'Tuition', 5500.00),
  (@sid, 1, 'Exam', 200.00),
  (@sid, 2, 'Activity', 250.00),
  (@sid, 3, 'Computer', 200.00),
  (@sid, 4, 'Diary', 250.00),
  (@sid, 5, 'Development', 1000.00),
  (@sid, 6, 'Assessment Book', 300.00),
  (@sid, 7, 'Ream Paper', 600.00);

-- Term 2  (total 6,100)
INSERT INTO fee_structures (section, year_label, term) VALUES ('PRE_SCHOOL', '2026', 2);
SET @sid = LAST_INSERT_ID();
INSERT INTO fee_structure_items (structure_id, line_no, item_name, amount) VALUES
  (@sid, 0, 'Tuition', 5500.00),
  (@sid, 1, 'Exam', 200.00),
  (@sid, 2, 'Computer', 200.00),
  (@sid, 3, 'Activity', 200.00);

-- Term 3  (total 5,250)
INSERT INTO fee_structures (section, year_label, term) VALUES ('PRE_SCHOOL', '2026', 3);
SET @sid = LAST_INSERT_ID();
INSERT INTO fee_structure_items (structure_id, line_no, item_name, amount) VALUES
  (@sid, 0, 'Tuition', 4600.00),
  (@sid, 1, 'Exam', 200.00),
  (@sid, 2, 'Computer', 200.00),
  (@sid, 3, 'Activity', 250.00);

-- ── LOWER PRIMARY (Grade 1-3) ──────────────────────────────────────────

-- Term 1  (total 10,850)
INSERT INTO fee_structures (section, year_label, term) VALUES ('LOWER_PRIMARY', '2026', 1);
SET @sid = LAST_INSERT_ID();
INSERT INTO fee_structure_items (structure_id, line_no, item_name, amount) VALUES
  (@sid, 0, 'Tuition', 6000.00),
  (@sid, 1, 'Exam', 250.00),
  (@sid, 2, 'Activity', 250.00),
  (@sid, 3, 'Computer', 700.00),
  (@sid, 4, 'Diary', 250.00),
  (@sid, 5, 'Preps', 1500.00),
  (@sid, 6, 'Development', 1000.00),
  (@sid, 7, 'Assessment Book', 300.00),
  (@sid, 8, 'Ream Paper', 600.00);

-- Term 2  (total 8,650)
INSERT INTO fee_structures (section, year_label, term) VALUES ('LOWER_PRIMARY', '2026', 2);
SET @sid = LAST_INSERT_ID();
INSERT INTO fee_structure_items (structure_id, line_no, item_name, amount) VALUES
  (@sid, 0, 'Tuition', 6000.00),
  (@sid, 1, 'Exam', 250.00),
  (@sid, 2, 'Computer', 700.00),
  (@sid, 3, 'Activity', 200.00),
  (@sid, 4, 'Preps', 1500.00);

-- Term 3  (total 7,550)
INSERT INTO fee_structures (section, year_label, term) VALUES ('LOWER_PRIMARY', '2026', 3);
SET @sid = LAST_INSERT_ID();
INSERT INTO fee_structure_items (structure_id, line_no, item_name, amount) VALUES
  (@sid, 0, 'Tuition', 4900.00),
  (@sid, 1, 'Exam', 250.00),
  (@sid, 2, 'Computer', 700.00),
  (@sid, 3, 'Activity', 200.00),
  (@sid, 4, 'Preps', 1500.00);

-- ── UPPER PRIMARY (Grade 4-6) ──────────────────────────────────────────

-- Term 1  (total 11,350)
INSERT INTO fee_structures (section, year_label, term) VALUES ('UPPER_PRIMARY', '2026', 1);
SET @sid = LAST_INSERT_ID();
INSERT INTO fee_structure_items (structure_id, line_no, item_name, amount) VALUES
  (@sid, 0, 'Tuition', 6500.00),
  (@sid, 1, 'Exam', 250.00),
  (@sid, 2, 'Activity', 250.00),
  (@sid, 3, 'Computer', 700.00),
  (@sid, 4, 'Diary', 250.00),
  (@sid, 5, 'Preps', 1500.00),
  (@sid, 6, 'Development', 1000.00),
  (@sid, 7, 'Assessment Book', 300.00),
  (@sid, 8, 'Ream Paper', 600.00);

-- Term 2  (total 9,150)
INSERT INTO fee_structures (section, year_label, term) VALUES ('UPPER_PRIMARY', '2026', 2);
SET @sid = LAST_INSERT_ID();
INSERT INTO fee_structure_items (structure_id, line_no, item_name, amount) VALUES
  (@sid, 0, 'Tuition', 6500.00),
  (@sid, 1, 'Exam', 250.00),
  (@sid, 2, 'Computer', 700.00),
  (@sid, 3, 'Activity', 200.00),
  (@sid, 4, 'Preps', 1500.00);

-- Term 3  (total 7,900)
INSERT INTO fee_structures (section, year_label, term) VALUES ('UPPER_PRIMARY', '2026', 3);
SET @sid = LAST_INSERT_ID();
INSERT INTO fee_structure_items (structure_id, line_no, item_name, amount) VALUES
  (@sid, 0, 'Tuition', 5200.00),
  (@sid, 1, 'Exam', 250.00),
  (@sid, 2, 'Computer', 700.00),
  (@sid, 3, 'Activity', 250.00),
  (@sid, 4, 'Preps', 1500.00);

-- ── JUNIOR SCHOOL (Grade 7-9) ──────────────────────────────────────────

-- Term 1  (total 14,150)
INSERT INTO fee_structures (section, year_label, term) VALUES ('JUNIOR_SCHOOL', '2026', 1);
SET @sid = LAST_INSERT_ID();
INSERT INTO fee_structure_items (structure_id, line_no, item_name, amount) VALUES
  (@sid, 0, 'Ream Paper', 600.00),
  (@sid, 1, 'Tuition Fee', 6500.00),
  (@sid, 2, 'Technical Learning Materials', 1500.00),
  (@sid, 3, 'Library and Laboratory Charges', 1000.00),
  (@sid, 4, 'Co-Curriculum Activities', 500.00),
  (@sid, 5, 'Assessment Fee', 500.00),
  (@sid, 6, 'Learning Projects', 500.00),
  (@sid, 7, 'Remedial Classes', 1500.00),
  (@sid, 8, 'Development', 1000.00),
  (@sid, 9, 'Assessment Book/Diary', 550.00);

-- Term 2  (total 12,000)
INSERT INTO fee_structures (section, year_label, term) VALUES ('JUNIOR_SCHOOL', '2026', 2);
SET @sid = LAST_INSERT_ID();
INSERT INTO fee_structure_items (structure_id, line_no, item_name, amount) VALUES
  (@sid, 0, 'Tuition Fee', 6500.00),
  (@sid, 1, 'Technical Learning Materials', 1500.00),
  (@sid, 2, 'Library and Laboratory Charges', 1000.00),
  (@sid, 3, 'Co-Curriculum Activities', 500.00),
  (@sid, 4, 'Assessment Fee', 500.00),
  (@sid, 5, 'Learning Projects', 500.00),
  (@sid, 6, 'Remedial Classes', 1500.00);

-- Term 3  (total 10,600)
INSERT INTO fee_structures (section, year_label, term) VALUES ('JUNIOR_SCHOOL', '2026', 3);
SET @sid = LAST_INSERT_ID();
INSERT INTO fee_structure_items (structure_id, line_no, item_name, amount) VALUES
  (@sid, 0, 'Tuition Fee', 5100.00),
  (@sid, 1, 'Technical Learning Materials', 1500.00),
  (@sid, 2, 'Library and Laboratory Charges', 1000.00),
  (@sid, 3, 'Co-Curriculum Activities', 500.00),
  (@sid, 4, 'Assessment Fee', 500.00),
  (@sid, 5, 'Learning Projects', 500.00),
  (@sid, 6, 'Remedial Classes', 1500.00);


-- ── CHECK: 12 rows, totals per section and term ─────────────────────────────
SELECT s.section, s.term, COUNT(i.item_name) AS items, SUM(i.amount) AS total
FROM fee_structures s
JOIN fee_structure_items i ON i.structure_id = s.structure_id
WHERE s.year_label = '2026'
GROUP BY s.section, s.term
ORDER BY FIELD(s.section, 'PRE_SCHOOL', 'LOWER_PRIMARY', 'UPPER_PRIMARY', 'JUNIOR_SCHOOL'), s.term;
