SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
SELECT setval('organizations_id_seq', (SELECT MAX(id) FROM organizations));
SELECT setval('vendors_id_seq', (SELECT MAX(id) FROM vendors));
SELECT setval('workflows_id_seq', (SELECT MAX(id) FROM workflows));
SELECT setval('ai_insights_id_seq', (SELECT MAX(id) FROM ai_insights));
