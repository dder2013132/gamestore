package com.gamestore.model;

import java.util.List;
import junit.framework.TestCase;

public class HomePageTest extends TestCase {
    private HomeGame game(int id, long buyers) {
        return new HomeGame(new Game(String.valueOf(id), "Game " + id, 2400, "Adventure", "images/game.png"), buyers, 0, 0);
    }
    public void testSlidesInterleaveNewAndPopularWithoutRepeatingGames() {
        var newest = game(9, 2); var second = game(8, 4); var popular = game(3, 8);
        var page = new HomePage(List.of(newest, second, game(7, 0), game(6, 0)), List.of(popular, second, newest, game(2, 1)), List.of(), 10);
        var slides = page.getHeroSlides();
        assertEquals(List.of("9", "3", "8", "7"), slides.stream().map(s -> s.game().getId()).toList());
        assertEquals("新着 & 人気", slides.get(0).getLabel());
        assertEquals("人気のゲーム", slides.get(1).getLabel()); assertEquals(8L, slides.get(1).getBuyerCount());
        assertEquals("新着ゲーム", slides.get(3).getLabel());
        assertSame(newest.game(), slides.get(0).getGame());
    }
    public void testAtMostThreeFromEachShelfAndSixSlidesInTotal() {
        var page = new HomePage(List.of(game(9, 0), game(8, 0), game(7, 0), game(6, 0)), List.of(game(1, 9), game(2, 8), game(3, 7), game(4, 6)), List.of(game(5, 0)), 9);
        assertEquals(List.of("9", "1", "8", "2", "7", "3"), page.getHeroSlides().stream().map(s -> s.game().getId()).toList());
    }
    public void testEmptySingleAndNoPurchaseCatalogsNeedNoFakePopularity() {
        assertTrue(new HomePage(List.of(), List.of(), List.of(), 0).getHeroSlides().isEmpty());
        var page = new HomePage(List.of(game(1, 0)), List.of(), List.of(), 1);
        assertEquals(1, page.getHeroSlides().size()); assertFalse(page.getHeroSlides().get(0).isPopular());
        assertTrue(page.getHeroSlides().get(0).isNewArrival());
        assertEquals("新着ゲーム", page.getHeroSlides().get(0).getLabel());
    }
}
