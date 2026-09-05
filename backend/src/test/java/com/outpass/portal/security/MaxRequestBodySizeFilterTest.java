package com.outpass.portal.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Covers the photo-upload size cap's request-level enforcement: rejecting a declared
 * oversized Content-Length before reading any body at all, and -- the part a DTO @Size
 * constraint alone can't do -- rejecting a body that turns out to exceed the limit while
 * being streamed, without ever buffering the full oversized payload into memory.
 */
class MaxRequestBodySizeFilterTest {

    private final MaxRequestBodySizeFilter filter = new MaxRequestBodySizeFilter();

    @Test
    void unprotectedPath_passesThroughUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/student/outpass");
        request.setContent("small body".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void declaredContentLengthOverLimit_rejectedBeforeReadingBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/student/profile");
        request.setContentType("application/json");
        // setContent() also sets Content-Length to the actual byte array length -- oversized
        // on purpose, standing in for a client declaring (honestly) a too-large body.
        request.setContent(new byte[(int) (MaxRequestBodySizeFilter.MAX_BODY_BYTES + 1)]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("exceeds the maximum allowed size");
        // The whole point: chain.doFilter() (and everything downstream -- Jackson, the
        // controller) is never invoked, so nothing ever tries to read the oversized body.
        verifyNoInteractions(chain);
    }

    @Test
    void streamedBodyExceedsLimit_understatedContentLength_rejectedWith413_neverReachesChain() throws Exception {
        // Simulates a chunked/understated-Content-Length request: the servlet container
        // reports no declared length, but the actual stream yields more bytes than the limit
        // once read. This is the case a Content-Length pre-check alone can't catch.
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/student/profile") {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        byte[] oversizedBody = new byte[(int) (MaxRequestBodySizeFilter.MAX_BODY_BYTES + 1024)];
        Arrays.fill(oversizedBody, (byte) 'a');
        request.setContent(oversizedBody);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("exceeds the maximum allowed size");
        // Confirms rejection happens entirely within the filter -- DispatcherServlet/the
        // controller/Jackson never see this request at all once it's deemed too large.
        verifyNoInteractions(chain);
    }

    @Test
    void streamedBodyWithinLimit_understatedContentLength_reachesChainWithFullBodyIntact() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/student/profile") {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        byte[] body = "{\"contactNumber\":\"9000000000\"}".getBytes();
        request.setContent(body);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        byte[][] replayedBody = new byte[1][];
        FilterChain chain = (req, res) -> {
            chainInvoked.set(true);
            replayedBody[0] = req.getInputStream().readAllBytes();
        };

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked).isTrue();
        assertThat(replayedBody[0]).isEqualTo(body);
    }
}
