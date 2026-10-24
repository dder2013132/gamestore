package com.gamestore.service;

import java.util.Map;
import java.util.Set;
import com.gamestore.model.User;
import com.gamestore.repository.JdbcWishlistRepository;
import com.gamestore.support.WebFixture;
import junit.framework.TestCase;

public class WishlistViewTest extends TestCase {
    private static class Wishes extends JdbcWishlistRepository {
        int calls;long user;
        @Override public Set<String> findIdsForUser(long userId){calls++;user=userId;return Set.of("12","17");}
    }
    public void testGuestsDoNotQueryAnotherMembersWishlist()throws Exception {
        WebFixture web=new WebFixture();Wishes wishes=new Wishes();WishlistView.populate(web.request,wishes);
        assertEquals(0,wishes.calls);assertEquals(Map.of(),web.attributes.get("wishlistIds"));
    }
    public void testMemberHeartsAreLoadedBySessionUserAndNavigationKeepsConditions()throws Exception {
        WebFixture web=new WebFixture();web.sessionExists=true;web.sessionValues.put("loginUser",new User(7L,"alice"));web.path="/browse";
        web.parameters.put("q","月 & moon");web.parameters.put("page","2");web.parameters.put("sort","price_asc");web.parameters.put("userId","999");
        Wishes wishes=new Wishes();WishlistView.populate(web.request,wishes);
        assertEquals(7L,wishes.user);assertEquals(Map.of("12",true,"17",true),web.attributes.get("wishlistIds"));
        assertEquals("/browse?q=%E6%9C%88+%26+moon&sort=price_asc&page=2",web.attributes.get("wishlistReturnTo"));
    }
}
