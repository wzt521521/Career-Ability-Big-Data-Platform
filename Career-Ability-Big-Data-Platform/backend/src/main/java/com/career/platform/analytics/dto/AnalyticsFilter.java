package com.career.platform.analytics.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class AnalyticsFilter {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
    private String city;
    private String position;
    private String industry;

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
}
