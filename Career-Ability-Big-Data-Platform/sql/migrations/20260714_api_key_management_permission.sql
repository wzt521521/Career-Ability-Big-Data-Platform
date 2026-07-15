-- Grants self-service API key management without exposing API call audit data.

USE career_ability;

START TRANSACTION;

INSERT IGNORE INTO sys_permission
    (permission_name, permission_code, parent_id, type, path, icon, sort_order)
VALUES
    ('API Key management', 'api:key:manage', 0, 'menu', '/open-api/keys', 'Key', 7);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'api:key:manage'
WHERE r.role_code IN (
    'ROLE_ADMIN', 'ROLE_ANALYST', 'ROLE_COLLEGE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT');

COMMIT;
