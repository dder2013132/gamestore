package com.gamestore.model;

import java.util.List;

public record CatalogPage(List<Game> games, List<String> genres, int catalogCount, int resultCount,
        int page, int pageSize) {
    public CatalogPage {
        games = List.copyOf(games);
        genres = List.copyOf(genres);
    }
    public int pageCount() { return Math.max(1, Math.ceilDiv(resultCount, pageSize)); }
    public int firstResult() { return resultCount == 0 ? 0 : (page - 1) * pageSize + 1; }
    public int lastResult() { return resultCount == 0 ? 0 : firstResult() + games.size() - 1; }
}
