package com.career.platform.collect.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * Bounded pagination shared by collection management read endpoints.
 */
public class CollectPageRequest {

    public static final int MAX_SIZE = 100;
    // Preserve the former list endpoint behavior as closely as possible while bounding every request.
    public static final int DEFAULT_SIZE = MAX_SIZE;

    @Min(value = 1, message = "页码必须大于等于 1")
    private int page = 1;

    @Min(value = 1, message = "每页条数必须大于等于 1")
    @Max(value = MAX_SIZE, message = "每页条数不能超过 100")
    private int size = DEFAULT_SIZE;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
