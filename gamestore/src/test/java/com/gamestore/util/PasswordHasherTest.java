package com.gamestore.util;

import junit.framework.TestCase;

public class PasswordHasherTest extends TestCase {
    public void testHashUsesIndependentSaltAndVerifiesUnicodePassword() {
        String password = "로그인 日本語 password 42";
        String first = PasswordHasher.hash(password);
        String second = PasswordHasher.hash(password);
        assertFalse(first.equals(second));
        assertFalse(first.contains(password));
        assertTrue(first.startsWith("pbkdf2-sha256$600000$"));
        assertTrue(PasswordHasher.verify(password, first));
        assertTrue(PasswordHasher.verify(password, second));
        assertFalse(PasswordHasher.verify("incorrect password", first));
    }

    public void testMalformedHashesAreRejected() {
        assertFalse(PasswordHasher.verify(null, "anything"));
        assertFalse(PasswordHasher.verify("password", null));
        assertFalse(PasswordHasher.verify("password", "plaintext"));
        assertFalse(PasswordHasher.verify("password", "pbkdf2-sha256$600000$invalid$invalid"));
        assertFalse(PasswordHasher.verify("password", "pbkdf2-sha256$999999999$invalid$invalid"));
        assertFalse(PasswordHasher.verify("password", "pbkdf2-sha256$-1$invalid$invalid"));
    }
}
