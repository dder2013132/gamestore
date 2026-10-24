package com.gamestore.util;

import java.util.List;
import com.gamestore.model.Game;
import com.gamestore.support.WebFixture;
import junit.framework.TestCase;

public class CheckoutTokensTest extends TestCase {
    private List<Game> cart(int price) { return List.of(new Game("1", "Game", price, "Adventure", "a.png")); }
    public void testSameReviewedCartReusesTokenAcrossTabsAndReloads() {
        WebFixture web = new WebFixture();
        var first = CheckoutTokens.prepare(web.session, 7L, cart(4800));
        var second = CheckoutTokens.prepare(web.session, 7L, cart(4800));
        assertEquals(first, second); assertTrue(CheckoutTokens.valid(first.token()));
    }
    public void testMemberCartVersionAndPriceAreBoundToIntent() {
        WebFixture web = new WebFixture(); var intent = CheckoutTokens.prepare(web.session, 7L, cart(4800));
        assertTrue(CheckoutTokens.matches(web.session, intent, 7L, cart(4800)));
        assertFalse(CheckoutTokens.matches(web.session, intent, 8L, cart(4800)));
        assertFalse(CheckoutTokens.matches(web.session, intent, 7L, cart(5900)));
        CheckoutTokens.cartChanged(web.session);
        assertFalse(CheckoutTokens.matches(web.session, intent, 7L, cart(4800)));
        assertNull(CheckoutTokens.find(web.session, intent.token()));
    }
    public void testPendingIntentsAreBounded() {
        WebFixture web = new WebFixture(); var oldest = CheckoutTokens.prepare(web.session, 7L, cart(100));
        for (int i = 101; i < 120; i++) { CheckoutTokens.prepare(web.session, 7L, cart(i)); }
        assertNull(CheckoutTokens.find(web.session, oldest.token()));
        assertEquals(8, ((java.util.Map<?, ?>) web.sessionValues.get("checkoutIntents")).size());
    }
    public void testCompletionKeepsAnEditedCartAndClearsOnlyOriginalCart() {
        WebFixture web = new WebFixture(); web.sessionValues.put("cart", cart(4800));
        var intent = CheckoutTokens.prepare(web.session, 7L, cart(4800));
        CheckoutTokens.cartChanged(web.session); web.sessionValues.put("cart", cart(4800));
        CheckoutTokens.complete(web.session, intent);
        assertNotNull(web.sessionValues.get("cart"));
        var next = CheckoutTokens.prepare(web.session, 7L, cart(4800));
        CheckoutTokens.complete(web.session, next);
        assertNull(web.sessionValues.get("cart"));
    }
    public void testStaleRefreshCannotReplaceANewerCart() {
        WebFixture web = new WebFixture(); web.sessionValues.put("cart", cart(4800));
        var intent = CheckoutTokens.prepare(web.session, 7L, cart(4800));
        CheckoutTokens.cartChanged(web.session); List<Game> newer = cart(3000); web.sessionValues.put("cart", newer);
        assertFalse(CheckoutTokens.refreshIfCurrent(web.session, intent, cart(5900)));
        assertSame(newer, web.sessionValues.get("cart"));
    }
}
