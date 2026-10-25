package com.gamestore.model;

import java.util.List;

public record ReviewPage(List<Review> entries, int totalCount, double averageRating, int page, int pageSize) {
    public ReviewPage { entries = List.copyOf(entries); }
    public List<Review> getEntries() { return entries; }
    public int getTotalCount() { return totalCount; }
    public double getAverageRating() { return averageRating; }
    public int getPage() { return page; }
    public int getPageCount() { return Math.max(1, Math.ceilDiv(totalCount, pageSize)); }
    public int getFirstResult() { return totalCount == 0 ? 0 : (page - 1) * pageSize + 1; }
    public int getLastResult() { return totalCount == 0 ? 0 : getFirstResult() + entries.size() - 1; }
    public int getPageStart() { return Math.max(1, page - 2); }
    public int getPageEnd() { return Math.min(getPageCount(), page + 2); }
}
