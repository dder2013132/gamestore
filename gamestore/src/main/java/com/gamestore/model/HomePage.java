package com.gamestore.model;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;

public record HomePage(List<HomeGame> newGames, List<HomeGame> popularGames, List<HomeGame> topRatedGames, int catalogCount) {
    public HomePage {
        newGames = List.copyOf(newGames); popularGames = List.copyOf(popularGames); topRatedGames = List.copyOf(topRatedGames);
    }
    public List<HomeGame> getNewGames() { return newGames; }
    public List<HomeGame> getPopularGames() { return popularGames; }
    public List<HomeGame> getTopRatedGames() { return topRatedGames; }
    public int getCatalogCount() { return catalogCount; }
    public Game getSpotlight() { return newGames.isEmpty() ? null : newGames.get(0).game(); }
    public List<HomeSlide> getHeroSlides() {
        var entries = new LinkedHashMap<String, HomeGame>();
        Set<String> newest = new HashSet<>(), popular = new HashSet<>();
        // Interleave the top three from each shelf, retaining one slide per game.
        for (int i = 0; i < 3; i++) {
            if (i < newGames.size()) {
                HomeGame entry = newGames.get(i);
                newest.add(entry.game().getId()); entries.putIfAbsent(entry.game().getId(), entry);
            }
            if (i < popularGames.size()) {
                HomeGame entry = popularGames.get(i);
                popular.add(entry.game().getId()); entries.putIfAbsent(entry.game().getId(), entry);
            }
        }
        return entries.values().stream().map(entry -> new HomeSlide(entry.game(),
                newest.contains(entry.game().getId()), popular.contains(entry.game().getId()), entry.buyerCount())).toList();
    }
}
