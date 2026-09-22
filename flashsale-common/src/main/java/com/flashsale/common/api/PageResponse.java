package com.flashsale.common.api;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int pageSize, long total) {
    public PageResponse {
        items = List.copyOf(items);
        if (page < 1 || pageSize < 1 || total < 0) {
            throw new IllegalArgumentException("invalid page response");
        }
    }
}
