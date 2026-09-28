-- Run AUTOMATICALLY once by the app on start-up (config/DataUpdateRunner). Do not run by hand.
-- The runner manages the transaction; results of SELECT/SHOW lines are ignored.

-- ═══════════════════════════════════════════════════════════════════════════════
--  PAPSXMS — Load the school transport destinations and fares
--  (from the fare sheet on the notice board, photographed 25 Sep 2026)
--
--  BEFORE RUNNING: restart the backend once with the Transport files, so the
--  table transport_routes exists.
--
--  Termly = monthly x 3, two ways = one way x 2 (checked for every row).
--  Buruburu two ways termly is printed as 105,000 — loaded as 10,500 (3,500 x 3).
--
--  Safe to run again: destinations that already exist are not duplicated.
--  Fares can be changed later on the Transport page.
-- ═══════════════════════════════════════════════════════════════════════════════

INSERT INTO transport_routes (name, one_way_monthly, one_way_termly, two_way_monthly, two_way_termly, active)
SELECT name, ow_m, ow_t, tw_m, tw_t, 1 FROM (
    SELECT 'Kwa Ndege' AS name, 1200.00 AS ow_m, 3600.00 AS ow_t, 2400.00 AS tw_m, 7200.00 AS tw_t UNION ALL
    SELECT 'Kayaba' AS name, 1000.00 AS ow_m, 3000.00 AS ow_t, 2000.00 AS tw_m, 6000.00 AS tw_t UNION ALL
    SELECT 'Mwalimu Court' AS name, 1000.00 AS ow_m, 3000.00 AS ow_t, 2000.00 AS tw_m, 6000.00 AS tw_t UNION ALL
    SELECT 'Amani Court' AS name, 1200.00 AS ow_m, 3600.00 AS ow_t, 2400.00 AS tw_m, 7200.00 AS tw_t UNION ALL
    SELECT 'Pipeline' AS name, 1200.00 AS ow_m, 3600.00 AS ow_t, 2400.00 AS tw_m, 7200.00 AS tw_t UNION ALL
    SELECT 'Buruburu' AS name, 1750.00 AS ow_m, 5250.00 AS ow_t, 3500.00 AS tw_m, 10500.00 AS tw_t UNION ALL
    SELECT 'Donholm' AS name, 1200.00 AS ow_m, 3600.00 AS ow_t, 2400.00 AS tw_m, 7200.00 AS tw_t UNION ALL
    SELECT 'Green Span' AS name, 1350.00 AS ow_m, 4050.00 AS ow_t, 2700.00 AS tw_m, 8100.00 AS tw_t UNION ALL
    SELECT 'Oilcom' AS name, 1200.00 AS ow_m, 3600.00 AS ow_t, 2400.00 AS tw_m, 7200.00 AS tw_t UNION ALL
    SELECT 'Transami' AS name, 1250.00 AS ow_m, 3750.00 AS ow_t, 2500.00 AS tw_m, 7500.00 AS tw_t UNION ALL
    SELECT 'Fedha' AS name, 1000.00 AS ow_m, 3000.00 AS ow_t, 2000.00 AS tw_m, 6000.00 AS tw_t UNION ALL
    SELECT 'Christ Community' AS name, 1000.00 AS ow_m, 3000.00 AS ow_t, 2000.00 AS tw_m, 6000.00 AS tw_t UNION ALL
    SELECT 'Kware' AS name, 1200.00 AS ow_m, 3600.00 AS ow_t, 2400.00 AS tw_m, 7200.00 AS tw_t UNION ALL
    SELECT 'Tumaini' AS name, 1200.00 AS ow_m, 3600.00 AS ow_t, 2400.00 AS tw_m, 7200.00 AS tw_t UNION ALL
    SELECT 'Deska' AS name, 1250.00 AS ow_m, 3750.00 AS ow_t, 2500.00 AS tw_m, 7500.00 AS tw_t UNION ALL
    SELECT 'Mradi' AS name, 1500.00 AS ow_m, 4500.00 AS ow_t, 3000.00 AS tw_m, 9000.00 AS tw_t UNION ALL
    SELECT 'Makuti' AS name, 1500.00 AS ow_m, 4500.00 AS ow_t, 3000.00 AS tw_m, 9000.00 AS tw_t UNION ALL
    SELECT 'Utawala' AS name, 2000.00 AS ow_m, 6000.00 AS ow_t, 4000.00 AS tw_m, 12000.00 AS tw_t UNION ALL
    SELECT 'Church Road' AS name, 1200.00 AS ow_m, 3600.00 AS ow_t, 2400.00 AS tw_m, 7200.00 AS tw_t
) AS fares
WHERE NOT EXISTS (SELECT 1 FROM transport_routes t WHERE t.name = fares.name);

-- ── CHECK: 19 destinations ──
SELECT name, one_way_monthly, one_way_termly, two_way_monthly, two_way_termly
FROM transport_routes ORDER BY name;
