package com.gamestore.model;

/** Public aggregate statistics only; never exposes a purchaser or reviewer identity. */
public record HomeGame(Game game, long buyerCount, long reviewCount, double averageRating) {
    public Game getGame() { return game; }
    public long getBuyerCount() { return buyerCount; }
    public long getReviewCount() { return reviewCount; }
    public double getAverageRating() { return averageRating; }
}
