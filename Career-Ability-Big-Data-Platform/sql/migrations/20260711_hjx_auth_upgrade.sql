-- Non-destructive upgrade for an existing career_ability database.
-- Run this once after deploying the HJX authentication integration.

USE career_ability;

START TRANSACTION;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'dashboard:view', 'position:view', 'report:view',
    'system:view', 'collect:view', 'collect:toggle',
    'api:view', 'api:docs')
WHERE r.role_code = 'ROLE_ANALYST';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'dashboard:view', 'position:view', 'report:view')
WHERE r.role_code = 'ROLE_COLLEGE_ADMIN';

COMMIT;
