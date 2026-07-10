package com.career.platform.analytics.service;

import com.career.platform.analytics.dto.AnalyticsFilter;
import com.career.platform.position.entity.JobCompany;
import com.career.platform.position.entity.JobPosition;
import com.career.platform.position.repository.PositionRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {
    private final PositionRepository positionRepository;

    public AnalyticsService(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    @Cacheable("stat-overview")
    public Map<String, Object> overview() {
        return overview(positions());
    }

    public Map<String, Object> overviewFor(AnalyticsFilter filter) {
        return overview(filteredPositions(filter));
    }

    private Map<String, Object> overview(List<JobPosition> positions) {
        LocalDate firstDay = LocalDate.now().withDayOfMonth(1);
        long newThisMonth = positions.stream()
                .filter(position -> position.getPublishDate() != null && !position.getPublishDate().isBefore(firstDay))
                .count();
        long activeCompanies = positions.stream().map(JobPosition::getCompany).filter(Objects::nonNull)
                .map(company -> company.getId() == null ? company.getCompanyName() : company.getId())
                .filter(Objects::nonNull).distinct().count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalPositions", positions.size());
        result.put("newThisMonth", newThisMonth);
        result.put("averageSalary", averageSalary(positions));
        result.put("activeCompanies", activeCompanies);
        result.put("updatedAt", java.time.LocalDateTime.now());
        return result;
    }

    @Cacheable("stat-position")
    public Map<String, Object> positionsAnalysis() {
        return positionsAnalysis(positions());
    }

    public Map<String, Object> positionsAnalysisFor(AnalyticsFilter filter) {
        return positionsAnalysis(filteredPositions(filter));
    }

    private Map<String, Object> positionsAnalysis(List<JobPosition> positions) {
        List<Map<String, Object>> categories = rankedCounts(positions, JobPosition::getTitle, 20);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", positions.size());
        result.put("categories", categories);
        result.put("hotPositions", categories);
        result.put("monthlyGrowthRate", monthlyGrowth(positions));
        return result;
    }

    @Cacheable("stat-salary")
    public Map<String, Object> salary() {
        return salary(positions());
    }

    public Map<String, Object> salaryForTitle(String title) {
        return salary(positionsForTitle(title));
    }

    public Map<String, Object> salaryFor(AnalyticsFilter filter) { return salary(filteredPositions(filter)); }

    private Map<String, Object> salary(List<JobPosition> positions) {
        List<Double> salaries = positions.stream().map(this::midSalary).filter(Objects::nonNull).sorted().collect(Collectors.toList());
        List<Map<String, Object>> distribution = List.of(
                bucket("0-5K", salaries, 0, 5),
                bucket("5-10K", salaries, 5, 10),
                bucket("10-15K", salaries, 10, 15),
                bucket("15-20K", salaries, 15, 20),
                bucket("20-30K", salaries, 20, 30),
                bucket("30K以上", salaries, 30, Double.MAX_VALUE)
        );
        List<Map<String, Object>> top = positions.stream().filter(position -> midSalary(position) != null)
                .sorted(Comparator.comparing(this::midSalary).reversed()).limit(20)
                .map(position -> item(position.getTitle(), midSalary(position), "salary"))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("average", averageSalary(positions));
        result.put("median", median(salaries));
        result.put("distribution", distribution);
        result.put("topPositions", top);
        result.put("cityComparison", groupAverage(positions, JobPosition::getCity, 15));
        return result;
    }

    @Cacheable("stat-skills")
    public Map<String, Object> skills() {
        return skills(positions());
    }

    public Map<String, Object> skillsForTitle(String title) {
        return skills(positionsForTitle(title));
    }

    public Map<String, Object> skillsFor(AnalyticsFilter filter) { return skills(filteredPositions(filter)); }

    private Map<String, Object> skills(List<JobPosition> positions) {
        Map<String, Long> counts = new HashMap<>();
        Map<String, Long> combinations = new HashMap<>();
        for (JobPosition position : positions) {
            List<String> unique = position.getSkills() == null ? List.of() : position.getSkills().stream()
                    .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty())
                    .distinct().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
            unique.forEach(skill -> counts.merge(skill, 1L, Long::sum));
            for (int first = 0; first < unique.size(); first++) {
                for (int second = first + 1; second < unique.size(); second++) {
                    combinations.merge(unique.get(first) + " + " + unique.get(second), 1L, Long::sum);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topSkills", ranked(counts, 30));
        result.put("associations", ranked(combinations, 15));
        result.put("totalTaggedPositions", positions.stream().filter(p -> p.getSkills() != null && !p.getSkills().isEmpty()).count());
        return result;
    }

    @Cacheable("stat-education")
    public Map<String, Object> education() {
        return education(positions());
    }

    public Map<String, Object> educationForTitle(String title) {
        return education(positionsForTitle(title));
    }

    public Map<String, Object> educationFor(AnalyticsFilter filter) { return education(filteredPositions(filter)); }

    private Map<String, Object> education(List<JobPosition> positions) {
        Map<String, Long> counts = countBy(positions, position -> defaultValue(position.getEducation(), "不限"));
        List<Map<String, Object>> salaryByEducation = groupAverage(positions,
                position -> defaultValue(position.getEducation(), "不限"), 20);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("distribution", ranked(counts, 20));
        result.put("salaryComparison", salaryByEducation);
        return result;
    }

    @Cacheable("stat-city")
    public Map<String, Object> city() {
        return city(positions());
    }

    public Map<String, Object> cityForTitle(String title) {
        return city(positionsForTitle(title));
    }

    public Map<String, Object> cityFor(AnalyticsFilter filter) { return city(filteredPositions(filter)); }

    private Map<String, Object> city(List<JobPosition> positions) {
        Map<String, Long> counts = countBy(positions, position -> defaultValue(position.getCity(), "未知"));
        Map<String, String> provinceByCity = positions.stream().filter(position -> position.getCity() != null)
                .collect(Collectors.toMap(JobPosition::getCity,
                        position -> defaultValue(position.getProvince(), "未知"), (first, second) -> first));
        List<Map<String, Object>> rankings = ranked(counts, 30);
        rankings.forEach(item -> item.put("province", provinceByCity.getOrDefault(item.get("name"), "未知")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ranking", rankings);
        result.put("salaryComparison", groupAverage(positions, JobPosition::getCity, 20));
        result.put("heatmap", rankings);
        return result;
    }

    @Cacheable("stat-company")
    public Map<String, Object> company() {
        return company(positions());
    }

    public Map<String, Object> companyFor(AnalyticsFilter filter) { return company(filteredPositions(filter)); }

    private Map<String, Object> company(List<JobPosition> positions) {
        Map<String, Long> industry = countBy(positions, position -> position.getCompany() == null
                ? "未知" : defaultValue(position.getCompany().getIndustry(), "未知"));
        Map<String, Long> sizes = countBy(positions, position -> position.getCompany() == null
                ? "未知" : defaultValue(position.getCompany().getCompanySize(), "未知"));
        Map<String, Long> active = countBy(positions, position -> position.getCompany() == null
                ? "未知" : defaultValue(position.getCompany().getCompanyName(), "未知"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("industryDistribution", ranked(industry, 20));
        Map<String, Set<Object>> companyIdsByIndustry = new HashMap<>();
        for (JobPosition position : positions) {
            JobCompany value = position.getCompany();
            if (value == null) continue;
            String industryName = defaultValue(value.getIndustry(), "未知");
            Object companyKey = value.getId() == null ? value.getCompanyName() : value.getId();
            if (companyKey != null) companyIdsByIndustry.computeIfAbsent(industryName, key -> new HashSet<>()).add(companyKey);
        }
        result.put("industryCompanyCounts", ranked(companyIdsByIndustry.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> (long) entry.getValue().size())), 20));
        result.put("sizeDistribution", ranked(sizes, 20));
        result.put("activeCompanies", ranked(active, 20));
        return result;
    }

    @Cacheable("stat-trends")
    public Map<String, Object> trends() {
        return trends(positions());
    }

    public Map<String, Object> trendsFor(AnalyticsFilter filter) { return trends(filteredPositions(filter)); }

    private Map<String, Object> trends(List<JobPosition> positions) {
        LocalDate today = LocalDate.now();
        Map<LocalDate, Long> dailyCounts = positions.stream().filter(position -> position.getPublishDate() != null)
                .collect(Collectors.groupingBy(JobPosition::getPublishDate, Collectors.counting()));
        List<Map<String, Object>> daily = new ArrayList<>();
        for (int daysAgo = 29; daysAgo >= 0; daysAgo--) {
            LocalDate date = today.minusDays(daysAgo);
            daily.add(item(date.toString(), dailyCounts.getOrDefault(date, 0L), "value"));
        }

        Map<YearMonth, Long> monthlyCounts = positions.stream().filter(position -> position.getPublishDate() != null)
                .collect(Collectors.groupingBy(position -> YearMonth.from(position.getPublishDate()), Collectors.counting()));
        List<Map<String, Object>> monthly = new ArrayList<>();
        YearMonth current = YearMonth.now();
        for (int monthsAgo = 11; monthsAgo >= 0; monthsAgo--) {
            YearMonth month = current.minusMonths(monthsAgo);
            monthly.add(item(month.toString(), monthlyCounts.getOrDefault(month, 0L), "value"));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("daily", daily);
        result.put("monthly", monthly);
        result.put("monthOverMonth", monthlyGrowth(positions));
        result.put("yearOverYear", yearGrowth(positions));
        return result;
    }

    private List<JobPosition> positions() {
        return positionRepository.findAllWithCompany();
    }

    private List<JobPosition> positionsForTitle(String title) {
        if (title == null || title.isBlank()) {
            return positions();
        }
        return positionRepository.findByTitleContainingIgnoreCase(title.trim());
    }

    private List<JobPosition> filteredPositions(AnalyticsFilter filter) {
        if (filter == null) return positions();
        if (filter.getStartDate() != null && filter.getEndDate() != null
                && filter.getStartDate().isAfter(filter.getEndDate())) {
            throw new IllegalArgumentException("startDate 不能晚于 endDate");
        }
        return positions().stream()
                .filter(position -> filter.getStartDate() == null || position.getPublishDate() != null
                        && !position.getPublishDate().isBefore(filter.getStartDate()))
                .filter(position -> filter.getEndDate() == null || position.getPublishDate() != null
                        && !position.getPublishDate().isAfter(filter.getEndDate()))
                .filter(position -> !hasText(filter.getCity()) || filter.getCity().trim().equals(position.getCity()))
                .filter(position -> !hasText(filter.getPosition()) || position.getTitle() != null
                        && position.getTitle().toLowerCase(Locale.ROOT).contains(filter.getPosition().trim().toLowerCase(Locale.ROOT)))
                .filter(position -> !hasText(filter.getIndustry()) || position.getCompany() != null
                        && filter.getIndustry().trim().equals(position.getCompany().getIndustry()))
                .collect(Collectors.toList());
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    private Map<String, Long> countBy(List<JobPosition> positions, Function<JobPosition, String> classifier) {
        return positions.stream().collect(Collectors.groupingBy(classifier, Collectors.counting()));
    }

    private List<Map<String, Object>> rankedCounts(List<JobPosition> positions,
                                                    Function<JobPosition, String> classifier, int limit) {
        return ranked(countBy(positions, classifier), limit);
    }

    private List<Map<String, Object>> ranked(Map<String, Long> counts, int limit) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit).map(entry -> item(entry.getKey(), entry.getValue(), "value"))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> groupAverage(List<JobPosition> positions,
                                                    Function<JobPosition, String> classifier, int limit) {
        return positions.stream().filter(position -> classifier.apply(position) != null && midSalary(position) != null)
                .collect(Collectors.groupingBy(classifier, Collectors.averagingDouble(this::midSalary)))
                .entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(limit)
                .map(entry -> item(entry.getKey(), round(entry.getValue()), "averageSalary"))
                .collect(Collectors.toList());
    }

    private Map<String, Object> bucket(String name, List<Double> values, double min, double max) {
        long count = values.stream().filter(value -> value >= min && value < max).count();
        return item(name, count, "value");
    }

    private Double midSalary(JobPosition position) {
        Integer min = position.getSalaryMin();
        Integer max = position.getSalaryMax();
        if ((min == null || min <= 0) && (max == null || max <= 0)) {
            return null;
        }
        if (min == null || min <= 0) return max.doubleValue();
        if (max == null || max <= 0) return min.doubleValue();
        return (min + max) / 2.0;
    }

    private double averageSalary(List<JobPosition> positions) {
        return round(positions.stream().map(this::midSalary).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private double median(List<Double> sorted) {
        if (sorted.isEmpty()) return 0;
        int middle = sorted.size() / 2;
        double value = sorted.size() % 2 == 0 ? (sorted.get(middle - 1) + sorted.get(middle)) / 2 : sorted.get(middle);
        return round(value);
    }

    private double monthlyGrowth(List<JobPosition> positions) {
        YearMonth current = YearMonth.now();
        long currentCount = countMonth(positions, current);
        long previousCount = countMonth(positions, current.minusMonths(1));
        return growth(currentCount, previousCount);
    }

    private double yearGrowth(List<JobPosition> positions) {
        YearMonth current = YearMonth.now();
        return growth(countMonth(positions, current), countMonth(positions, current.minusYears(1)));
    }

    private long countMonth(List<JobPosition> positions, YearMonth month) {
        return positions.stream().filter(position -> position.getPublishDate() != null
                && YearMonth.from(position.getPublishDate()).equals(month)).count();
    }

    private double growth(long current, long previous) {
        if (previous == 0) return current == 0 ? 0 : 100;
        return round((current - previous) * 100.0 / previous);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Map<String, Object> item(String name, Object value, String valueKey) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put(valueKey, value);
        return item;
    }
}
