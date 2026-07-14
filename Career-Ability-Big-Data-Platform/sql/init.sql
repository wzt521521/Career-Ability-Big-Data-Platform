-- ============================================================
-- 职业能力大数据服务平台 — 数据库初始化脚本
-- 全部表 DDL + 种子数据
-- ============================================================

-- 创建数据库（Docker 启动时已通过 MYSQL_DATABASE 环境变量创建，此处作为保险）
CREATE DATABASE IF NOT EXISTS career_ability
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE career_ability;

-- 禁用外键检查，防止 DROP TABLE 顺序问题
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 第一部分：用户与权限体系 (B 负责)
-- ============================================================

-- 1. 系统用户
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)     NOT NULL UNIQUE COMMENT '登录账号',
    password    VARCHAR(255)    NOT NULL COMMENT 'BCrypt 密文',
    real_name   VARCHAR(50)     DEFAULT NULL COMMENT '真实姓名',
    email       VARCHAR(100)    DEFAULT NULL COMMENT '邮箱',
    phone       VARCHAR(20)     DEFAULT NULL COMMENT '手机号',
    college     VARCHAR(100)    DEFAULT NULL COMMENT '所属学院（数据级权限用）',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1-启用 0-禁用',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sys_user_username (username),
    INDEX idx_sys_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户';

-- 2. 角色定义
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    role_name   VARCHAR(50)     NOT NULL COMMENT '角色名称',
    role_code   VARCHAR(50)     NOT NULL UNIQUE COMMENT '角色编码（ROLE_ADMIN 等）',
    description VARCHAR(200)    DEFAULT NULL COMMENT '角色描述',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色定义';

-- 3. 权限定义
DROP TABLE IF EXISTS sys_permission;
CREATE TABLE sys_permission (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    permission_name VARCHAR(100)    NOT NULL COMMENT '权限名称',
    permission_code VARCHAR(100)    NOT NULL UNIQUE COMMENT '权限标识（如 user:read）',
    parent_id       BIGINT          NOT NULL DEFAULT 0 COMMENT '父权限ID（0=顶级）',
    type            VARCHAR(20)     DEFAULT NULL COMMENT 'menu / button / api',
    path            VARCHAR(200)    DEFAULT NULL COMMENT '前端路由路径（菜单类型用）',
    icon            VARCHAR(50)     DEFAULT NULL COMMENT '菜单图标',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '排序号',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sys_permission_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限定义';

-- 4. 用户-角色关联
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联';

-- 5. 角色-权限关联
DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE sys_role_permission (
    role_id         BIGINT NOT NULL,
    permission_id   BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id)       REFERENCES sys_role(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES sys_permission(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限关联';

-- 6. 操作日志（AOP 自动记录）
DROP TABLE IF EXISTS sys_operation_log;
CREATE TABLE sys_operation_log (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT          DEFAULT NULL COMMENT '操作人ID',
    username    VARCHAR(50)     DEFAULT NULL COMMENT '操作人账号',
    module      VARCHAR(50)     DEFAULT NULL COMMENT '操作模块',
    operation   VARCHAR(50)     DEFAULT NULL COMMENT '操作类型',
    description VARCHAR(500)    DEFAULT NULL COMMENT '操作描述',
    method      VARCHAR(200)    DEFAULT NULL COMMENT '请求方法',
    params      TEXT            DEFAULT NULL COMMENT '请求参数',
    ip          VARCHAR(50)     DEFAULT NULL COMMENT '请求IP',
    duration    BIGINT          DEFAULT NULL COMMENT '执行耗时(ms)',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1-成功 0-失败',
    error_msg   TEXT            DEFAULT NULL COMMENT '错误信息',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sys_log_user_id (user_id),
    INDEX idx_sys_log_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志';

-- ============================================================
-- 第二部分：核心业务表 (A 的 ETL 写入)
-- ============================================================

-- 7. 企业信息
DROP TABLE IF EXISTS job_company;
CREATE TABLE job_company (
    id           BIGINT          AUTO_INCREMENT PRIMARY KEY,
    company_name VARCHAR(200)    NOT NULL COMMENT '企业名称',
    company_size VARCHAR(50)     DEFAULT NULL COMMENT '规模',
    industry     VARCHAR(100)    DEFAULT NULL COMMENT '行业分类',
    company_type VARCHAR(50)     DEFAULT NULL COMMENT '企业性质（民营/国企/外企/合资/上市公司）',
    create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_job_company_name (company_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业信息';

-- 8. 岗位信息（核心表）
DROP TABLE IF EXISTS job_position;
CREATE TABLE job_position (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    job_id      VARCHAR(100)    NOT NULL UNIQUE COMMENT '原始数据唯一标识',
    title       VARCHAR(200)    NOT NULL COMMENT '岗位名称',
    company_id  BIGINT          DEFAULT NULL COMMENT '关联企业ID',
    salary_min  INT             DEFAULT NULL COMMENT '最低月薪（K）',
    salary_max  INT             DEFAULT NULL COMMENT '最高月薪（K）',
    city        VARCHAR(50)     DEFAULT NULL COMMENT '城市',
    province    VARCHAR(50)     DEFAULT NULL COMMENT '省份',
    city_tier   VARCHAR(20)     DEFAULT NULL COMMENT '城市层级',
    education   VARCHAR(20)     DEFAULT NULL COMMENT '学历要求',
    experience  VARCHAR(20)     DEFAULT NULL COMMENT '经验要求',
    skills      JSON            DEFAULT NULL COMMENT '技能标签数组',
    welfare     JSON            DEFAULT NULL COMMENT '福利标签数组',
    description TEXT            DEFAULT NULL COMMENT '岗位描述原文',
    publish_date DATE           DEFAULT NULL COMMENT '发布日期',
    source_url  VARCHAR(500)    DEFAULT NULL COMMENT '原始URL',
    source_md5  VARCHAR(32)     DEFAULT NULL COMMENT '去重MD5',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_jp_title (title),
    INDEX idx_jp_city (city),
    INDEX idx_jp_province (province),
    INDEX idx_jp_city_tier (city_tier),
    INDEX idx_jp_education (education),
    INDEX idx_jp_experience (experience),
    INDEX idx_jp_salary (salary_min, salary_max),
    INDEX idx_jp_publish_date (publish_date),
    UNIQUE KEY uq_job_position_source_md5 (source_md5),
    INDEX idx_jp_company_id (company_id),
    FOREIGN KEY (company_id) REFERENCES job_company(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位信息';

-- 9. 技能字典（中期离线分析写入）
DROP TABLE IF EXISTS job_skill;
CREATE TABLE job_skill (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    skill_name  VARCHAR(100)    NOT NULL UNIQUE COMMENT '技能名称（标准化后）',
    category    VARCHAR(50)     DEFAULT NULL COMMENT '技能分类',
    frequency   INT             NOT NULL DEFAULT 0 COMMENT '出现总次数',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_job_skill_category (category),
    INDEX idx_job_skill_frequency (frequency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能字典';

-- ============================================================
-- 第三部分：学生画像 (D 负责)
-- ============================================================

-- 10. 学生就业画像
DROP TABLE IF EXISTS student_profile;
CREATE TABLE student_profile (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NOT NULL UNIQUE COMMENT '关联用户ID',
    major           VARCHAR(100)    DEFAULT NULL COMMENT '专业名称',
    skills          JSON            DEFAULT NULL COMMENT '已掌握技能',
    education       VARCHAR(20)     DEFAULT NULL COMMENT '学历',
    preferred_city  VARCHAR(200)    DEFAULT NULL COMMENT '意向城市（逗号分隔）',
    salary_min      INT             DEFAULT NULL COMMENT '期望最低月薪（K）',
    salary_max      INT             DEFAULT NULL COMMENT '期望最高月薪（K）',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生就业画像';

-- ============================================================
-- 第四部分：城市层级映射 (A 的 ETL 用)
-- ============================================================

-- 11. 城市层级映射
DROP TABLE IF EXISTS city_tier;
CREATE TABLE city_tier (
    id       BIGINT      AUTO_INCREMENT PRIMARY KEY,
    city     VARCHAR(50) NOT NULL COMMENT '城市标准名',
    alias    VARCHAR(200) DEFAULT NULL COMMENT '别名（逗号分隔）',
    province VARCHAR(50) NOT NULL COMMENT '所属省份',
    tier     VARCHAR(20) NOT NULL COMMENT '城市层级',
    INDEX idx_city_tier_city (city),
    INDEX idx_city_tier_tier (tier)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='城市层级映射';

-- ============================================================
-- 第五部分：采集管理 (A 负责)
-- ============================================================

-- 12. 数据源配置
DROP TABLE IF EXISTS collect_source;
CREATE TABLE collect_source (
    id               BIGINT          AUTO_INCREMENT PRIMARY KEY,
    source_name      VARCHAR(100)    NOT NULL COMMENT '数据源名称',
    source_type      VARCHAR(20)     NOT NULL DEFAULT 'FILE' COMMENT 'FILE / URL',
    file_path        VARCHAR(500)    DEFAULT NULL COMMENT '文件路径或URL模板',
    field_mapping    JSON            DEFAULT NULL COMMENT '字段映射配置',
    import_frequency VARCHAR(20)     DEFAULT 'manual' COMMENT '导入频率',
    status           TINYINT         NOT NULL DEFAULT 1 COMMENT '1-启用 0-禁用',
    description      VARCHAR(500)    DEFAULT NULL,
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源配置';

-- 13. 采集任务
DROP TABLE IF EXISTS collect_task;
CREATE TABLE collect_task (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    source_id       BIGINT          NOT NULL COMMENT '关联数据源ID',
    task_name       VARCHAR(100)    NOT NULL COMMENT '任务名称',
    cron_expression VARCHAR(50)     DEFAULT NULL COMMENT 'Cron表达式',
    status          VARCHAR(20)     NOT NULL DEFAULT 'IDLE' COMMENT 'IDLE/RUNNING/PAUSED/ERROR',
    last_run_time   DATETIME        DEFAULT NULL,
    next_run_time   DATETIME        DEFAULT NULL,
    retry_count     INT             NOT NULL DEFAULT 0,
    max_retries     INT             NOT NULL DEFAULT 3,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_collect_task_status (status),
    FOREIGN KEY (source_id) REFERENCES collect_source(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采集任务';

-- 14. 采集执行日志
DROP TABLE IF EXISTS collect_log;
CREATE TABLE collect_log (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    task_id      BIGINT       NOT NULL COMMENT '关联任务ID',
    file_name    VARCHAR(200) DEFAULT NULL,
    total_count  INT          NOT NULL DEFAULT 0,
    success_count INT         NOT NULL DEFAULT 0,
    fail_count   INT          NOT NULL DEFAULT 0,
    error_msg    TEXT         DEFAULT NULL,
    start_time   DATETIME     NOT NULL,
    end_time     DATETIME     DEFAULT NULL,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_collect_log_task (task_id),
    INDEX idx_collect_log_time (start_time),
    FOREIGN KEY (task_id) REFERENCES collect_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采集执行日志';

-- ============================================================
-- 第六部分：统计结果表 (C 的中期离线分析写入)
-- ============================================================

-- 15. 岗位统计
DROP TABLE IF EXISTS stat_position;
CREATE TABLE stat_position (
    id            BIGINT         AUTO_INCREMENT PRIMARY KEY,
    stat_date     DATE           NOT NULL COMMENT '统计日期',
    stat_type     VARCHAR(20)    NOT NULL DEFAULT 'DAILY' COMMENT 'DAILY/WEEKLY/MONTHLY',
    total_count   INT            NOT NULL DEFAULT 0,
    new_count     INT            NOT NULL DEFAULT 0,
    hot_positions JSON           DEFAULT NULL COMMENT '热门岗位TOP20',
    growth_rate   DECIMAL(5,2)   DEFAULT NULL COMMENT '环比增长率(%)',
    create_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_stat_pos_date_type (stat_date, stat_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位统计结果';

-- 16. 薪资统计
DROP TABLE IF EXISTS stat_salary;
CREATE TABLE stat_salary (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    stat_date           DATE            NOT NULL,
    stat_type           VARCHAR(20)     NOT NULL DEFAULT 'DAILY',
    position_name       VARCHAR(100)    DEFAULT NULL COMMENT '岗位名称(NULL=全量)',
    avg_salary          DECIMAL(10,2)   DEFAULT NULL COMMENT '平均薪资(K)',
    median_salary       DECIMAL(10,2)   DEFAULT NULL COMMENT '中位数薪资(K)',
    salary_distribution JSON            DEFAULT NULL COMMENT '薪资区间分布',
    top_salary          JSON            DEFAULT NULL COMMENT '高薪岗位TOP20',
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_stat_salary_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资统计结果';

-- 17. 学历统计
DROP TABLE IF EXISTS stat_education;
CREATE TABLE stat_education (
    id             BIGINT          AUTO_INCREMENT PRIMARY KEY,
    stat_date      DATE            NOT NULL,
    stat_type      VARCHAR(20)     NOT NULL DEFAULT 'DAILY',
    education      VARCHAR(20)     NOT NULL COMMENT '学历',
    position_count INT             NOT NULL DEFAULT 0,
    avg_salary     DECIMAL(10,2)   DEFAULT NULL,
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_stat_edu_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学历统计结果';

-- 18. 技能统计
DROP TABLE IF EXISTS stat_skill;
CREATE TABLE stat_skill (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    stat_date   DATE            NOT NULL,
    stat_type   VARCHAR(20)     NOT NULL DEFAULT 'DAILY',
    skill_name  VARCHAR(100)    NOT NULL,
    frequency   INT             NOT NULL DEFAULT 0,
    trend       VARCHAR(10)     DEFAULT NULL COMMENT 'up/down/stable',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_stat_skill_date (stat_date),
    INDEX idx_stat_skill_name (skill_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能统计结果';

-- 19. 城市统计
DROP TABLE IF EXISTS stat_city;
CREATE TABLE stat_city (
    id             BIGINT          AUTO_INCREMENT PRIMARY KEY,
    stat_date      DATE            NOT NULL,
    stat_type      VARCHAR(20)     NOT NULL DEFAULT 'DAILY',
    city           VARCHAR(50)     NOT NULL,
    province       VARCHAR(50)     DEFAULT NULL,
    position_count INT             NOT NULL DEFAULT 0,
    avg_salary     DECIMAL(10,2)   DEFAULT NULL,
    rank_num       INT             DEFAULT NULL,
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_stat_city_date (stat_date),
    INDEX idx_stat_city_city (city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='城市统计结果';

-- 20. 企业统计
DROP TABLE IF EXISTS stat_company;
CREATE TABLE stat_company (
    id             BIGINT          AUTO_INCREMENT PRIMARY KEY,
    stat_date      DATE            NOT NULL,
    stat_type      VARCHAR(20)     NOT NULL DEFAULT 'DAILY',
    industry       VARCHAR(100)    DEFAULT NULL,
    company_count  INT             NOT NULL DEFAULT 0,
    position_count INT             NOT NULL DEFAULT 0,
    active_ranking JSON            DEFAULT NULL COMMENT '活跃度排名',
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_stat_comp_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业统计结果';

-- 21. 趋势统计
DROP TABLE IF EXISTS stat_trend;
CREATE TABLE stat_trend (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    stat_date   DATE            NOT NULL,
    stat_type   VARCHAR(20)     NOT NULL DEFAULT 'DAILY',
    daily_new   INT             NOT NULL DEFAULT 0 COMMENT '日新增',
    month_new   INT             NOT NULL DEFAULT 0 COMMENT '月新增',
    month_growth DECIMAL(5,2)   DEFAULT NULL COMMENT '月环比(%)',
    year_growth  DECIMAL(5,2)   DEFAULT NULL COMMENT '同比增长(%)',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_stat_trend_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='趋势统计结果';

-- ============================================================
-- 第七部分：报告生成 (D 的中期功能)
-- ============================================================

-- 22. 报告模板
DROP TABLE IF EXISTS report_template;
CREATE TABLE report_template (
    id            BIGINT          AUTO_INCREMENT PRIMARY KEY,
    template_name VARCHAR(100)    NOT NULL COMMENT '模板名称',
    template_type VARCHAR(50)     NOT NULL COMMENT 'monthly/yearly/skill/custom',
    template_file VARCHAR(200)    NOT NULL COMMENT '模板文件名(.ftl)',
    description   VARCHAR(500)    DEFAULT NULL,
    dimensions    JSON            DEFAULT NULL COMMENT '包含的分析维度',
    is_default    TINYINT         NOT NULL DEFAULT 0,
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1-启用 0-禁用',
    create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告模板';

-- 23. 报告生成记录
DROP TABLE IF EXISTS report_record;
CREATE TABLE report_record (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    template_id      BIGINT       NOT NULL,
    user_id          BIGINT       NOT NULL,
    report_title     VARCHAR(200) NOT NULL COMMENT '报告标题',
    time_range_start DATE         DEFAULT NULL,
    time_range_end   DATE         DEFAULT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/GENERATING/COMPLETED/FAILED',
    file_path        VARCHAR(500) DEFAULT NULL,
    file_size        BIGINT       DEFAULT NULL COMMENT '字节',
    error_msg        TEXT         DEFAULT NULL,
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_report_status (status),
    INDEX idx_report_user (user_id),
    INDEX idx_report_time (create_time),
    FOREIGN KEY (template_id) REFERENCES report_template(id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告生成记录';

-- ============================================================
-- 第八部分：开放 API 管理 (B 的中期功能)
-- ============================================================

-- 24. API 密钥
DROP TABLE IF EXISTS api_key;
CREATE TABLE api_key (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    api_key     VARCHAR(64)  NOT NULL UNIQUE,
    app_name    VARCHAR(100) NOT NULL COMMENT '应用名称',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-禁用',
    rate_limit  INT          NOT NULL DEFAULT 100 COMMENT '每分钟上限',
    total_calls BIGINT       NOT NULL DEFAULT 0,
    expire_time DATETIME     DEFAULT NULL COMMENT 'NULL=永不过期',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_api_key_status (status),
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API密钥';

-- 25. API 调用日志
DROP TABLE IF EXISTS api_call_log;
CREATE TABLE api_call_log (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    api_key_id  BIGINT       NOT NULL,
    api_path    VARCHAR(200) NOT NULL,
    method      VARCHAR(10)  NOT NULL,
    params      TEXT         DEFAULT NULL,
    ip          VARCHAR(50)  DEFAULT NULL,
    duration    BIGINT       DEFAULT NULL COMMENT '响应耗时(ms)',
    status_code INT          NOT NULL DEFAULT 200,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_api_log_key (api_key_id),
    INDEX idx_api_log_time (create_time),
    FOREIGN KEY (api_key_id) REFERENCES api_key(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API调用日志';

-- 重新启用外键检查
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 种子数据
-- ============================================================

-- 五个系统角色
INSERT INTO sys_role (role_name, role_code, description) VALUES
('系统管理员',   'ROLE_ADMIN',          '全部功能权限，含用户管理和系统配置'),
('数据分析员',   'ROLE_ANALYST',        '数据采集管理、分析、报告生成、API管理'),
('教师',         'ROLE_TEACHER',        '查看分析结果、下载报告、查看推荐'),
('学院管理员',   'ROLE_COLLEGE_ADMIN',  '查看本院数据、下载报告'),
('学生',         'ROLE_STUDENT',        '查看岗位推荐、浏览部分分析数据');

-- 基础权限（菜单 + 按钮）
INSERT INTO sys_permission (id, permission_name, permission_code, parent_id, type, path, icon, sort_order) VALUES
(1,  '数据大屏',     'dashboard:view',   0, 'menu', '/dashboard',   'DataAnalysis', 1),
(2,  '岗位分析',     'position:view',    0, 'menu', '/position',    'Briefcase',    2),
(3,  '报告中心',     'report:view',      0, 'menu', '/report',      'Document',     3),
(4,  '岗位推荐',     'recommend:view',   0, 'menu', '/recommend',   'Star',         4),
(5,  '系统管理',     'system:view',      0, 'menu', '/system',      'Setting',      9),
(6,  '用户管理',     'user:read',        5, 'menu', '/system/user',  NULL,           1),
(7,  '用户新增',     'user:create',      6, 'button', NULL,          NULL,           1),
(8,  '用户编辑',     'user:update',      6, 'button', NULL,          NULL,           2),
(9,  '用户删除',     'user:delete',      6, 'button', NULL,          NULL,           3),
(10, '角色管理',     'role:read',        5, 'menu', '/system/role',  NULL,           2),
(11, '角色编辑',     'role:update',      10,'button', NULL,          NULL,           1),
(12, '操作日志',     'log:read',         5, 'menu', '/system/log',   NULL,           3),
(13, '采集管理',     'collect:view',     5, 'menu', '/system/collect', NULL,         4),
(14, '采集启停',     'collect:toggle',   13,'button', NULL,          NULL,           1),
(15, 'API管理',      'api:view',         5, 'menu', '/system/api',   NULL,           5),
(16, 'API文档',      'api:docs',         0, 'menu', '/api-docs',    'Guide',        8);

-- 城市层级映射（37个城市，含中英文别名）
INSERT INTO city_tier (city, alias, province, tier) VALUES
('北京',   '北京市,Beijing',       '北京',   '一线'),
('上海',   '上海市,Shanghai',      '上海',   '一线'),
('广州',   '广州市,Guangzhou',     '广东',   '一线'),
('深圳',   '深圳市,Shenzhen',      '广东',   '一线'),
('杭州',   '杭州市,Hangzhou',      '浙江',   '新一线'),
('成都',   '成都市,Chengdu',       '四川',   '新一线'),
('武汉',   '武汉市,Wuhan',         '湖北',   '新一线'),
('南京',   '南京市,Nanjing',       '江苏',   '新一线'),
('西安',   '西安市,Xi''an',        '陕西',   '新一线'),
('重庆',   '重庆市,Chongqing',     '重庆',   '新一线'),
('长沙',   '长沙市,Changsha',      '湖南',   '新一线'),
('天津',   '天津市,Tianjin',       '天津',   '新一线'),
('苏州',   '苏州市,Suzhou',        '江苏',   '新一线'),
('郑州',   '郑州市,Zhengzhou',     '河南',   '新一线'),
('东莞',   '东莞市,Dongguan',      '广东',   '新一线'),
('青岛',   '青岛市,Qingdao',       '山东',   '新一线'),
('合肥',   '合肥市,Hefei',         '安徽',   '新一线'),
('佛山',   '佛山市,Foshan',        '广东',   '新一线'),
('宁波',   '宁波市,Ningbo',        '浙江',   '新一线'),
('厦门',   '厦门市,Xiamen',        '福建',   '二线'),
('福州',   '福州市,Fuzhou',        '福建',   '二线'),
('无锡',   '无锡市,Wuxi',          '江苏',   '二线'),
('济南',   '济南市,Jinan',         '山东',   '二线'),
('大连',   '大连市,Dalian',        '辽宁',   '二线'),
('昆明',   '昆明市,Kunming',       '云南',   '二线'),
('沈阳',   '沈阳市,Shenyang',      '辽宁',   '二线'),
('南昌',   '南昌市,Nanchang',      '江西',   '二线'),
('贵阳',   '贵阳市,Guiyang',       '贵州',   '二线'),
('南宁',   '南宁市,Nanning',       '广西',   '二线'),
('哈尔滨', '哈尔滨市,Harbin',      '黑龙江', '二线'),
('石家庄', '石家庄市,Shijiazhuang','河北',   '二线'),
('太原',   '太原市,Taiyuan',       '山西',   '二线'),
('海口',   '海口市,Haikou',        '海南',   '三线'),
('兰州',   '兰州市,Lanzhou',       '甘肃',   '三线'),
('银川',   '银川市,Yinchuan',      '宁夏',   '三线'),
('呼和浩特','呼和浩特市,Hohhot',   '内蒙古', '三线'),
('乌鲁木齐','乌鲁木齐市,Urumqi',   '新疆',   '三线');

-- 预置报告模板（供 D 中期使用）
INSERT INTO report_template (template_name, template_type, template_file, description, dimensions, is_default) VALUES
('月度就业分析报告', 'monthly', 'monthly_report.ftl', '月度岗位统计、薪资分析、技能需求、地域分布综合报告',
 '["position","salary","skill","city","education","trend"]', 1),
('年度趋势报告',     'yearly',  'yearly_report.ftl',  '年度就业趋势分析，含同比环比、行业变化趋势',
 '["position","salary","skill","trend","industry"]', 1),
('技能需求报告',     'skill',   'skill_report.ftl',   '热门技能排行、技能组合关联度、技能需求趋势分析',
 '["skill","salary","education"]', 1);

-- ============================================================
-- 管理员账号（初始密码: admin123）
-- ============================================================
INSERT INTO sys_user (username, password, real_name, email, status) VALUES
('admin', '$2a$10$R.ly2ozcryr0uP/apBwJBOfWv/eCIE3wwXkr7HCOXTd1Qc4IYfwZu', '系统管理员', 'admin@example.com', 1);

-- admin 分配系统管理员角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.role_code = 'ROLE_ADMIN';

-- 管理员角色分配全部 16 项权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'ROLE_ADMIN';

-- 数据分析员：全校分析、报告、采集、开放 API 和接口文档
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'ROLE_ANALYST'
  AND p.permission_code IN (
      'dashboard:view', 'position:view', 'report:view',
      'system:view', 'collect:view', 'collect:toggle',
      'api:view', 'api:docs');

-- 学院管理员：仅由后端数据范围组件限制到所属学院
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'ROLE_COLLEGE_ADMIN'
  AND p.permission_code IN ('dashboard:view', 'position:view', 'report:view');

-- 学生角色分配基础权限（数据大屏 + 岗位分析 + 岗位推荐）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'ROLE_STUDENT'
  AND p.permission_code IN ('dashboard:view', 'position:view', 'recommend:view');

-- 教师角色分配查看权限（数据大屏 + 岗位分析 + 报告中心 + 岗位推荐）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'ROLE_TEACHER'
  AND p.permission_code IN ('dashboard:view', 'position:view', 'report:view', 'recommend:view');
