package com.career.platform.common;

import org.springframework.data.domain.Page;

import java.util.List;

public class PageResponse<T> {
    private final List<T> content;
    private final long totalElements;
    private final int totalPages;
    private final int number;
    private final int size;

    public PageResponse(List<T> content, long totalElements, int totalPages, int number, int size) {
        this.content = content;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.number = number;
        this.size = size;
    }

    public static <S, T> PageResponse<T> from(Page<S> page, List<T> content) {
        return new PageResponse<>(content, page.getTotalElements(), page.getTotalPages(),
                page.getNumber() + 1, page.getSize());
    }

    public List<T> getContent() { return content; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
    public int getNumber() { return number; }
    public int getSize() { return size; }
}
