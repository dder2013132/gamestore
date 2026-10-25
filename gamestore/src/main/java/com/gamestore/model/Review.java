package com.gamestore.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record Review(long id, long userId, int gameId, String username, String gameTitle, String imagePath,
        int rating, String body, long version, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static final int BODY_LIMIT = 2000;
    public long getId() { return id; }
    public long getUserId() { return userId; }
    public int getGameId() { return gameId; }
    public String getUsername() { return username; }
    public String getGameTitle() { return gameTitle; }
    public String getImagePath() { return imagePath; }
    public int getRating() { return rating; }
    public String getBody() { return body; }
    public long getVersion() { return version; }
    public boolean isEdited() { return version > 0; }
    public String getStars() { return "★".repeat(rating) + "☆".repeat(5 - rating); }
    public String getFormattedCreatedAt() { return format(createdAt); }
    public String getFormattedUpdatedAt() { return format(updatedAt); }
    private static String format(LocalDateTime time) { return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")); }

    public static String validate(int rating, String body) {
        if (rating < 1 || rating > 5) { throw new IllegalArgumentException("評価は1〜5から選んでください。"); }
        if (body == null || body.strip().isEmpty()) { throw new IllegalArgumentException("レビューの内容を入力してください。"); }
        String clean = body.strip();
        if (clean.length() > BODY_LIMIT) { throw new IllegalArgumentException("レビューは2,000文字以内で入力してください。"); }
        return clean;
    }
}
