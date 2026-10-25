package com.gamestore.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Optional catalog information, independent of historical order prices. */
public record GameDetails(String developer, LocalDate releaseDate, String supportedLanguages,
        List<String> screenshots, String galleryNote) {
    public static final int MAX_SCREENSHOTS = 6;
    public static final GameDetails EMPTY = new GameDetails("", null, "", List.of(), "");
    public GameDetails {
        developer = developer == null ? "" : developer.strip();
        supportedLanguages = supportedLanguages == null ? "" : supportedLanguages.strip();
        galleryNote = galleryNote == null ? "" : galleryNote.strip();
        screenshots = List.copyOf(screenshots);
        if (developer.length() > 100 || supportedLanguages.length() > 200 || galleryNote.length() > 200
                || (releaseDate != null && (releaseDate.getYear() < 1000 || releaseDate.getYear() > 9999))
                || screenshots.size() > MAX_SCREENSHOTS || screenshots.stream().distinct().count() != screenshots.size()
                || screenshots.stream().anyMatch(path -> !validScreenshotPath(path))) {
            throw new IllegalArgumentException("Invalid game details");
        }
    }
    public static boolean validScreenshotPath(String path) {
        return path != null && path.length() <= 300
                && path.matches("images/screenshots/[a-z0-9][a-z0-9_.-]*\\.(png|jpg|jpeg|webp)");
    }
    public String getDeveloper() { return developer; }
    public LocalDate getReleaseDate() { return releaseDate; }
    public String getSupportedLanguages() { return supportedLanguages; }
    public List<String> getScreenshots() { return screenshots; }
    public String getGalleryNote() { return galleryNote; }
    public String getFormattedReleaseDate() { return releaseDate == null ? "" : releaseDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")); }
}
