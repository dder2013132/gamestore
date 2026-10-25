package com.gamestore.model;

import java.util.Locale;

public enum OrderStatus {
    PENDING("支払い待ち"), COMPLETED("注文完了"), CANCELLED("キャンセル済み");
    private final String label;
    OrderStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
    public String getCode() { return name(); }
    public String getCssClass() { return name().toLowerCase(Locale.ROOT); }
}
