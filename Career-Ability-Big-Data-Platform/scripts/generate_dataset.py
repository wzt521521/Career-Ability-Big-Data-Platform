"""
生成 >=500 条模拟招聘数据，覆盖多种岗位、城市、技能组合。
输出 CSV 格式，兼容 import_data.py 的列映射规则。
"""
import csv
import random
import os

OUTPUT_PATH = os.path.join(os.path.dirname(__file__), "..", "data", "kaggle_jobs_500.csv")
TOTAL = 520

# ---- 数据素材 ----
COMPANIES = [
    ("字节跳动", "5000人以上", "互联网/IT", "民营"),
    ("阿里巴巴", "5000人以上", "互联网/IT", "上市公司"),
    ("腾讯", "5000人以上", "互联网/IT", "上市公司"),
    ("华为", "5000人以上", "通信/IT", "民营"),
    ("百度", "5000人以上", "互联网/IT", "上市公司"),
    ("京东", "5000人以上", "互联网/电商", "上市公司"),
    ("美团", "5000人以上", "互联网/O2O", "上市公司"),
    ("网易", "5000人以上", "互联网/游戏", "上市公司"),
    ("快手", "2000-5000人", "互联网/短视频", "民营"),
    ("哔哩哔哩", "2000-5000人", "互联网/视频", "上市公司"),
    ("小红书", "2000-5000人", "互联网/社交", "民营"),
    ("滴滴出行", "5000人以上", "互联网/出行", "民营"),
    ("科大讯飞", "2000-5000人", "人工智能", "上市公司"),
    ("商汤科技", "1000-2000人", "人工智能", "民营"),
    ("旷视科技", "500-1000人", "人工智能", "民营"),
    ("寒武纪", "500-1000人", "人工智能/芯片", "上市公司"),
    ("地平线", "500-1000人", "人工智能/芯片", "民营"),
    ("Momenta", "200-500人", "人工智能/自动驾驶", "外商独资"),
    ("字节跳动(成都)", "1000-3000人", "互联网/IT", "民营"),
    ("蚂蚁集团", "5000人以上", "互联网金融", "民营"),
    ("拼多多", "5000人以上", "互联网/电商", "上市公司"),
    ("SHEIN", "5000人以上", "互联网/电商", "民营"),
    ("米哈游", "2000-5000人", "游戏/互联网", "民营"),
    ("莉莉丝", "1000-2000人", "游戏/互联网", "民营"),
    ("鹰角网络", "500-1000人", "游戏/互联网", "民营"),
    ("大疆创新", "5000人以上", "硬件/无人机", "民营"),
    ("比亚迪", "5000人以上", "新能源汽车", "上市公司"),
    ("蔚来汽车", "5000人以上", "新能源汽车", "上市公司"),
    ("理想汽车", "2000-5000人", "新能源汽车", "上市公司"),
    ("小鹏汽车", "2000-5000人", "新能源汽车", "上市公司"),
    ("小米集团", "5000人以上", "消费电子/互联网", "上市公司"),
    ("OPPO", "5000人以上", "消费电子", "民营"),
    ("vivo", "5000人以上", "消费电子", "民营"),
    ("荣耀", "2000-5000人", "消费电子", "民营"),
    ("中兴通讯", "5000人以上", "通信/IT", "上市公司"),
    ("海康威视", "5000人以上", "安防/AI", "上市公司"),
    ("大华股份", "5000人以上", "安防/AI", "上市公司"),
    ("深信服", "2000-5000人", "网络安全", "上市公司"),
    ("奇安信", "2000-5000人", "网络安全", "上市公司"),
    ("中科创达", "2000-5000人", "软件外包/IoT", "上市公司"),
]

CITIES = [
    ("北京", "北京", "一线"),
    ("上海", "上海", "一线"),
    ("广州", "广东", "一线"),
    ("深圳", "广东", "一线"),
    ("杭州", "浙江", "新一线"),
    ("成都", "四川", "新一线"),
    ("武汉", "湖北", "新一线"),
    ("南京", "江苏", "新一线"),
    ("西安", "陕西", "新一线"),
    ("重庆", "重庆", "新一线"),
    ("长沙", "湖南", "新一线"),
    ("天津", "天津", "新一线"),
    ("苏州", "江苏", "新一线"),
    ("合肥", "安徽", "二线"),
    ("郑州", "河南", "二线"),
    ("厦门", "福建", "二线"),
    ("青岛", "山东", "二线"),
    ("大连", "辽宁", "二线"),
    ("宁波", "浙江", "二线"),
    ("济南", "山东", "二线"),
]

JOBS = [
    # (岗位名, 学历分布权重, 经验分布权重)
    ("Java开发工程师", ["本科","本科","本科","大专","硕士"], ["1-3年","3-5年","3-5年","5-10年","应届"]),
    ("Python开发工程师", ["本科","本科","硕士","大专","博士"], ["1-3年","3-5年","5-10年","应届","应届"]),
    ("前端开发工程师", ["本科","大专","本科","本科","硕士"], ["1-3年","3-5年","1-3年","5-10年","应届"]),
    ("Go后端开发工程师", ["本科","本科","本科","大专","硕士"], ["1-3年","3-5年","3-5年","5-10年","应届"]),
    ("C++开发工程师", ["本科","硕士","本科","本科","博士"], ["3-5年","5-10年","1-3年","5-10年","应届"]),
    ("Android开发工程师", ["本科","大专","本科","本科","硕士"], ["1-3年","3-5年","1-3年","5-10年","应届"]),
    ("iOS开发工程师", ["本科","本科","大专","本科","硕士"], ["1-3年","3-5年","3-5年","5-10年","应届"]),
    ("数据分析师", ["本科","本科","硕士","大专","本科"], ["1-3年","3-5年","1-3年","5-10年","应届"]),
    ("大数据开发工程师", ["本科","本科","硕士","本科","大专"], ["1-3年","3-5年","3-5年","5-10年","1-3年"]),
    ("算法工程师(NLP/CV)", ["硕士","硕士","硕士","博士","本科"], ["1-3年","3-5年","5-10年","应届","3-5年"]),
    ("DevOps运维工程师", ["本科","大专","本科","本科","大专"], ["1-3年","3-5年","3-5年","5-10年","1-3年"]),
    ("测试开发工程师", ["本科","大专","本科","本科","大专"], ["1-3年","3-5年","1-3年","5-10年","应届"]),
    ("产品经理(技术方向)", ["本科","本科","硕士","本科","大专"], ["1-3年","3-5年","3-5年","5-10年","1-3年"]),
    ("UI/UX设计师", ["本科","本科","大专","本科","硕士"], ["1-3年","3-5年","1-3年","5-10年","应届"]),
    ("网络安全工程师", ["本科","本科","大专","本科","硕士"], ["1-3年","3-5年","1-3年","5-10年","应届"]),
    ("区块链开发工程师", ["本科","硕士","本科","本科","博士"], ["1-3年","3-5年","3-5年","5-10年","应届"]),
    ("嵌入式软件开发", ["本科","本科","大专","本科","硕士"], ["1-3年","3-5年","1-3年","5-10年","应届"]),
    ("数据仓库工程师", ["本科","本科","本科","大专","硕士"], ["1-3年","3-5年","1-3年","5-10年","3-5年"]),
    ("运维开发工程师", ["本科","大专","本科","本科","大专"], ["1-3年","3-5年","1-3年","5-10年","应届"]),
    ("推荐系统工程师", ["硕士","硕士","本科","博士","硕士"], ["1-3年","3-5年","3-5年","5-10年","1-3年"]),
]

SKILL_POOL = {
    "Java开发工程师": ["Java","Spring Boot","MyBatis","MySQL","Redis","Docker","Kafka","Spring Cloud","Linux","Git"],
    "Python开发工程师": ["Python","Django","Flask","MySQL","MongoDB","Redis","Docker","Linux","FastAPI","Celery"],
    "前端开发工程师": ["JavaScript","TypeScript","Vue","React","CSS","HTML","Webpack","Vite","Node.js","ECharts"],
    "Go后端开发工程师": ["Go","Gin","gRPC","MySQL","Redis","Docker","Kubernetes","Linux","Kafka","PostgreSQL"],
    "C++开发工程师": ["C++","STL","Boost","Linux","Git","CMake","Qt","TCP/IP","多线程","Docker"],
    "Android开发工程师": ["Android","Java","Kotlin","Retrofit","Jetpack","Git","MVP/MVVM","SQLite","Gradle","Flutter"],
    "iOS开发工程师": ["iOS","Swift","Objective-C","UIKit","SwiftUI","Xcode","CoreData","Git","CocoaPods","Combine"],
    "数据分析师": ["Python","SQL","Pandas","NumPy","Tableau","Excel","Matplotlib","Seaborn","Spark SQL","统计学"],
    "大数据开发工程师": ["Hadoop","Spark","Hive","Kafka","Flink","HDFS","HBase","ClickHouse","Java","Scala"],
    "算法工程师(NLP/CV)": ["Python","TensorFlow","PyTorch","NLP","OpenCV","Scikit-learn","Transformer","深度学习","BERT","模型部署"],
    "DevOps运维工程师": ["Linux","Docker","Kubernetes","Jenkins","Ansible","Terraform","Nginx","Shell","Prometheus","Grafana"],
    "测试开发工程师": ["Python","Java","Selenium","JMeter","Appium","Postman","Git","Jenkins","Pytest","自动化测试"],
    "产品经理(技术方向)": ["需求分析","Axure","数据驱动","竞品分析","敏捷开发","SQL","Jira","PRD","项目管理","用户研究"],
    "UI/UX设计师": ["Figma","Sketch","Adobe XD","Photoshop","Illustrator","用户体验","交互设计","After Effects","C4D","UI设计"],
    "网络安全工程师": ["Wireshark","Kali Linux","IDS/IPS","Burp Suite","防火墙","SIEM","Python","渗透测试","OWASP","密码学"],
    "区块链开发工程师": ["Solidity","Go","Ethereum","智能合约","Rust","DeFi","Hyperledger","共识算法","Node.js","Web3.js"],
    "嵌入式软件开发": ["C","C++","RTOS","ARM","Linux","FreeRTOS","MCU","I2C/SPI","PCB","嵌入式调试"],
    "数据仓库工程师": ["SQL","Hive","Spark SQL","Flink","数据建模","ETL","ClickHouse","Hadoop","Python","数据治理"],
    "运维开发工程师": ["Python","Go","Docker","Kubernetes","Ansible","Terraform","Nginx","Shell","GitOps","Prometheus"],
    "推荐系统工程师": ["Python","TensorFlow","PyTorch","Spark","推荐算法","Jaccard","协同过滤","DNN","召回","排序"],
}

BENEFITS = [
    ["五险一金","年终奖"],
    ["五险一金","年终奖","弹性工作"],
    ["五险一金","年终奖","股票期权"],
    ["五险一金","年终奖","弹性工作","股票期权"],
    ["五险一金","年终奖","补充商业保险"],
    ["五险一金","年终奖","带薪年假","弹性工作"],
    ["五险一金","年终奖","股票期权","带薪年假"],
    ["五险一金","年终奖","弹性工作","餐补"],
    ["五险一金","年终奖","股票期权","弹性工作","餐补","交通补贴"],
    ["五险一金","年终奖","带薪年假","股票期权","补充商业保险"],
]

SALARY_RANGES = [
    (6, 12), (8, 15), (10, 18), (12, 22), (10, 20),
    (15, 25), (15, 30), (18, 35), (20, 35), (20, 40),
    (8, 14), (9, 16), (12, 20), (14, 24), (16, 28),
    (25, 45), (25, 50), (30, 55), (35, 60), (40, 70),
]


def generate():
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    random.seed(42)

    rows = []
    for i in range(TOTAL):
        company_name, company_size, industry, company_type = random.choice(COMPANIES)
        job_title, edu_weights, exp_weights = random.choice(JOBS)
        city, province, tier = random.choice(CITIES)
        education = random.choice(edu_weights)
        experience = random.choice(exp_weights)
        salary = random.choice(SALARY_RANGES)
        skills = random.sample(SKILL_POOL[job_title], k=random.randint(3, 6))
        benefits = random.choice(BENEFITS)
        publish_date = f"2026-{random.randint(4,7):02d}-{random.randint(1,28):02d}"

        rows.append({
            "jobId": f"SYN-{i+1:04d}",
            "title": job_title,
            "companyName": company_name,
            "companySize": company_size,
            "industry": industry,
            "companyType": company_type,
            "salaryMin": salary[0],
            "salaryMax": salary[1],
            "city": city,
            "province": province,
            "cityTier": tier,
            "education": education,
            "experience": experience,
            "skills": "|".join(skills),
            "welfare": "|".join(benefits),
            "publishDate": publish_date,
            "sourceUrl": f"https://synthetic.dataset/{i+1}",
        })

    # 写入 CSV
    fieldnames = list(rows[0].keys())
    with open(OUTPUT_PATH, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    # 统计
    titles = set(r["title"] for r in rows)
    cities = set(r["city"] for r in rows)
    print(f"生成 {len(rows)} 条模拟数据 -> {OUTPUT_PATH}")
    print(f"  岗位种类: {len(titles)}")
    print(f"  城市覆盖: {len(cities)}")
    print(f"  示例: {rows[0]['title']} @ {rows[0]['companyName']} {rows[0]['city']} {rows[0]['salaryMin']}K-{rows[0]['salaryMax']}K")


if __name__ == "__main__":
    generate()
