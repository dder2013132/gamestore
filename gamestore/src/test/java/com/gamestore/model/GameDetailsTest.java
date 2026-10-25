package com.gamestore.model;

import static org.junit.Assert.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.Test;

public class GameDetailsTest {
    @Test public void galleryIsImmutableAndOptionalFieldsCanBeEmpty() {
        var paths = new ArrayList<>(List.of("images/screenshots/a.png")); var details = new GameDetails(null, null, null, paths, null);
        paths.clear(); assertEquals(1, details.screenshots().size()); assertEquals("", details.getFormattedReleaseDate());
        try { details.screenshots().clear(); fail("Immutable gallery"); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void unsafePathsDuplicatesAndMoreThanSixImagesAreRejected() {
        for (List<String> paths : List.of(List.of("https://example.com/a.png"), List.of("images/screenshots/../a.png"),
                List.of("images/screenshots/a.png?x=1"), List.of("images/a.png"), List.of("images/screenshots/a.svg"),
                List.of("images/screenshots/a.png", "images/screenshots/a.png"),
                java.util.stream.IntStream.rangeClosed(1, 7).mapToObj(i -> "images/screenshots/" + i + ".png").toList())) {
            try { new GameDetails("", null, "", paths, ""); fail("Invalid gallery accepted"); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void invalidMetadataCannotReachRepositoryWrite() {
        for (GameDetailsFactory factory : List.<GameDetailsFactory>of(
                () -> new GameDetails("x".repeat(101), null, "", List.of(), ""),
                () -> new GameDetails("", LocalDate.of(999, 1, 1), "", List.of(), ""),
                () -> new GameDetails("", null, "x".repeat(201), List.of(), ""),
                () -> new GameDetails("", null, "", List.of(), "x".repeat(201)))) {
            try { factory.make(); fail("Invalid metadata accepted"); } catch (IllegalArgumentException expected) { }
        }
    }
    @FunctionalInterface interface GameDetailsFactory { GameDetails make(); }
}
