package com.example.jfxribbon.web;

import static com.example.jfxribbon.web.LoopbackHostFilter.isLoopbackHost;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LoopbackHostFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "localhost", "localhost:8080",
            "127.0.0.1", "127.0.0.1:8080",
            "[::1]", "[::1]:8080",
            "[0:0:0:0:0:0:0:1]:8080",
            "[::ffff:127.0.0.1]:8080",
            "LOCALHOST", "LocalHost:8080"})
    void acceptsLoopbackHosts(String host) {
        assertTrue(isLoopbackHost(host), host);
    }

    /**
     * The suffix cases are the ones that matter most: an attacker can register
     * {@code localhost.evil.example}, so a refactor to startsWith/contains would open a real,
     * browser-reachable bypass while every other assertion here still passed.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "evil.example", "evil.example:8080",
            "localhost.evil.example", "localhost.evil.example:8080",
            "127.0.0.1.evil.example",
            "notlocalhost", "localhostx",
            "evil.example:localhost",
            "evil.example:8080:localhost",
            "[::1].evil.example",
            "]:localhost",
            "evil.example]:localhost"})
    void rejectsEverythingElse(String host) {
        assertFalse(isLoopbackHost(host), host);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsAMissingHost(String host) {
        assertFalse(isLoopbackHost(host), String.valueOf(host));
    }
}
