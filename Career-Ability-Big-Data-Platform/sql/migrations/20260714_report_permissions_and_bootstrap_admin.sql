-- Security hardening for an existing career_ability database.
-- This migration deliberately does not create or reset an administrator password.
-- Use INITIAL_ADMIN_USERNAME and INITIAL_ADMIN_PASSWORD once when bootstrapping an empty install.

USE career_ability;

START TRANSACTION;

INSERT IGNORE INTO sys_permission
    (permission_name, permission_code, parent_id, type, path, icon, sort_order)
VALUES
    ('生成报告', 'report:generate', 3, 'button', NULL, NULL, 1),
    ('删除报告', 'report:delete', 3, 'button', NULL, NULL, 2);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('report:generate', 'report:delete')
WHERE r.role_code IN ('ROLE_ADMIN', 'ROLE_ANALYST');

COMMIT;
