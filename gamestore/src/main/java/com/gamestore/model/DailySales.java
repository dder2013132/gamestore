package com.gamestore.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record DailySales(LocalDate date, long orderCount, long amount, int percent) {
    public LocalDate getDate() { return date; }
    public long getOrderCount() { return orderCount; }
    public long getAmount() { return amount; }
    public int getPercent() { return percent; }
    public String getDateLabel() { return date.format(DateTimeFormatter.ofPattern("MM/dd")); }
}
