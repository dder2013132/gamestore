package com.gamestore.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record LibraryGame(Game game, LocalDateTime acquiredAt, Integer rating) {
    public Game getGame() { return game; }
    public String getFormattedAcquiredAt() { return acquiredAt.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")); }
    public boolean isReviewed() { return rating != null; }
    public Integer getRating() { return rating; }
}
