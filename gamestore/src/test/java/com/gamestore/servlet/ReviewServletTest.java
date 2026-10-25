package com.gamestore.servlet;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import com.gamestore.model.*;
import com.gamestore.repository.*;
import com.gamestore.support.WebFixture;
import junit.framework.TestCase;

public class ReviewServletTest extends TestCase {
    private static class Games extends JdbcGameRepository {
        Game game = new Game("1", "First game", 1000, "Adventure", "images/aetherbound.png");
        @Override public Game findById(String id) { return game; }
    }
    private static class Reviews extends JdbcReviewRepository {
        boolean eligible = true; Review own; ReviewResult result = ReviewResult.SAVED; SQLException failure;
        long userId, reviewId, version; int gameId, rating, calls, requestedPage; String body;
        private void call(long uid, int gid) throws SQLException { if (failure != null) { throw failure; } calls++; userId = uid; gameId = gid; }
        @Override public Review findOwn(long uid, int gid) throws SQLException { call(uid, gid); return own; }
        @Override public boolean eligible(long uid, int gid) throws SQLException { call(uid, gid); return eligible; }
        @Override public ReviewPage findForUser(long uid, int page) throws SQLException { call(uid, 0); requestedPage = page; return new ReviewPage(List.of(), 0, 0, 1, 10); }
        @Override public ReviewResult create(long uid, int gid, int rating, String body) throws SQLException { call(uid, gid); this.rating = rating; this.body = body; return result; }
        @Override public ReviewResult update(long uid, int gid, long id, long version, int rating, String body) throws SQLException {
            call(uid, gid); reviewId = id; this.version = version; this.rating = rating; this.body = body; return result;
        }
        @Override public ReviewResult delete(long uid, int gid, long id, long version) throws SQLException { call(uid, gid); reviewId = id; this.version = version; return result; }
    }
    private WebFixture web; private Games games; private Reviews reviews; private ReviewServlet servlet;
    @Override protected void setUp() throws Exception {
        web = new WebFixture(); web.path = "/reviews/edit"; web.sessionExists = true;
        web.sessionValues.put("loginUser", new User(7L, "alice")); web.parameters.put("gameId", "1");
        games = new Games(); reviews = new Reviews(); servlet = new ReviewServlet(reviews, games); servlet.init(web.config);
    }
    private void form(String path) {
        web.path = path; web.method = "POST"; web.parameters.put("rating", "5"); web.parameters.put("body", "  日本語の感想\nIt's fun!  ");
        web.parameters.put("reviewId", "8"); web.parameters.put("version", "2"); web.parameters.put("userId", "999");
    }
    public void testEditorLoadsOnlyOwnReviewAndBlocksIneligibleMember() throws Exception {
        reviews.own = new Review(8L, 7L, 1, "alice", "First game", "images/aetherbound.png", 4, "own content", 2L, LocalDateTime.now(), LocalDateTime.now());
        web.parameters.put("userId", "999"); servlet.doGet(web.request, web.response);
        assertEquals(7L, reviews.userId); assertEquals("update", web.attributes.get("reviewMode")); assertEquals("own content", web.attributes.get("formBody"));
        setUp(); reviews.eligible = false; servlet.doGet(web.request, web.response);
        assertEquals(403, web.status); assertEquals(false, web.attributes.get("canReview"));
    }
    public void testCreateUsesSessionAuthorAndRedirectsToGameReviews() throws Exception {
        form("/reviews/create"); servlet.doPost(web.request, web.response);
        assertEquals(7L, reviews.userId); assertEquals(1, reviews.gameId); assertEquals(5, reviews.rating);
        assertEquals("日本語の感想\nIt's fun!", reviews.body); assertEquals("/gamestore/games?id=1#reviews", web.redirect);
    }
    public void testUpdateAndDeleteUseIdentityAndVersionForCurrentMember() throws Exception {
        for (String path : new String[] {"/reviews/update", "/reviews/delete"}) {
            setUp(); form(path); servlet.doPost(web.request, web.response);
            assertEquals(7L, reviews.userId); assertEquals(8L, reviews.reviewId); assertEquals(2L, reviews.version);
            assertEquals(path.endsWith("delete") ? "/gamestore/account/reviews?deleted=1" : "/gamestore/games?id=1#reviews", web.redirect);
        }
    }
    public void testInvalidInputDoesNotWriteAndRetainsTextWithoutNumericCoercion() throws Exception {
        for (String[] value : new String[][] {{"rating", "0"}, {"rating", "6"}, {"rating", "<script>"}, {"rating", null},
                {"body", null}, {"body", " \n\t "}, {"body", "x".repeat(2001)}}) {
            setUp(); form("/reviews/create"); web.parameters.put(value[0], value[1]); servlet.doPost(web.request, web.response);
            assertEquals(400, web.status); assertEquals(0, reviews.calls); assertNotNull(web.attributes.get("reviewError"));
            assertEquals("/WEB-INF/views/review-edit.jsp", web.forwarded);
            if ("<script>".equals(value[1])) { assertEquals(0, web.attributes.get("formRating")); }
        }
    }
    public void testBadReviewIdentityAndVersionNeverMutate() throws Exception {
        for (String[] value : new String[][] {{"reviewId", "0"}, {"reviewId", "-1"}, {"reviewId", "9223372036854775808"},
                {"version", null}, {"version", "-1"}}) {
            setUp(); form("/reviews/update"); web.parameters.put(value[0], value[1]); servlet.doPost(web.request, web.response);
            assertEquals(400, web.status); assertEquals(0, reviews.calls);
        }
    }
    public void testDuplicateAndStaleWritesDoNotRefreshVersionSilently() throws Exception {
        for (ReviewResult result : new ReviewResult[] {ReviewResult.ALREADY_EXISTS, ReviewResult.STALE}) {
            setUp(); form("/reviews/update"); reviews.result = result; servlet.doPost(web.request, web.response);
            assertEquals(409, web.status); assertEquals("2", web.attributes.get("formVersion"));
            assertEquals("日本語の感想\nIt's fun!", web.attributes.get("formBody"));
        }
        setUp(); form("/reviews/delete"); reviews.result = ReviewResult.STALE; servlet.doPost(web.request, web.response);
        assertEquals(409, web.status); assertEquals("/WEB-INF/views/review-conflict.jsp", web.forwarded);
    }
    public void testPurchaseRequirementAndOwnershipFailuresAreDistinct() throws Exception {
        form("/reviews/create"); reviews.result = ReviewResult.NOT_ELIGIBLE; servlet.doPost(web.request, web.response);
        assertEquals(403, web.status); assertEquals(false, web.attributes.get("canReview"));
        setUp(); form("/reviews/update"); reviews.result = ReviewResult.NOT_FOUND; servlet.doPost(web.request, web.response); assertEquals(404, web.status);
    }
    public void testMyReviewsUsesOwnIdAndHandlesDatabaseErrorWithoutEmptyState() throws Exception {
        web.path = "/account/reviews"; web.parameters.put("userId", "999"); web.parameters.put("reviewPage", "2");
        servlet.doGet(web.request, web.response); assertEquals(7L, reviews.userId); assertEquals(2, reviews.requestedPage);
        assertEquals("/WEB-INF/views/account-reviews.jsp", web.forwarded);
        setUp(); web.path = "/account/reviews"; reviews.failure = new SQLException("private SQL"); servlet.doGet(web.request, web.response);
        assertEquals(503, web.status); assertNull(web.attributes.get("reviews")); assertNotNull(web.attributes.get("reviewError"));
    }
    public void testDatabaseFailureKeepsSubmittedReviewAndDoesNotExposeSql() throws Exception {
        form("/reviews/create"); reviews.failure = new SQLException("private SQL"); servlet.doPost(web.request, web.response);
        assertEquals(503, web.status); assertEquals("日本語の感想\nIt's fun!", web.attributes.get("formBody"));
        assertFalse(web.attributes.get("reviewError").toString().contains("private SQL"));
    }
    public void testInvalidGameIdsMissingGamesAndUnsupportedMethodsAreRejected() throws Exception {
        for (String value : new String[] {null, "0", "2147483648", "x"}) {
            setUp(); web.parameters.put("gameId", value); servlet.doGet(web.request, web.response); assertEquals(400, web.status);
        }
        setUp(); games.game = null; servlet.doGet(web.request, web.response); assertEquals(404, web.status);
        setUp(); web.path = "/reviews/create"; servlet.doGet(web.request, web.response); assertEquals(405, web.status);
        setUp(); web.sessionValues.clear(); servlet.doGet(web.request, web.response); assertEquals(401, web.status);
    }
}
