package com.gamestore.model;

import java.util.List;

public record AdminDashboard(DashboardSummary summary, List<DailySales> days, List<GameSales> games,
        int gameCount, int page, int pageSize) {
    public AdminDashboard { days = List.copyOf(days); games = List.copyOf(games); }
    public DashboardSummary getSummary() { return summary; }
    public List<DailySales> getDays() { return days; }
    public List<GameSales> getGames() { return games; }
    public int getGameCount() { return gameCount; }
    public int getPage() { return page; }
    public int getPageCount() { return Math.max(1, Math.ceilDiv(gameCount, pageSize)); }
    public int getFirstResult() { return gameCount == 0 ? 0 : (page - 1) * pageSize + 1; }
    public int getLastResult() { return gameCount == 0 ? 0 : getFirstResult() + games.size() - 1; }
    public int getPageStart() { return Math.max(1, page - 2); }
    public int getPageEnd() { return Math.min(getPageCount(), page + 2); }
    public long getPeakAmount() { return days.stream().mapToLong(DailySales::amount).max().orElse(0); }
    public String getChartFrom() { return days.isEmpty() ? "" : days.get(0).date().toString(); }
    public String getChartTo() { return days.isEmpty() ? "" : days.get(days.size() - 1).date().toString(); }
}
