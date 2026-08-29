package com.culberth.tools.datablaster.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects any request whose {@code Host} header is not a loopback host, on <em>every</em> path.
 *
 * <p>The connector binds loopback, but that does not stop DNS rebinding: a page on an attacker's
 * domain whose DNS resolves to 127.0.0.1 reaches this server same-origin and can read the
 * response. A browser sets {@code Host} from the URL authority and cannot forge it, so the header
 * is what gives such a request away.
 *
 * <p>This lives in a filter rather than in a handler because a per-handler check leaves every
 * other path — {@code /}, an unmapped URL falling through to the error controller, and every
 * endpoint added later — answering rebound requests. The rejection is a bare 404 with no body: a
 * distinctive status or error message would still fingerprint the application, which is the
 * disclosure the check exists to prevent.
 */
@Component
public class LoopbackHostFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_HOSTS =
            Set.of("localhost", "127.0.0.1", "[::1]", "[0:0:0:0:0:0:0:1]", "[::ffff:127.0.0.1]");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!isLoopbackHost(request.getHeader("Host"))) {
            // No body, no distinguishing message — an attacker learns only that something is here,
            // which any listening socket already reveals.
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Visible for testing. */
    static boolean isLoopbackHost(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        // Strip the port. Truncating at the LAST colon is what makes "evil.example:localhost"
        // safe; the bracket check keeps an IPv6 literal's own colons intact.
        int portSeparator = host.lastIndexOf(':');
        boolean ipv6Literal = host.startsWith("[");
        String hostname = host;
        if (portSeparator > -1 && (!ipv6Literal || host.indexOf(']') < portSeparator)) {
            hostname = host.substring(0, portSeparator);
        }
        return ALLOWED_HOSTS.contains(hostname.toLowerCase(Locale.ROOT));
    }
}
