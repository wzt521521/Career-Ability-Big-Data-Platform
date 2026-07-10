"""
全链路测试脚本: CSV → Redis → ETL → MySQL
验证所有 Bug 修复
"""
import sys, json
sys.path.insert(0, '.')

# ============ STEP 1: Import CSV to Redis ============
import pandas as pd
import redis
from config import REDIS_HOST, REDIS_PORT, REDIS_DB, RAW_QUEUE, CLEANED_QUEUE

r = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, db=REDIS_DB, decode_responses=True)

# Clear queues first
r.delete(RAW_QUEUE, CLEANED_QUEUE)

df = pd.read_csv('../data/test_sample.csv')
from import_data import row_to_json, load_city_mapping
city_map = load_city_mapping()

imported = 0
for idx, (_, row) in enumerate(df.iterrows()):
    job = row_to_json(row, idx, city_map)
    if not job['title'] and not job['company']['name']:
        continue
    r.lpush(RAW_QUEUE, json.dumps(job, ensure_ascii=False))
    imported += 1

print(f'[IMPORT] {imported} records -> Redis (raw queue len={r.llen(RAW_QUEUE)})')

# ============ STEP 2: Run ETL (FIXED code) ============
import pymysql
from config import MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE

# Clear MySQL
conn0 = pymysql.connect(
    host=MYSQL_HOST, port=MYSQL_PORT,
    user=MYSQL_USER, password=MYSQL_PASSWORD,
    database=MYSQL_DATABASE, charset='utf8mb4'
)
c0 = conn0.cursor()
c0.execute('SET FOREIGN_KEY_CHECKS=0')
c0.execute('TRUNCATE TABLE job_position')
c0.execute('TRUNCATE TABLE job_company')
c0.execute('SET FOREIGN_KEY_CHECKS=1')
conn0.commit()
c0.close()
conn0.close()
print('[MYSQL] Tables truncated')

# Load dictionaries
with open('city_mapping.json', 'r', encoding='utf-8') as f:
    city_map2 = json.load(f)
with open('skill_dict.json', 'r', encoding='utf-8') as f:
    skill_data = json.load(f)

keywords = set()
for cat_list in skill_data.get('categories', {}).values():
    for kw in cat_list:
        keywords.add(kw.lower())
aliases = {}
for alias, target in skill_data.get('aliases', {}).items():
    aliases[alias.lower()] = target

from etl_clean import (
    normalize_salary, normalize_city, normalize_education,
    normalize_experience, extract_skills, compute_md5
)

conn = pymysql.connect(
    host=MYSQL_HOST, port=MYSQL_PORT,
    user=MYSQL_USER, password=MYSQL_PASSWORD,
    database=MYSQL_DATABASE, charset='utf8mb4'
)
cursor = conn.cursor()

stats = {'consumed': 0, 'success': 0, 'null_discard': 0, 'duplicate': 0, 'error': 0}
results = []

while True:
    result = r.brpop(RAW_QUEUE, timeout=2)
    if result is None:
        break
    _, raw_json = result
    stats['consumed'] += 1

    try:
        job = json.loads(raw_json)
    except json.JSONDecodeError:
        stats['error'] += 1
        continue

    title = (job.get('title') or '').strip()
    company_name = (job.get('company', {}).get('name') or '').strip()
    if not title or not company_name:
        stats['null_discard'] += 1
        continue

    original_exp = job.get('experience')
    original_edu = job.get('education')
    original_city = job.get('city')
    original_salary = dict(job.get('salary') or {})

    job['salary'] = normalize_salary(job.get('salary'))
    city, province, tier = normalize_city(job.get('city'), job.get('province'), city_map2)
    job['city'] = city
    job['province'] = province
    job['cityTier'] = tier
    job['education'] = normalize_education(job.get('education'))
    job['experience'] = normalize_experience(job.get('experience'))
    job['skills'] = extract_skills(
        job.get('title'), job.get('description'),
        job.get('skills'), keywords, aliases
    )

    source_md5 = compute_md5(job.get('jobId'), job.get('sourceUrl'))
    job['sourceMd5'] = source_md5

    cursor.execute(
        'SELECT COUNT(*) FROM job_position WHERE source_md5 = %s',
        (source_md5,)
    )
    if cursor.fetchone()[0] > 0:
        stats['duplicate'] += 1
        continue

    # get_or_create_company
    cursor.execute(
        'SELECT id FROM job_company WHERE company_name = %s',
        (company_name,)
    )
    row = cursor.fetchone()
    if row:
        company_id = row[0]
    else:
        cursor.execute(
            'INSERT INTO job_company (company_name, company_size, industry, company_type) '
            'VALUES (%s,%s,%s,%s)',
            (company_name,
             job.get('company', {}).get('size') or None,
             job.get('company', {}).get('industry') or None,
             job.get('company', {}).get('type') or None)
        )
        company_id = cursor.lastrowid

    skills_json = json.dumps(job.get('skills') or [], ensure_ascii=False)
    welfare_json = json.dumps(job.get('welfare') or [], ensure_ascii=False)
    pub_date = job.get('publishDate') or None
    if pub_date and isinstance(pub_date, str) and len(pub_date) > 10:
        pub_date = pub_date[:10]

    cursor.execute(
        'INSERT INTO job_position '
        '(job_id, title, company_id, salary_min, salary_max, '
        'city, province, city_tier, education, experience, '
        'skills, welfare, description, publish_date, source_url, source_md5) '
        'VALUES (%s,%s,%s,%s,%s, %s,%s,%s,%s,%s, %s,%s,%s,%s, %s,%s)',
        (job.get('jobId'), job.get('title'), company_id,
         job.get('salary', {}).get('min'), job.get('salary', {}).get('max'),
         job.get('city'), job.get('province'), job.get('cityTier'),
         job.get('education'), job.get('experience'),
         skills_json, welfare_json, job.get('description'),
         pub_date, job.get('sourceUrl'), source_md5)
    )
    conn.commit()

    r.lpush(CLEANED_QUEUE, json.dumps(job, ensure_ascii=False))
    stats['success'] += 1

    results.append({
        'title': title,
        'orig_exp': original_exp,
        'norm_exp': job['experience'],
        'orig_edu': original_edu,
        'norm_edu': job['education'],
        'orig_city': original_city,
        'norm_city': job['city'],
        'province': job['province'],
        'tier': job['cityTier'],
        'orig_sal': original_salary,
        'norm_sal': job['salary'],
        'skill_count': len(job.get('skills') or []),
        'skills': job.get('skills', [])[:5]
    })

cursor.close()
conn.close()

print(f'[ETL] consumed={stats["consumed"]} success={stats["success"]} '
      f'discard={stats["null_discard"]} dup={stats["duplicate"]} err={stats["error"]}')
print(f'[ETL] cleaned queue len = {r.llen(CLEANED_QUEUE)}')
r.close()

# ============ STEP 3: Verification ============
print()
print('=' * 125)
header = (f'{"Title":<24} {"OrigExp":<14} {"NormExp":<12} '
          f'{"OrigEdu":<8} {"NormEdu":<8} {"OrigCity":<8} '
          f'{"City":<8} {"Province":<8} {"Tier":<8} {"Salary":<14} {"Skills":<6}')
print(header)
print('-' * 125)
for rec in results:
    s = rec['norm_sal']
    if s.get('min') is not None and s.get('max') is not None:
        sal_str = f'{s["min"]}-{s["max"]}K'
    else:
        sal_str = 'N/A'
    print(f'{rec["title"]:<24} {str(rec["orig_exp"]):<14} {str(rec["norm_exp"]):<12} '
          f'{str(rec["orig_edu"]):<8} {str(rec["norm_edu"]):<8} '
          f'{str(rec["orig_city"]):<8} {str(rec["norm_city"]):<8} '
          f'{str(rec["province"]):<8} {str(rec["tier"]):<8} {sal_str:<14} {rec["skill_count"]}')

# ============ STEP 4: Critical Bug Checks ============
print()
print('=' * 60)
print('BUG VERIFICATION')
print('=' * 60)

# Bug #1: "5-10年" should NOT become "10年以上"
bug1 = [r for r in results if '5-10' in str(r['orig_exp'])]
all_pass = True
for r in bug1:
    if r['norm_exp'] == '10年以上':
        print(f'FAIL Bug#1: {r["title"]} orig={r["orig_exp"]} -> {r["norm_exp"]} (should be 5-10年)')
        all_pass = False
if all_pass and bug1:
    print(f'PASS Bug#1: {len(bug1)} records with 5-10年 ALL correctly classified')
elif not bug1:
    print('N/A Bug#1: No 5-10年 data in test set')

# Bug #2: Experience "5-10年" should return "5-10年" not other
print()
print('Quick unit test for normalize_experience:')
test_cases = [
    ('5-10年', '5-10年'),
    ('10年以上', '10年以上'),
    ('1-3年', '1-3年'),
    ('3-5年', '3-5年'),
    ('应届', '应届'),
    ('本科', None),  # no experience info, should return None
    ('16年', '10年以上'),  # 16 years >= 10
    ('14年工作经验', '10年以上'),
    ('6年', '5-10年'),
    ('4年以上', '3-5年'),
    ('2年', '1-3年'),
]
for inp, expected in test_cases:
    got = normalize_experience(inp)
    status = 'PASS' if got == expected else f'FAIL (got {got})'
    print(f'  {status}: normalize_experience({inp!r}) = {got!r} (expected {expected!r})')

# Bug #5: normalize_salary with string input should not crash
print()
print('Quick unit test for normalize_salary:')
sal_tests = [
    ({'min': 15, 'max': 25}, {'min': 15, 'max': 25}),
    ({'min': 0, 'max': 0}, {'min': 0, 'max': 0}),
    ({'min': None, 'max': None}, {'min': None, 'max': None}),
    ({'min': 200, 'max': 400}, {'min': 16, 'max': 33}),  # annual -> monthly
    ({'min': 'invalid', 'max': 'data'}, {'min': None, 'max': None}),  # Bug#5 fix
]
for inp, expected in sal_tests:
    got = normalize_salary(dict(inp))
    status = 'PASS' if got == expected else f'FAIL (got {got})'
    print(f'  {status}: normalize_salary({inp}) = {got} (expected {expected})')

print()
print('=== ALL TESTS COMPLETE ===')
