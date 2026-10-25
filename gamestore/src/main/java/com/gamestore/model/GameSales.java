package com.gamestore.model;

public record GameSales(Game game, long units, long buyers, long amount) {
    public Game getGame() { return game; }
    public long getUnits() { return units; }
    public long getBuyers() { return buyers; }
    public long getAmount() { return amount; }
}
