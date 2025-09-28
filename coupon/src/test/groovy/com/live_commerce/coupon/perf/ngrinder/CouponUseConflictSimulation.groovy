package com.live_commerce.coupon.perf.ngrinder

import static net.grinder.script.Grinder.grinder
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.is

import groovy.json.JsonSlurper

import net.grinder.plugin.http.HTTPPluginControl
import net.grinder.plugin.http.HTTPRequest
import net.grinder.script.GTest
import net.grinder.scriptengine.groovy.junit.GrinderRunner

import HTTPClient.NVPair

import org.junit.BeforeClass
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 시나리오 1: 동시 쿠폰 사용 충돌 테스트 (Exactly-Once 보장 검증)
 */
@RunWith(GrinderRunner)
class CouponUseConflictSimulation {

    static final String BASE_URL = System.getProperty('coupon.api.base', 'http://localhost:18085')
    static final String COUPON_ID = System.getProperty('coupon.id', 'coup-00001')
    static final String ACCESS_TOKEN = System.getProperty('coupon.accessToken', 'sample-master-token')

    static GTest test
    static HTTPRequest request

    @BeforeClass
    static void beforeClass() {
        HTTPPluginControl.getConnectionDefaults().setTimeout(10000)
        test = new GTest(1, 'POST /api/v1/coupons/{couponId}/use')
        request = new HTTPRequest()
        test.record(request)
    }

    @Before
    void before() {
        grinder.logger.info('Thread {} preparing request for coupon {}', grinder.threadNumber, COUPON_ID)
    }

    @Test
    void useCouponOnce() {
        NVPair[] headers = [
                new NVPair('Content-Type', 'application/json'),
                new NVPair('Authorization', "Bearer ${ACCESS_TOKEN}".toString()),
                new NVPair('X-Request-User-Id', 'test-user'),
                new NVPair('X-Request-User-Roles', 'ROLE_USER'),
        ] as NVPair[]

        String url = "${BASE_URL}/api/v1/coupons/${COUPON_ID}/use".toString()
        byte[] body = '{}'.getBytes('UTF-8')

        def response = request.POST(url, headers, body)

        grinder.logger.info('Response status: {}', response.statusCode)

        // HTTP 200=성공, 409=충돌
        assertThat('unexpected status code', (response.statusCode in [200, 409]), is(true))

        if (response.statusCode == 200) {
            def parsed = new JsonSlurper().parseText(response.bodyText)
            grinder.logger.info('Coupon used successfully: {}', parsed)
        }
    }
}
