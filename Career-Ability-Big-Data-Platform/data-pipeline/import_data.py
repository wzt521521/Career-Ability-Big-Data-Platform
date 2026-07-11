"""
数据导入脚本 — 读取 CSV/Excel → 标准化 JSON → LPUSH 到 Redis List
用法: python import_data.py [文件路径]
      不传参数则使用 config.py 中的默认路径
"""
import sys
import json
import hashlib
from datetime import datetime
import pandas as pd
import redis
from config import (
    REDIS_HOST, REDIS_PORT, REDIS_DB,
    RAW_QUEUE, DEFAULT_CSV_PATH, BATCH_SIZE
)


def load_city_mapping():
    """加载城市→省份→层级映射表"""
    try:
        with open("city_mapping.json", "r", encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        print("[WARN] city_mapping.json 未找到，城市标准化将跳过")
        return {}


def normalize_city(raw, mapping):
    """城市标准化：查映射表，返回 {city, province, tier}"""
    if not raw or pd.isna(raw):
        return {"city": None, "province": None, "tier": None}
    raw = str(raw).strip()
    if raw in mapping:
        m = mapping[raw]
        return {"city": raw, "province": m["province"], "tier": m["tier"]}
    return {"city": raw, "province": None, "tier": "其他"}


def parse_salary(raw):
    """
    薪资解析 → {min, max} 单位 K
    支持格式: "8K-15K", "8000-15000", "8-15K", "面议", 纯数字
    """
    if raw is None or pd.isna(raw):
        return {"min": None, "max": None}

    s = str(raw).strip()

    # 去掉 K/k、空格、货币符号、单位（先清理再做面议判断）
    s = s.replace("K", "").replace("k", "").replace(" ", "").replace("￥", "").replace("¥", "")
    s = s.replace("/月", "").replace("/month", "").replace("每月", "")

    # "面议" 或类似表述（清理后再判断，避免空格/大小写干扰）
    s_lower = s.lower()
    if any(w in s_lower for w in ["面议", "薪资open", "open", "negotiable", "薪资面议"]):
        return {"min": 0, "max": 0}

    # 尝试用 - 或 ~ 分割
    import re
    parts = re.split(r"[-~–—到至]", s)
    if len(parts) == 2:
        try:
            lo = float(parts[0])
            hi = float(parts[1])
            # 如果数字 > 100 说明是元，转 K
            if lo > 100:
                lo /= 1000
            if hi > 100:
                hi /= 1000
            return {"min": int(lo), "max": int(hi)}
        except ValueError:
            pass

    # 单个数字
    try:
        val = float(s)
        if val > 100:
            val /= 1000
        return {"min": int(val), "max": int(val)}
    except ValueError:
        pass

    return {"min": None, "max": None}


def make_job_id(row, idx):
    """生成唯一 jobId"""
    # 优先用原始 ID
    for col in ["Job ID", "job_id", "jobId", "id", "ID", "index"]:
        if col in row and not pd.isna(row[col]):
            return str(row[col])
    # fallback：用行号 + 来源标识
    return f"import-{datetime.now().strftime('%Y%m%d')}-{idx:06d}"


def row_to_json(row, idx, city_map):
    """单行 CSV → 标准 JSON"""
    # 列映射：优先匹配常见列名（支持 Kaggle / synthetic 等多来源）
    title = row.get("Job Title") or row.get("job_title") or row.get("Title") or row.get("title") or row.get("position") or ""
    company_name = row.get("Company") or row.get("Company Name") or row.get("company") or row.get("company_name") or row.get("Employer") or row.get("companyName") or ""
    location = row.get("Location") or row.get("location") or row.get("City") or row.get("city") or row.get("Region") or row.get("region") or ""
    education = row.get("Qualification") or row.get("education") or row.get("Education") or row.get("Degree") or ""
    experience = row.get("Experience") or row.get("experience") or row.get("Seniority") or ""
    salary_raw = row.get("Salary") or row.get("salary") or row.get("Salary Range") or row.get("Avg Salary") or row.get("Avg Salary(K)") or ""
    skills_raw = row.get("Skills") or row.get("skills") or row.get("Key Skills") or row.get("Technologies") or ""
    desc = row.get("Job Description") or row.get("job_description") or row.get("Description") or row.get("description") or row.get("Job_Description") or ""
    publish_date = row.get("Publish Date") or row.get("publish_date") or row.get("Date") or row.get("Post Date") or row.get("publishDate") or ""
    company_size = row.get("Company Size") or row.get("company_size") or row.get("Size") or row.get("companySize") or ""
    industry = row.get("Industry") or row.get("industry") or row.get("Sector") or row.get("industry") or ""
    company_type = row.get("Company Type") or row.get("company_type") or row.get("Type") or row.get("companyType") or ""
    welfare = row.get("Welfare") or row.get("welfare") or row.get("Benefits") or ""
    source_url = row.get("source_url") or row.get("Source URL") or row.get("URL") or row.get("url") or row.get("sourceUrl") or ""
    province_raw = row.get("province") or row.get("Province") or ""
    city_tier_raw = row.get("cityTier") or row.get("CityTier") or ""

    # 薪资解析：优先使用预分离的 min/max 列，其次解析 Salary 字符串
    salary_min_raw = row.get("salaryMin") or row.get("Salary Min") or None
    salary_max_raw = row.get("salaryMax") or row.get("Salary Max") or None
    if salary_min_raw is not None and not pd.isna(salary_min_raw) and salary_max_raw is not None and not pd.isna(salary_max_raw):
        salary = parse_salary(f"{salary_min_raw}-{salary_max_raw}")
    else:
        salary = parse_salary(salary_raw)

    # 城市标准化：优先用 city 列查映射，辅以 province/tier 直传
    city_info = normalize_city(location, city_map)
    if not city_info["province"] and province_raw and not pd.isna(province_raw):
        city_info["province"] = str(province_raw).strip()
    if not city_info["tier"] and city_tier_raw and not pd.isna(city_tier_raw):
        city_info["tier"] = str(city_tier_raw).strip()

    # 技能预处理（支持逗号或竖线分隔）
    skills = []
    if skills_raw and not pd.isna(skills_raw):
        if isinstance(skills_raw, str):
            skills = [s.strip() for s in skills_raw.replace("|", ",").replace("，", ",").split(",") if s.strip()]
        elif isinstance(skills_raw, list):
            skills = skills_raw

    # 福利预处理（支持逗号或竖线分隔）
    welfare_list = []
    if welfare and not pd.isna(welfare):
        if isinstance(welfare, str):
            welfare_list = [w.strip() for w in welfare.replace("|", ",").replace("，", ",").split(",") if w.strip()]
        elif isinstance(welfare, list):
            welfare_list = welfare

    # 发布日期预处理
    pub_date = None
    if publish_date and not pd.isna(publish_date):
        try:
            pub_date = str(pd.to_datetime(publish_date).date())
        except Exception:
            pub_date = str(publish_date)[:10]

    return {
        "jobId": make_job_id(row, idx),
        "title": str(title).strip() if title and not pd.isna(title) else "",
        "company": {
            "name": str(company_name).strip() if company_name and not pd.isna(company_name) else "",
            "size": str(company_size).strip() if company_size and not pd.isna(company_size) else None,
            "industry": str(industry).strip() if industry and not pd.isna(industry) else None,
            "type": str(company_type).strip() if company_type and not pd.isna(company_type) else None
        },
        "salary": salary,
        "city": city_info["city"],
        "province": city_info["province"],
        "cityTier": city_info["tier"],
        "education": str(education).strip() if education and not pd.isna(education) else None,
        "experience": str(experience).strip() if experience and not pd.isna(experience) else None,
        "skills": skills,
        "welfare": welfare_list,
        "description": str(desc).strip() if desc and not pd.isna(desc) else None,
        "publishDate": pub_date,
        "sourceUrl": str(source_url).strip() if source_url and not pd.isna(source_url) else "",
        "crawlTime": datetime.now().strftime("%Y-%m-%dT%H:%M:%S")
    }


def main():
    # 1. 确定文件路径
    file_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_CSV_PATH
    print(f"[INFO] 数据源: {file_path}")

    # 2. 根据后缀读取
    if file_path.endswith(".csv"):
        df = pd.read_csv(file_path, encoding="utf-8")
    elif file_path.endswith((".xlsx", ".xls")):
        df = pd.read_excel(file_path)
    else:
        print(f"[ERROR] 不支持的文件格式: {file_path}")
        sys.exit(1)

    print(f"[INFO] 总行数: {len(df)}")

    # 3. 加载城市映射
    city_map = load_city_mapping()

    # 4. 连接 Redis
    r = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, db=REDIS_DB,
                    decode_responses=True)
    r.ping()
    print(f"[INFO] Redis 连接成功: {REDIS_HOST}:{REDIS_PORT}")

    # 5. 逐行处理
    success = 0
    skip = 0
    for idx, (_, row) in enumerate(df.iterrows()):
        job_json = row_to_json(row, idx, city_map)

        # 跳过无效行：岗位名和公司名都为空
        if not job_json["title"] and not job_json["company"]["name"]:
            skip += 1
            continue

        json_str = json.dumps(job_json, ensure_ascii=False)
        r.lpush(RAW_QUEUE, json_str)
        success += 1

        if success % BATCH_SIZE == 0:
            print(f"  进度: {success} 条已导入...")

    # 6. 打印结果
    queue_len = r.llen(RAW_QUEUE)
    print(f"\n{'='*50}")
    print(f"导入完成: 成功 {success} 条, 跳过 {skip} 条")
    print(f"Redis List [{RAW_QUEUE}] 当前长度: {queue_len}")
    print(f"{'='*50}")


if __name__ == "__main__":
    main()
