package com.live_commerce.coupon.perf.ngrinder

import groovy.json.JsonSlurper
import org.junit.BeforeClass
import org.junit.Test

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is
import static org.hamcrest.Matchers.isOneOf

class CouponUseConflictSimulation {

    // --- Runtime parameters ---
    static final String BASE_URL       = System.getProperty('coupon.api.base', 'http://localhost:19091')
    static final String PATH_TEMPLATE  = System.getProperty('coupon.api.path', '/api/v2/issued-coupons/{id}/use')
    static final String HTTP_METHOD    = System.getProperty('coupon.api.method', 'PATCH').toUpperCase()
    static final String REQUEST_BODY   = System.getProperty('coupon.api.body', '{}')
    // 자동으로 사용 가능한 쿠폰을 찾거나(목록 API) 없으면 발급 후 재조회
    static final boolean AUTO_DISCOVER = Boolean.parseBoolean(System.getProperty('coupon.discover', 'true'))
    static final String LIST_PATH      = System.getProperty('coupon.list.path', '/api/v1/issued-coupons/')
    static final String SIGNUP_FIRST_PATH_TPL = System.getProperty('coupon.signupFirst.path', '/api/v2/issued-coupons/{userId}/signup-first')
    static final String USER_ID        = System.getProperty('coupon.userId', '') // UUID (선택)
    static final String COUPON_ID_PROP = System.getProperty('coupon.id', '')
    static final String USER_ID_HEADER = System.getProperty('coupon.userId.header', '1d288225-c228-4335-a49f-1b759253b695')
    static final String USERNAME_HEADER = System.getProperty('coupon.username.header', 'xodnd8384')
    static final String USER_ROLE_HEADER = System.getProperty('coupon.role.header', 'MASTER')
    static final boolean USE_AUTH      = Boolean.parseBoolean(System.getProperty('coupon.api.useAuth', 'true'))

    static HttpClient client
    static final long REQ_TIMEOUT_SEC  = Long.getLong('coupon.req.timeout.seconds', 10L)
    static final Pattern UUID_RE = Pattern.compile('^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$')

    static String resolvedCouponId

    @BeforeClass
    static void setup() {
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build()

        resolvedCouponId = resolveCouponId()
        System.out.println("[setup] BASE_URL=" + BASE_URL + ", method=" + HTTP_METHOD
                + ", pathTpl=" + PATH_TEMPLATE + ", coupon=" + resolvedCouponId
                + ", java=" + System.getProperty("java.version") + ", vendor=" + System.getProperty("java.vendor"))
    }

    private static boolean isUUID(String v) {
        if (v == null) return false
        return UUID_RE.matcher(v).matches()
    }

    private static HttpResponse<String> callOnce(String method, String path, String body) {
        def base = BASE_URL.endsWith("/") ? BASE_URL.substring(0, BASE_URL.length() - 1) : BASE_URL
        def url = base + path

        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(REQ_TIMEOUT_SEC))
                .header('Content-Type', 'application/json')
                .header('Accept', 'application/json')
                .header('User-Agent', 'ngrinder-coupon-test')

        if (USE_AUTH) {
            b.header('X-User-Id', USER_ID_HEADER)
            b.header('X-User-Username', USERNAME_HEADER)
            b.header('X-User-Role', USER_ROLE_HEADER)
        }

        switch (method) {
            case 'POST' : b = b.POST (HttpRequest.BodyPublishers.ofString(body)); break
            case 'PUT'  : b = b.PUT  (HttpRequest.BodyPublishers.ofString(body)); break
            case 'PATCH': b = b.method('PATCH', HttpRequest.BodyPublishers.ofString(body)); break
            case 'GET'  : b = b.GET(); break
            default     : b = b.method(method, HttpRequest.BodyPublishers.noBody())
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString())
    }

    private static String resolveCouponId() {
        // 1) 주어진 값이 UUID 형태면 그대로 사용
        if (isUUID(COUPON_ID_PROP)) return COUPON_ID_PROP

        if (!AUTO_DISCOVER) {
            throw new IllegalArgumentException("coupon.id is missing or not a UUID: '${COUPON_ID_PROP}'")
        }

        // 2) 내 쿠폰 목록에서 ACTIVE 중 첫 건 추출
        def resp = callOnce('GET', LIST_PATH, null)
        if (resp.statusCode() == 200) {
            def json = new JsonSlurper().parseText(resp.body())
            def id = deepFindActiveCouponId(json)
            if (id != null) return id
        }

        // 3) (선택) 유저ID가 주어졌으면 가입-첫발급 후 다시 목록 조회
        if (isUUID(USER_ID)) {
            def path = SIGNUP_FIRST_PATH_TPL.replace("{userId}", USER_ID)
            callOnce('POST', path, '{}') // 201/204/409 허용
            def r2 = callOnce('GET', LIST_PATH, null)
            if (r2.statusCode() == 200) {
                def json2 = new JsonSlurper().parseText(r2.body())
                def id2 = deepFindActiveCouponId(json2)
                if (id2 != null) return id2
            }
        }

        throw new IllegalStateException("Unable to resolve usable couponId. Provide -Dcoupon.id=<uuid> or enable list/bootstrap endpoints.")
    }

    private static String deepFindActiveCouponId(Object node) {
        if (node == null) return null
        if (node instanceof Map) {
            Map m = (Map) node
            def status = m.get('status') ?: m.get('couponStatus') ?: m.get('useStatus')
            def id = m.get('id') ?: m.get('couponId')
            if (id instanceof String && isUUID(id.toString()) && (status == null || status.toString().equalsIgnoreCase('ACTIVE'))) {
                return id.toString()
            }
            for (def v : m.values()) {
                def got = deepFindActiveCouponId(v)
                if (got != null) return got
            }
        } else if (node instanceof List) {
            for (def v : (List) node) {
                def got = deepFindActiveCouponId(v)
                if (got != null) return got
            }
        }
        return null
    }

    private static HttpResponse<String> callUseOnce() {
        def path = PATH_TEMPLATE.replace("{id}", resolvedCouponId)
        return callOnce(HTTP_METHOD, path, REQUEST_BODY)
    }

    @Test
    void useCouponOnce_shouldReturn200or409() {
        def resp = callUseOnce()
        System.out.println("[single] status=" + resp.statusCode())
        assertThat("unexpected status " + resp.statusCode(), resp.statusCode(), isOneOf(200, 409))
    }

    @Test
    void useCouponConcurrent50_shouldBeExactlyOnce() {
        int threads = Integer.getInteger('coupon.threads', 50)
        def pool = Executors.newFixedThreadPool(threads)
        try {
            def tasks = (1..threads).collect { { -> callUseOnce().statusCode() } as Callable<Integer> }
            def futures = pool.invokeAll(tasks, 60, TimeUnit.SECONDS)
            pool.shutdown()
            pool.awaitTermination(30, TimeUnit.SECONDS)

            def codes = []
            int timeouts = 0
            int failures = 0
            futures.each { f ->
                if (f.isCancelled()) { timeouts++; return }
                try {
                    codes << f.get(2, TimeUnit.SECONDS)
                } catch (TimeoutException te) {
                    timeouts++
                } catch (Throwable t) {
                    failures++
                }
            }

            int success = codes.count { it == 200 }
            int conflicts = codes.count { it == 409 }

            System.out.println("[concurrent] codes=" + codes)
            System.out.println("[concurrent] success=" + success + ", conflicts=" + conflicts
                    + ", timeouts=" + timeouts + ", failures=" + failures)

            assertThat("timeouts>0", timeouts, is(0))
            assertThat("failures>0", failures, is(0))
            assertThat("success count not in [0,1]", success, isOneOf(0, 1))
            assertThat("conflict count mismatch", conflicts, is(codes.size() - success))
        } finally {
            pool.shutdownNow()
        }
    }
}
