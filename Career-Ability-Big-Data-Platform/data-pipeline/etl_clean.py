"""
ETL 清洗脚本 — 常驻进程
BRPOP 消费 Redis raw-job-data → 清洗 → 写入 MySQL + LPUSH cleaned-job-data
用法: python etl_clean.py
      Ctrl+C 优雅退出，打印统计
"""
import json
import hashlib
import re
import signal
import sys
import time
from datetime import datetime

import pymysql
import redis

from config import (
    REDIS_HOST, REDIS_PORT, REDIS_DB,
    RAW_QUEUE, CLEANED_QUEUE,
    MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE,
    BATCH_SIZE
)

# ETL 内部日志间隔（与导入 BATCH_SIZE 解耦）
LOG_INTERVAL = 100


# ============================================================
# 加载词典
# ============================================================

def load_city_mapping():
    try:
        with open("city_mapping.json", "r", encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        print("[WARN] city_mapping.json 未找到")
        return {}


def load_skill_dict():
    """加载技能词典，返回 (keywords_set, aliases_map)"""
    try:
        with open("skill_dict.json", "r", encoding="utf-8") as f:
            data = json.load(f)
    except FileNotFoundError:
        print("[WARN] skill_dict.json 未找到")
        return set(), {}

    keywords = set()
    for cat_list in data.get("categories", {}).values():
        for kw in cat_list:
            keywords.add(kw.lower())

    aliases = {}
    for alias, target in data.get("aliases", {}).items():
        aliases[alias.lower()] = target

    return keywords, aliases


# ============================================================
# 标准化函数
# ============================================================

def normalize_salary(salary_obj):
    """薪资标准化 — 入职时已经是 {min, max} 格式，这里做二次校正"""
    if not salary_obj:
        return {"min": None, "max": None}

    lo = salary_obj.get("min")
    hi = salary_obj.get("max")

    # 转 int（失败则置 None，避免后续字符串与整数比较抛 TypeError）
    try:
        lo = int(lo) if lo is not None else None
        hi = int(hi) if hi is not None else None
    except (ValueError, TypeError):
        lo = None
        hi = None

    # 年薪转月薪（>80 认为可能是年薪，除以12）
    if lo and lo > 80:
        lo = int(lo / 12)
    if hi and hi > 80:
        hi = int(hi / 12)

    # 确保 min <= max
    if lo and hi and lo > hi:
        lo, hi = hi, lo

    return {"min": lo, "max": hi}


def normalize_city(city_name, province_name, city_map):
    """
    城市标准化
    优先用 providence/city 查映射表，返回 (city, province, tier)
    """
    raw = city_name or province_name
    if not raw:
        return None, None, None

    raw = str(raw).strip()

    # 直接匹配
    if raw in city_map:
        m = city_map[raw]
        return raw, m["province"], m["tier"]

    # 模糊匹配：去掉"市"再试
    simple = raw.replace("市", "").replace("City", "").strip()
    if simple in city_map:
        m = city_map[simple]
        return simple, m["province"], m["tier"]

    # 加"市"再试
    with_shi = raw + "市"
    if with_shi in city_map:
        m = city_map[with_shi]
        return with_shi, m["province"], m["tier"]

    return raw, None, "其他"


def normalize_education(raw):
    """学历标准化 → 不限/大专/本科/硕士/博士"""
    if not raw:
        return None
    s = str(raw).strip().lower()

    if any(w in s for w in ["博士", "phd", "doctorate", "doctoral"]):
        return "博士"
    if any(w in s for w in ["硕士", "master", "msc", "mba", "graduate"]):
        return "硕士"
    if any(w in s for w in ["本科", "bachelor", "bs", "ba", "undergraduate", "本科及以上"]):
        return "本科"
    if any(w in s for w in ["大专", "associate", "college", "diploma", "专科"]):
        return "大专"
    if any(w in s for w in ["高中", "中专", "high school", "secondary"]):
        return "不限"
    if any(w in s for w in ["不限", "无", "学历不限", "any"]):
        return "不限"

    return None


def normalize_experience(raw):
    """经验标准化 → 不限/应届/1-3年/3-5年/5-10年/10年以上"""
    if not raw:
        return None
    s = str(raw).strip().lower()

    # ① 应届 / 不限（最明确的关键词，优先匹配）
    if any(w in s for w in ["应届", "fresh", "graduate", "毕业生", "实习", "intern", "无经验", "entry"]):
        return "应届"
    if any(w in s for w in ["不限", "any", "经验不限"]):
        return "不限"

    # ② 范围模式（比单数字更精确，优先匹配）
    if any(w in s for w in ["5-10", "五到十", "5到10"]):
        return "5-10年"
    if any(w in s for w in ["3-5", "三到五", "3到5", "三至五"]):
        return "3-5年"
    if any(w in s for w in ["1-3", "一到三", "1到3", "一至三"]):
        return "1-3年"

    # ③ 中文数字词
    if any(w in s for w in ["十年"]):
        return "10年以上"
    if any(w in s for w in ["五年"]):
        return "5-10年"
    if any(w in s for w in ["三年", "四年"]):
        return "3-5年"
    if any(w in s for w in ["一年", "两年", "二年"]):
        return "1-3年"

    # ④ 英文
    if any(w in s for w in ["senior", "ten"]):
        return "10年以上"

    # ⑤ 提取数值做区间判断（避免 "6" in "16年" 这类误匹配）
    nums = re.findall(r'\d+', s)
    if nums:
        max_years = max(int(n) for n in nums)
        if max_years >= 10:
            return "10年以上"
        elif max_years >= 5:
            return "5-10年"
        elif max_years >= 3:
            return "3-5年"
        elif max_years >= 1:
            return "1-3年"

    return None


def extract_skills(title, description, skills_arr, keywords, aliases):
    """
    技能提取：在 title + description 中匹配关键词
    返回标准化后的技能列表
    """
    text = " ".join([
        str(title or ""),
        str(description or "")
    ]).lower()

    matched = set()

    # 原始 skills 数组中的技能也加入匹配（统一转首字母大写）
    if skills_arr and isinstance(skills_arr, list):
        for sk in skills_arr:
            sk_str = str(sk).strip()
            sk_lower = sk_str.lower()
            if sk_lower in aliases:
                matched.add(aliases[sk_lower])
            elif sk_lower in keywords:
                matched.add(sk_lower)
            else:
                matched.add(sk_str)

    # 关键词匹配
    for kw in keywords:
        if kw in text:
            matched.add(kw)

    # 别名反向匹配
    for alias, target in aliases.items():
        # 只匹配多词别名（避免过于宽泛的单字匹配）
        if " " in alias or len(alias) > 5:
            if alias in text:
                matched.add(target)

    # 大小写规范化：统一转 .title()（Spring Boot / Vue / React 等专有名词保持不变）
    preserve_case = {
        "ios", "macos", "ipados", "tvos", "watchos", "visionos",
        "php", "api", "sql", "nosql", "json", "xml", "html", "css",
        "aws", "gcp", "ci/cd", "nlp", "cv", "llm", "gpt", "bert",
        "k8s", "grpc", "restful", "http", "https", "tcp/ip", "dns",
        "vpn", "cdn", "jwt", "oauth", "oauth2", "sam", "csrf", "xss",
        "ssrf", "ddos", "cors", "ssl", "tls", "waf", "ids", "ips",
        "jvm", "gc", "juc", "jdbc", "jpa", "mvc", "mvp", "mvvm",
        "oop", "tdd", "bdd", "ddd", "uml", "erd", "etl", "elt",
        "saas", "paas", "iaas", "faas", "baas", "sre", "aiops",
    }
    result = []
    for m in matched:
        if not m or len(m) > 30:
            continue
        lower_m = m.lower()
        if lower_m in preserve_case:
            result.append(lower_m)
        elif m.islower() or m.isupper():
            # 全小写或全大写 → 转 title case
            result.append(m.title())
        else:
            result.append(m)

    return sorted(set(result), key=str.lower)


def compute_md5(job_id, source_url):
    """计算去重 MD5"""
    raw = f"{job_id}|{source_url or ''}"
    return hashlib.md5(raw.encode("utf-8")).hexdigest()


# ============================================================
# MySQL 操作
# ============================================================

def get_or_create_company(cursor, company_obj):
    """查询或创建企业，返回 company_id"""
    name = (company_obj.get("name") or "").strip()
    if not name:
        return None

    cursor.execute("SELECT id FROM job_company WHERE company_name = %s", (name,))
    row = cursor.fetchone()
    if row:
        return row[0]

    cursor.execute(
        """INSERT INTO job_company (company_name, company_size, industry, company_type)
           VALUES (%s, %s, %s, %s)""",
        (name,
         company_obj.get("size") or None,
         company_obj.get("industry") or None,
         company_obj.get("type") or None)
    )
    return cursor.lastrowid


def check_duplicate(cursor, source_md5):
    """检查是否已存在（MD5 去重）"""
    if not source_md5:
        return False
    cursor.execute(
        "SELECT COUNT(*) FROM job_position WHERE source_md5 = %s",
        (source_md5,)
    )
    return cursor.fetchone()[0] > 0


def insert_position(cursor, job, company_id, source_md5):
    """写入岗位记录"""
    skills_json = json.dumps(job.get("skills") or [], ensure_ascii=False)
    welfare_json = json.dumps(job.get("welfare") or [], ensure_ascii=False)

    pub_date = job.get("publishDate") or None
    if pub_date and isinstance(pub_date, str) and len(pub_date) > 10:
        pub_date = pub_date[:10]

    cursor.execute(
        """INSERT INTO job_position
           (job_id, title, company_id, salary_min, salary_max,
            city, province, city_tier, education, experience,
            skills, welfare, description, publish_date,
            source_url, source_md5)
           VALUES (%s,%s,%s,%s,%s, %s,%s,%s,%s,%s, %s,%s,%s,%s, %s,%s)""",
        (job.get("jobId"),
         job.get("title"),
         company_id,
         job.get("salary", {}).get("min"),
         job.get("salary", {}).get("max"),
         job.get("city"),
         job.get("province"),
         job.get("cityTier"),
         job.get("education"),
         job.get("experience"),
         skills_json,
         welfare_json,
         job.get("description"),
         pub_date,
         job.get("sourceUrl"),
         source_md5)
    )


# ============================================================
# 主循环
# ============================================================

# 统计
stats = {
    "consumed": 0,
    "success": 0,
    "null_discard": 0,
    "duplicate": 0,
    "error": 0,
}
running = True


def handle_signal(sig, frame):
    global running
    print("\n[INFO] 收到退出信号，正在关闭...")
    running = False


signal.signal(signal.SIGINT, handle_signal)
signal.signal(signal.SIGTERM, handle_signal)


def print_stats():
    print(f"\n{'='*50}")
    print(f"总消费:     {stats['consumed']}")
    print(f"成功写入:   {stats['success']}")
    print(f"空值丢弃:   {stats['null_discard']}")
    print(f"重复跳过:   {stats['duplicate']}")
    print(f"异常失败:   {stats['error']}")
    print(f"{'='*50}")


def main():
    global stats

    print("[INFO] ETL 清洗脚本启动")
    print(f"      消费队列: {RAW_QUEUE}")
    print(f"      生产队列: {CLEANED_QUEUE}")

    # 加载词典
    city_map = load_city_mapping()
    print(f"[INFO] 城市映射: {len(city_map)} 条")

    keywords, aliases = load_skill_dict()
    print(f"[INFO] 技能关键词: {len(keywords)} 个, 别名: {len(aliases)} 个")

    # 连接 Redis
    r = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, db=REDIS_DB,
                    decode_responses=True)
    r.ping()
    print(f"[INFO] Redis: {REDIS_HOST}:{REDIS_PORT}")

    # 连接 MySQL
    conn = pymysql.connect(
        host=MYSQL_HOST, port=MYSQL_PORT,
        user=MYSQL_USER, password=MYSQL_PASSWORD,
        database=MYSQL_DATABASE, charset="utf8mb4"
    )
    cursor = conn.cursor()
    print(f"[INFO] MySQL: {MYSQL_HOST}:{MYSQL_PORT}/{MYSQL_DATABASE}")

    print("\n[INFO] 开始消费数据...\n")

    while running:
        try:
            # BRPOP 阻塞等待，超时 5 秒（便于检查退出信号）
            result = r.brpop(RAW_QUEUE, timeout=5)
            if result is None:
                continue

            _, raw_json = result
            stats["consumed"] += 1

            # ---- 解析 ----
            try:
                job = json.loads(raw_json)
            except json.JSONDecodeError:
                stats["error"] += 1
                continue

            # ---- ① 空值检查 ----
            title = (job.get("title") or "").strip()
            company_name = (job.get("company", {}).get("name") or "").strip()
            if not title or not company_name:
                stats["null_discard"] += 1
                if stats["consumed"] % LOG_INTERVAL == 0:
                    print(f"  [{stats['consumed']}] 消费/成功/丢弃/重复/异常: "
                          f"{stats['consumed']}/{stats['success']}/"
                          f"{stats['null_discard']}/{stats['duplicate']}/{stats['error']}")
                continue

            # ---- ② 薪资标准化 ----
            job["salary"] = normalize_salary(job.get("salary"))

            # ---- ③ 城市标准化 ----
            city, province, tier = normalize_city(
                job.get("city"), job.get("province"), city_map
            )
            job["city"] = city
            job["province"] = province
            job["cityTier"] = tier

            # ---- ④ 学历标准化 ----
            job["education"] = normalize_education(job.get("education"))

            # ---- ⑤ 经验标准化 ----
            job["experience"] = normalize_experience(job.get("experience"))

            # ---- ⑥ 技能提取 ----
            job["skills"] = extract_skills(
                job.get("title"),
                job.get("description"),
                job.get("skills"),
                keywords,
                aliases
            )

            # ---- ⑦ MD5 去重 ----
            source_md5 = compute_md5(job.get("jobId"), job.get("sourceUrl"))
            job["sourceMd5"] = source_md5

            if check_duplicate(cursor, source_md5):
                stats["duplicate"] += 1
                if stats["consumed"] % LOG_INTERVAL == 0:
                    print(f"  [{stats['consumed']}] 消费/成功/丢弃/重复/异常: "
                          f"{stats['consumed']}/{stats['success']}/"
                          f"{stats['null_discard']}/{stats['duplicate']}/{stats['error']}")
                continue

            # ---- ⑧ 写入 MySQL ----
            company_id = get_or_create_company(cursor, job.get("company", {}))
            insert_position(cursor, job, company_id, source_md5)
            conn.commit()

            # ---- 产入清洗队列 ----
            cleaned_json = json.dumps(job, ensure_ascii=False)
            r.lpush(CLEANED_QUEUE, cleaned_json)

            stats["success"] += 1

            # 进度日志
            if stats["success"] % 10 == 0:
                print(f"  [{stats['consumed']}/{stats['success']}] "
                      f"OK  {job.get('jobId')}  {title[:30]}")

        except Exception as e:
            stats["error"] += 1
            print(f"  [ERROR] {e}")
            try:
                conn.rollback()
            except Exception:
                pass

    # 退出
    cursor.close()
    conn.close()
    r.close()
    print_stats()
    print("[INFO] ETL 清洗脚本已退出")


if __name__ == "__main__":
    main()
