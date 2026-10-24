package com.gamestore.servlet;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import com.gamestore.model.*;
import com.gamestore.repository.JdbcWishlistRepository;
import com.gamestore.support.WebFixture;
import junit.framework.TestCase;

public class WishlistServletTest extends TestCase {
    private static class Wishes extends JdbcWishlistRepository {
        long user; int id; int adds; int removes; int page;
        SQLException failure;
        @Override public boolean add(long userId, int gameId) throws SQLException { check(); user=userId;id=gameId;adds++;return gameId==1; }
        @Override public void remove(long userId, int gameId) throws SQLException { check();user=userId;id=gameId;removes++; }
        @Override public Set<String> findIdsForUser(long userId) throws SQLException { check();user=userId;return Set.of("1"); }
        @Override public CatalogPage findPageForUser(long userId, int page, int size) throws SQLException {
            check();user=userId;this.page=page;
            return new CatalogPage(List.of(new Game("1","Saved",3200,"Adventure","a.png","",false)),List.of(),1,1,1,size);
        }
        private void check() throws SQLException { if(failure!=null){throw failure;} }
    }
    private WebFixture member(String path) {
        WebFixture web=new WebFixture();web.path=path;web.sessionExists=true;
        web.sessionValues.put("loginUser",new User(7L,"alice"));web.parameters.put("gameId","1");
        return web;
    }
    private void call(WebFixture web,Wishes wishes,boolean post)throws Exception {
        WishlistServlet servlet=new WishlistServlet(wishes);servlet.init(web.config);
        if(post){servlet.doPost(web.request,web.response);}else{servlet.doGet(web.request,web.response);}
    }
    public void testListUsesSessionMemberAndShowsSuspendedSavedGame()throws Exception {
        WebFixture web=member("/wishlist");web.parameters.put("userId","999");web.parameters.put("page","2");
        Wishes wishes=new Wishes();call(web,wishes,false);
        assertEquals(7L,wishes.user);assertEquals(2,wishes.page);assertEquals(1,web.attributes.get("resultCount"));
        assertFalse(((Game)((List<?>)web.attributes.get("games")).get(0)).isActive());
        assertEquals("/wishlist",web.attributes.get("paginationPath"));assertEquals("/WEB-INF/views/wishlist.jsp",web.forwarded);
    }
    public void testAddAndRemoveIgnoreUserIdParameterAndReturnToCurrentPage()throws Exception {
        for(String action:new String[]{"add","remove"}) {
            WebFixture web=member("/wishlist/"+action);web.parameters.put("userId","999");
            web.parameters.put("returnTo","/browse?q=moon&genre=Adventure&sort=price_asc&page=2");
            Wishes wishes=new Wishes();call(web,wishes,true);
            assertEquals(7L,wishes.user);assertEquals(1,wishes.id);
            assertEquals(action.equals("add")?1:0,wishes.adds);assertEquals(action.equals("remove")?1:0,wishes.removes);
            assertEquals("/gamestore/browse?q=moon&genre=Adventure&sort=price_asc&page=2",web.redirect);
        }
    }
    public void testInvalidAndMissingGamesDoNotReportSuccess()throws Exception {
        for(String id:new String[]{null,"0","-1","x","2147483648"}) {
            WebFixture web=member("/wishlist/add");web.parameters.put("gameId",id);Wishes wishes=new Wishes();call(web,wishes,true);
            assertEquals(400,web.status);assertEquals(0,wishes.adds);
        }
        WebFixture missing=member("/wishlist/add");missing.parameters.put("gameId","999");call(missing,new Wishes(),true);assertEquals(404,missing.status);
    }
    public void testExternalReturnUrlsCannotRedirectOutsideApplication()throws Exception {
        for(String url:new String[]{"https://evil.example/","//evil.example/","/\\evil.example/","/admin/games","/browse\r\nLocation: https://evil.example/"}) {
            WebFixture web=member("/wishlist/remove");web.parameters.put("returnTo",url);call(web,new Wishes(),true);
            assertEquals("/gamestore/wishlist",web.redirect);
        }
    }
    public void testMutationsDoNotRunOnGetOrListPost()throws Exception {
        WebFixture web=member("/wishlist/add");Wishes wishes=new Wishes();call(web,wishes,false);assertEquals(405,web.status);assertEquals(0,wishes.adds);
        web=member("/wishlist");call(web,wishes,true);assertEquals(405,web.status);
    }
    public void testFailureShowsUnavailableStateInsteadOfEmptyWishlist()throws Exception {
        Wishes wishes=new Wishes();wishes.failure=new SQLException("private database details");
        WebFixture web=member("/wishlist");call(web,wishes,false);
        assertEquals(503,web.status);assertNotNull(web.attributes.get("wishlistError"));assertNull(web.attributes.get("games"));
        assertFalse(web.attributes.get("wishlistError").toString().contains("private database details"));
        web=member("/wishlist/add");call(web,wishes,true);assertEquals(503,web.status);assertNull(web.redirect);
    }
    public void testDirectUnauthenticatedInvocationDoesNotReachRepository()throws Exception {
        WebFixture web=new WebFixture();web.path="/wishlist/add";Wishes wishes=new Wishes();call(web,wishes,true);
        assertEquals(401,web.status);assertEquals(0,wishes.adds);
    }
}
