package org.springframework.web.servlet.config.annotation;

import com.outpass.portal.config.WebConfig;
import com.outpass.portal.interceptor.RateLimitInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Deliberately in Spring's own org.springframework.web.servlet.config.annotation package so
 * this test can call InterceptorRegistry#getInterceptors(), which is package-private/
 * protected -- there is no other public way to inspect what WebConfig actually registered.
 *
 * Covers the /warden/rooms/** rate-limit-exclusion fix: this path used to be fully exempt
 * from RateLimitInterceptor even though it requires only WARDEN/ADMIN auth (not full
 * unauthenticated exposure like /auth/**), leaving a compromised/malicious staff account free
 * to hammer destructive room-management operations (removeBuilding, removeFloor, etc.) with
 * no throttle at all.
 */
class WebConfigTest {

    private final WebConfig webConfig = new WebConfig(mock(RateLimitInterceptor.class));

    private MappedInterceptor registeredInterceptor() {
        InterceptorRegistry registry = new InterceptorRegistry();
        webConfig.addInterceptors(registry);
        return (MappedInterceptor) registry.getInterceptors().get(0);
    }

    private boolean matches(MappedInterceptor interceptor, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        ServletRequestPathUtils.parseAndCache(request);
        return interceptor.matches(request);
    }

    @Test
    void wardenRoomManagementEndpointsAreNoLongerExemptFromRateLimiting() {
        MappedInterceptor interceptor = registeredInterceptor();

        assertThat(matches(interceptor, "/warden/rooms/buildings")).isTrue();
        assertThat(matches(interceptor, "/warden/rooms/5/allocate")).isTrue();
        assertThat(matches(interceptor, "/warden/rooms/bulk-allocate")).isTrue();
    }

    @Test
    void otherWardenEndpointsRemainRateLimited() {
        MappedInterceptor interceptor = registeredInterceptor();

        assertThat(matches(interceptor, "/warden/outpasses/pending")).isTrue();
        assertThat(matches(interceptor, "/student/outpass")).isTrue();
        assertThat(matches(interceptor, "/security/outpasses/scan")).isTrue();
        assertThat(matches(interceptor, "/admin/wardens")).isTrue();
    }

    @Test
    void authEndpointsRemainExemptSinceTheyHaveTheirOwnDedicatedRateLimiting() {
        MappedInterceptor interceptor = registeredInterceptor();

        assertThat(matches(interceptor, "/auth/student/login")).isFalse();
        assertThat(matches(interceptor, "/auth/refresh")).isFalse();
    }
}
