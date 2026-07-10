"""
数据管道 — 连接配置
所有连接信息从环境变量读取，Docker 容器启动时注入。
本地开发可在命令行设置或直接修改默认值。
"""
import os

# Redis
REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", 6379))
REDIS_DB = int(os.getenv("REDIS_DB", 0))

# Redis List Key
RAW_QUEUE = "queue:raw-job-data"
CLEANED_QUEUE = "queue:cleaned-job-data"

# MySQL
MYSQL_HOST = os.getenv("MYSQL_HOST", "localhost")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", 3307))
MYSQL_USER = os.getenv("MYSQL_USER", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "root123")
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "career_ability")

# CSV 导入
DEFAULT_CSV_PATH = os.getenv("CSV_PATH", "../data/test_sample.csv")
BATCH_SIZE = 100  # 每 N 条打印一次进度
