package com.nosota.mwallet.dto;

import java.util.List;

public class PagedResponse<T> {

    private List<T> data;
    private int pageNumber;
    private int pageSize;
    private long totalRecords;
    private int totalPages;

    // getters and setters

    public PagedResponse(List<T> data, int pageNumber, int pageSize, long totalRecords) {
        this.data = data;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalRecords = totalRecords;
        this.totalPages = (int) Math.ceil((double) totalRecords / pageSize);
    }
}
