package com.career.platform.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class OfflineAnalysisService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OfflineAnalysisService.class);
    private final AnalyticsService analytics;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OfflineAnalysisService(AnalyticsService analytics, JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.analytics = analytics;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @CacheEvict(cacheNames = {"stat-overview", "stat-position", "stat-salary", "stat-skills",
            "stat-education", "stat-city", "stat-company", "stat-trends"}, allEntries = true,
            beforeInvocation = true)
    public void calculateAndPersist() {
        LocalDate today = LocalDate.now();
        Date statDate = Date.valueOf(today);
        Map<String, Object> overview = analytics.overview();
        Map<String, Object> positions = analytics.positionsAnalysis();
        Map<String, Object> salary = analytics.salary();
        Map<String, Object> education = analytics.education();
        Map<String, Object> skills = analytics.skills();
        Map<String, Object> city = analytics.city();
        Map<String, Object> company = analytics.company();
        Map<String, Object> trends = analytics.trends();

        deleteExisting(statDate);
        jdbc.update("INSERT INTO stat_position(stat_date, stat_type, total_count, new_count, hot_positions, growth_rate) VALUES (?, 'DAILY', ?, ?, ?, ?)",
                statDate, number(overview.get("totalPositions")).intValue(), number(overview.get("newThisMonth")).intValue(),
                json(positions.get("hotPositions")), number(positions.get("monthlyGrowthRate")));
        jdbc.update("INSERT INTO stat_salary(stat_date, stat_type, avg_salary, median_salary, salary_distribution, top_salary) VALUES (?, 'DAILY', ?, ?, ?, ?)",
                statDate, number(salary.get("average")), number(salary.get("median")),
                json(salary.get("distribution")), json(salary.get("topPositions")));

        insertEducation(statDate, list(education.get("distribution")), list(education.get("salaryComparison")));
        insertSkills(statDate, list(skills.get("topSkills")));
        insertCities(statDate, list(city.get("ranking")), list(city.get("salaryComparison")));
        insertCompanies(statDate, list(company.get("industryDistribution")),
                list(company.get("industryCompanyCounts")), company.get("activeCompanies"));

        List<Map<String, Object>> daily = list(trends.get("daily"));
        int dailyNew = daily.isEmpty() ? 0 : number(daily.get(daily.size() - 1).get("value")).intValue();
        jdbc.update("INSERT INTO stat_trend(stat_date, stat_type, daily_new, month_new, month_growth, year_growth) VALUES (?, 'DAILY', ?, ?, ?, ?)",
                statDate, dailyNew, number(overview.get("newThisMonth")).intValue(),
                number(trends.get("monthOverMonth")), number(trends.get("yearOverYear")));
        LOGGER.info("Daily analytics persisted for {}", today);
    }

    private void deleteExisting(Date date) {
        for (String table : List.of("stat_position", "stat_salary", "stat_education", "stat_skill",
                "stat_city", "stat_company", "stat_trend")) {
            jdbc.update("DELETE FROM " + table + " WHERE stat_date = ? AND stat_type = 'DAILY'", date);
        }
    }

    private void insertEducation(Date date, List<Map<String, Object>> distribution,
                                 List<Map<String, Object>> salaryComparison) {
        Map<String, Object> salaries = salaryComparison.stream().collect(java.util.stream.Collectors.toMap(
                item -> String.valueOf(item.get("name")), item -> item.get("averageSalary"), (a, b) -> a));
        distribution.forEach(item -> jdbc.update(
                "INSERT INTO stat_education(stat_date, stat_type, education, position_count, avg_salary) VALUES (?, 'DAILY', ?, ?, ?)",
                date, item.get("name"), number(item.get("value")).intValue(), number(salaries.get(item.get("name")))));
    }

    private void insertSkills(Date date, List<Map<String, Object>> values) {
        values.forEach(item -> jdbc.update(
                "INSERT INTO stat_skill(stat_date, stat_type, skill_name, frequency, trend) VALUES (?, 'DAILY', ?, ?, 'stable')",
                date, item.get("name"), number(item.get("value")).intValue()));
    }

    private void insertCities(Date date, List<Map<String, Object>> ranking,
                              List<Map<String, Object>> salaryComparison) {
        Map<String, Object> salaries = salaryComparison.stream().collect(java.util.stream.Collectors.toMap(
                item -> String.valueOf(item.get("name")), item -> item.get("averageSalary"), (a, b) -> a));
        for (int index = 0; index < ranking.size(); index++) {
            Map<String, Object> item = ranking.get(index);
            jdbc.update("INSERT INTO stat_city(stat_date, stat_type, city, province, position_count, avg_salary, rank_num) VALUES (?, 'DAILY', ?, ?, ?, ?, ?)",
                    date, item.get("name"), item.get("province"), number(item.get("value")).intValue(),
                    number(salaries.get(item.get("name"))), index + 1);
        }
    }

    private void insertCompanies(Date date, List<Map<String, Object>> industries,
                                 List<Map<String, Object>> companyCounts, Object activeRanking) {
        Map<String, Object> counts = companyCounts.stream().collect(java.util.stream.Collectors.toMap(
                item -> String.valueOf(item.get("name")), item -> item.get("value"), (a, b) -> a));
        industries.forEach(item -> jdbc.update(
                "INSERT INTO stat_company(stat_date, stat_type, industry, company_count, position_count, active_ranking) VALUES (?, 'DAILY', ?, ?, ?, ?)",
                date, item.get("name"), number(counts.get(item.get("name"))).intValue(),
                number(item.get("value")).intValue(), json(activeRanking)));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return value == null ? List.of() : (List<Map<String, Object>>) value;
    }

    private Number number(Object value) {
        return value instanceof Number ? (Number) value : 0;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化统计结果", exception);
        }
    }
}
