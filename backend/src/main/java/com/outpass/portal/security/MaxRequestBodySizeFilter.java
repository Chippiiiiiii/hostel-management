package com.outpass.portal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

// Bounds the request body of the JSON endpoints that accept a base64-encoded photo
// (registration/profile-picture upload, complaint photo) so a client can't force the server
// to buffer an arbitrarily large payload into memory just by omitting/understating
// Content-Length -- the frontend's own 2MB client-side check is trivially bypassed by anyone
// calling the API directly. Two layers, matching the two ways an oversized body can arrive:
//   1. Content-Length is present and honest -> rejected immediately, before the body is read
//      at all.
//   2. Content-Length is missing (chunked transfer-encoding) or understates the real body ->
//      the body is read here, bounded at MAX_BODY_BYTES + one buffer's worth, *before*
//      DispatcherServlet/Jackson ever sees it. Reading it inside a message converter instead
//      (e.g. throwing from a wrapped InputStream mid-parse) doesn't work reliably: Jackson
//      catches any exception thrown from the underlying reader -- checked or not -- and
//      rewraps it as a generic JSON parse error, so the 413 status/message this filter wants
//      to return never survives past that point. Enforcing the bound here, before dispatch,
//      avoids that entirely and keeps the response deterministic.
@Component
public class MaxRequestBodySizeFilter extends OncePerRequestFilter {

    // A legitimate 2MB photo base64-encodes to ~2.8M characters (see
    // StudentRegistrationRequest.profilePicture); 4MB leaves comfortable room for that plus
    // the rest of the JSON body without allowing an unbounded upload.
    static final long MAX_BODY_BYTES = 4L * 1024 * 1024;

    private static final Set<String> PROTECTED_PATHS =
            Set.of("/auth/student/register", "/student/profile", "/student/complaints",
                    "/student/profile/id-card-photo");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        // getServletPath() behavior for a "/" default-mapped dispatcher varies across
        // servlet-spec versions; stripping the context path from the request URI directly
        // is unambiguous regardless (server.servlet.context-path=/api in this app, so this
        // yields e.g. "/student/profile" either way).
        String contextPath = request.getContextPath();
        String path = request.getRequestURI();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (!PROTECTED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        long declaredLength = request.getContentLengthLong();
        if (declaredLength > MAX_BODY_BYTES) {
            respondTooLarge(response);
            return;
        }

        byte[] body;
        try {
            body = readBounded(request.getInputStream(), MAX_BODY_BYTES);
        } catch (BodyTooLargeSignal signal) {
            respondTooLarge(response);
            return;
        }

        filterChain.doFilter(new ReplayableRequestWrapper(request, body), response);
    }

    // Reads at most maxBytes + one buffer's worth into memory, then stops -- never the full
    // body if it's oversized, which is the point (a client can't force unbounded buffering
    // just by sending a huge/never-ending chunked body with no honest Content-Length).
    private byte[] readBounded(InputStream in, long maxBytes) throws IOException, BodyTooLargeSignal {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(chunk)) != -1) {
            total += n;
            buffer.write(chunk, 0, n);
            if (total > maxBytes) {
                throw new BodyTooLargeSignal();
            }
        }
        return buffer.toByteArray();
    }

    // Hand-built JSON rather than going through Jackson's ObjectMapper: jackson-databind is
    // only a runtime-scope transitive dependency in this project (pulled in by
    // jjwt-jackson), not a compile-scope one, so ObjectMapper can't be referenced from
    // source here (see SecurityConfig.writeErrorResponse for the same pattern).
    private void respondTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"success\":false,\"message\":\"Request body exceeds the maximum allowed size (4MB)\"}");
    }

    // Internal signal only -- never leaves this filter, so it doesn't need to be a
    // RuntimeException subtype routed through GlobalExceptionHandler.
    private static final class BodyTooLargeSignal extends Exception {
    }

    // Downstream (Jackson, Spring's argument resolvers) needs to read the body from a real
    // InputStream; since it's already been fully read into `body` above, this just replays
    // that byte array instead of the original (now-exhausted) stream.
    private static class ReplayableRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] body;

        ReplayableRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return source.read();
                }

                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // Body is already fully buffered -- nothing async to notify.
                }
            };
        }
    }
}
