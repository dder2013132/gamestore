package com.gamestore.model;

public record DashboardSummary(long completedCount, long completedAmount, long soldUnits, long buyerCount,
        long pendingCount, long pendingAmount, long cancelledCount, long cancelledAmount) {
    public long getCompletedCount() { return completedCount; }
    public long getCompletedAmount() { return completedAmount; }
    public long getSoldUnits() { return soldUnits; }
    public long getBuyerCount() { return buyerCount; }
    public long getPendingCount() { return pendingCount; }
    public long getPendingAmount() { return pendingAmount; }
    public long getCancelledCount() { return cancelledCount; }
    public long getCancelledAmount() { return cancelledAmount; }
}
