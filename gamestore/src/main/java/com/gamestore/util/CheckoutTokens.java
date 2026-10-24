package com.gamestore.util;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.gamestore.model.Game;
import jakarta.servlet.http.HttpSession;

/** Server-issued intents bind confirmation to a member, cart version and reviewed prices. */
public final class CheckoutTokens {
    public record Intent(String token, long userId, long cartVersion, String fingerprint) implements Serializable { }
    private CheckoutTokens() { }

    public static boolean valid(String token) {
        if (token == null) { return false; }
        try { return UUID.fromString(token).toString().equalsIgnoreCase(token); }
        catch (IllegalArgumentException e) { return false; }
    }

    public static Intent prepare(HttpSession session, long userId, List<Game> cart) {
        synchronized (session) {
            Map<String, Intent> intents = intents(session);
            long version = version(session);
            String fingerprint = fingerprint(cart);
            for (Intent intent : intents.values()) {
                if (intent.userId() == userId && intent.cartVersion() == version && intent.fingerprint().equals(fingerprint)) { return intent; }
            }
            Intent intent = new Intent(UUID.randomUUID().toString(), userId, version, fingerprint);
            while (intents.size() >= 8) { intents.remove(intents.keySet().iterator().next()); }
            intents.put(intent.token(), intent);
            return intent;
        }
    }

    public static Intent find(HttpSession session, String token) {
        synchronized (session) { return intents(session).get(token); }
    }

    public static boolean matches(HttpSession session, Intent intent, long userId, List<Game> cart) {
        synchronized (session) {
            return intent != null && intent.userId() == userId && intent.cartVersion() == version(session)
                    && cart != null && intent.fingerprint().equals(fingerprint(cart));
        }
    }

    public static void discard(HttpSession session, String token) {
        synchronized (session) { intents(session).remove(token); }
    }

    public static boolean refreshIfCurrent(HttpSession session, Intent intent, List<Game> refreshed) {
        synchronized (session) {
            @SuppressWarnings("unchecked") List<Game> cart = (List<Game>) session.getAttribute("cart");
            if (!matches(session, intent, intent.userId(), cart)) { return false; }
            session.setAttribute("cart", new java.util.ArrayList<>(refreshed));
            discard(session, intent.token());
            return true;
        }
    }

    public static void cartChanged(HttpSession session) {
        synchronized (session) {
            session.setAttribute("cartVersion", version(session) + 1L);
            session.removeAttribute("checkoutIntents");
        }
    }

    public static void complete(HttpSession session, Intent intent) {
        synchronized (session) {
            @SuppressWarnings("unchecked") List<Game> cart = (List<Game>) session.getAttribute("cart");
            discard(session, intent.token());
            if (matches(session, intent, intent.userId(), cart)) {
                session.removeAttribute("cart");
                cartChanged(session);
            }
        }
    }

    private static long version(HttpSession session) {
        Object version = session.getAttribute("cartVersion");
        return version instanceof Long value ? value : 0L;
    }

    private static String fingerprint(List<Game> cart) {
        return cart.stream().map(game -> game.getId() + ":" + game.getPrice() + ":" + game.isActive())
                .sorted().collect(Collectors.joining(";"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Intent> intents(HttpSession session) {
        Map<String, Intent> intents = (Map<String, Intent>) session.getAttribute("checkoutIntents");
        if (intents == null) { intents = new LinkedHashMap<>(); session.setAttribute("checkoutIntents", intents); }
        return intents;
    }
}
