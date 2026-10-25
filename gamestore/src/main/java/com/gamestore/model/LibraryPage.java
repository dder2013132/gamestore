package com.gamestore.model;

import java.util.List;

public record LibraryPage(List<LibraryGame> games, int totalCount, int resultCount, int page, int pageSize) {
    public LibraryPage { games = List.copyOf(games); }
    public List<LibraryGame> getGames() { return games; }
    public int getTotalCount() { return totalCount; }
    public int getResultCount() { return resultCount; }
    public int getPage() { return page; }
    public int getPageCount() { return Math.max(1, Math.ceilDiv(resultCount, pageSize)); }
    public int getFirstResult() { return resultCount == 0 ? 0 : (page - 1) * pageSize + 1; }
    public int getLastResult() { return resultCount == 0 ? 0 : getFirstResult() + games.size() - 1; }
    public int getPageStart() { return Math.max(1, page - 2); }
    public int getPageEnd() { return Math.min(getPageCount(), page + 2); }
}
