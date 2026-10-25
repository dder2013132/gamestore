package com.gamestore.model;

/** A public catalog feature; membership and price come from the same home snapshot. */
public record HomeSlide(Game game, boolean newArrival, boolean popular, long buyerCount) {
    public Game getGame() { return game; }
    public boolean isNewArrival() { return newArrival; }
    public boolean isPopular() { return popular; }
    public long getBuyerCount() { return buyerCount; }
    public String getLabel() { return newArrival && popular ? "新着 & 人気" : popular ? "人気のゲーム" : "新着ゲーム"; }
}
