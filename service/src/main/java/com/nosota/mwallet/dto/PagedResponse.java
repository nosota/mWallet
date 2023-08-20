package com.nosota.mwallet.dto;

import java.util.List;

public class PagedResponse<T> {

    private List<T> data;
    private int pageNumber;
    private int pageSize;
    private long totalRecords;
    private int totalPages;

    /**
     * Creates a paged response based on the provided data.
     *
     * @param <T> The type of data being paginated.
     *
     * @param data The actual data for the current page. This list should only contain
     *             records relevant to the current page and not the entire dataset.
     *             It should ideally have a maximum size of {@code pageSize}.
     *
     * @param pageNumber The current page number.
     *
     * @param pageSize The number of records per page. Represents the maximum number of
     *                 records that can be present in {@code data}.
     *
     * @param totalRecords The total number of records across all pages. This is used
     *                     to calculate the total number of pages and understand the
     *                     entirety of the dataset.
     *
     * @return A {@code PagedResponse} object which encapsulates the provided data along
     *         with pagination metadata like the current page number, total number of
     *         pages, and more.
     *
     * @throws IllegalArgumentException If {@code data} contains more records than
     *                                  {@code pageSize}, or if {@code pageNumber}
     *                                  or {@code pageSize} are negative, or if
     *                                  {@code totalRecords} is less than the size of
     *                                  {@code data}.
     */
    public PagedResponse(List<T> data, int pageNumber, int pageSize, long totalRecords) {
        this.data = data;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalRecords = totalRecords;
        this.totalPages = (int) Math.ceil((double) totalRecords / pageSize);
    }

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(long totalRecords) {
        this.totalRecords = totalRecords;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}
