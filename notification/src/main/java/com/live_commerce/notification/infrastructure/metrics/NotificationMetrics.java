package com.live_commerce.notification.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * 알림(Notification) 서비스의 비즈니스 메트릭을 수집하는 클래스
 *
 * 📊 수집하는 메트릭:
 * - 알림 전송 성공/실패 횟수
 * - 재시도 횟수
 * - DLQ로 이동한 메시지 수
 *
 * 🎯 목적:
 * - Prometheus + Grafana에서 실시간으로 알림 전송 상황 모니터링
 * - 장애 발생 시 빠른 감지 및 대응
 *
 * 💡 사용법:
 * 1. NotificationService에 주입 (생성자 주입)
 * 2. 알림 전송 성공 시: metrics.incrementSuccess()
 * 3. 알림 전송 실패 시: metrics.incrementFailure()
 * 4. 재시도 발생 시: metrics.incrementRetry()
 * 5. DLQ 이동 시: metrics.incrementDLQ()
 *
 * @author Claude + 사용자
 * @since 2026-01-21
 */
@Component  // Spring Bean으로 등록 (자동 주입 가능)
public class NotificationMetrics {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Counter: 계속 증가하는 숫자를 기록하는 메트릭
    // - 절대 감소하지 않음 (리셋되거나 증가만 함)
    // - 예: 총 요청 수, 총 에러 수
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final Counter notificationSentSuccess;    // 성공한 알림 수
    private final Counter notificationSentFailure;    // 실패한 알림 수
    private final Counter notificationRetry;          // 재시도한 알림 수
    private final Counter dlqMessageCount;            // DLQ로 이동한 메시지 수

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Timer: 소요 시간의 분포(개수/합계/히스토그램 버킷)를 기록하는 메트릭
    // - Counter만으로는 "몇 건 나갔나"만 알 뿐 "얼마나 걸렸나"를 못 잰다.
    //   P95/P99 지연을 뽑으려면 Timer가 있어야 한다.
    // - 버킷/퍼센타일은 application.yml의
    //   management.metrics.distribution 설정에서 메트릭 이름별로 켠다.
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private final MeterRegistry registry;             // Timer.start()에 필요
    private final Timer notificationSendTimer;        // 수신자 1명에게 보내는 데 걸린 시간
    private final Timer notificationConsumeTimer;     // Consumer가 메시지 1건을 처리한 전체 시간

    /**
     * 생성자: MeterRegistry를 받아서 메트릭 등록
     *
     * 📝 설명:
     * - MeterRegistry는 Spring Boot가 자동으로 생성해서 주입해줌
     * - 여기에 메트릭을 등록하면 Prometheus가 자동으로 수집 가능
     * - 생성자 주입 방식 사용 (권장 방식)
     *
     * @param registry Spring Boot가 자동으로 주입해주는 MeterRegistry
     */
    public NotificationMetrics(MeterRegistry registry) {

        this.registry = registry;

        // ────────────────────────────────────────────────────────────
        // 1️⃣ 알림 전송 성공 Counter
        // ────────────────────────────────────────────────────────────
        // 메트릭 이름: notification_sent_total
        // 태그: status=success (성공/실패 구분용)
        // 설명: 알림 전송에 성공한 총 횟수
        this.notificationSentSuccess = Counter.builder("notification.sent")
                .description("알림 전송 성공 횟수")
                .tag("status", "success")  // 태그로 성공/실패 구분
                .register(registry);

        // ────────────────────────────────────────────────────────────
        // 2️⃣ 알림 전송 실패 Counter
        // ────────────────────────────────────────────────────────────
        // 메트릭 이름: notification_sent_total (성공과 동일)
        // 태그: status=failure (실패 구분용)
        // 설명: 알림 전송에 실패한 총 횟수
        this.notificationSentFailure = Counter.builder("notification.sent")
                .description("알림 전송 실패 횟수")
                .tag("status", "failure")
                .register(registry);

        // ────────────────────────────────────────────────────────────
        // 3️⃣ 재시도 Counter
        // ────────────────────────────────────────────────────────────
        // 메트릭 이름: notification_retry_total
        // 설명: 알림 재시도가 발생한 총 횟수
        this.notificationRetry = Counter.builder("notification.retry")
                .description("알림 재시도 횟수")
                .register(registry);

        // ────────────────────────────────────────────────────────────
        // 4️⃣ DLQ 메시지 Counter
        // ────────────────────────────────────────────────────────────
        // 메트릭 이름: notification_dlq_total
        // 설명: DLQ(Dead Letter Queue)로 이동한 메시지 수
        // 참고: 최대 재시도 횟수 초과 시 DLQ로 이동
        this.dlqMessageCount = Counter.builder("notification.dlq")
                .description("DLQ로 이동한 메시지 수")
                .register(registry);

        // ────────────────────────────────────────────────────────────
        // 5️⃣ 개별 발송 Timer
        // ────────────────────────────────────────────────────────────
        // 메트릭 이름: notification_send_seconds{_count,_sum,_bucket}
        // 측정 구간: AlertSender.send() 호출 1건 (수신자 1명)
        // 활용: 발송 채널 자체가 느려진 건지 판별
        this.notificationSendTimer = Timer.builder("notification.send")
                .description("수신자 1명에게 알림을 발송하는 데 걸린 시간")
                .register(registry);

        // ────────────────────────────────────────────────────────────
        // 6️⃣ 메시지 처리 Timer
        // ────────────────────────────────────────────────────────────
        // 메트릭 이름: notification_consume_seconds{_count,_sum,_bucket}
        // 측정 구간: Consumer가 메시지 1건을 받아 전체 수신자 발송을 마칠 때까지
        // 활용: 이력서에 쓰는 "알림 발송 P99 지연"의 근거가 되는 지표
        //       (http_server_requests는 트리거 API 응답 시간이라 발송 지연이 아니다)
        this.notificationConsumeTimer = Timer.builder("notification.consume")
                .description("Kafka 메시지 1건을 소비해 알림 발송을 마칠 때까지 걸린 시간")
                .register(registry);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Timer 기록용 메서드
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 개별 발송 구간의 소요 시간을 기록한다.
     *
     * <p>Timer.record(Runnable)은 내부가 try-finally라서, 발송이 예외로 끝나도
     * 소요 시간은 기록되고 예외는 그대로 호출부로 전파된다.
     * (실패 건도 지연 분포에 포함돼야 타임아웃으로 느려지는 상황을 잡을 수 있다)
     *
     * @param send 실제 발송 동작
     */
    public void recordSend(Runnable send) {
        notificationSendTimer.record(send);
    }

    /**
     * 메시지 처리 구간 계측 시작. 반환된 Sample을 {@link #stopConsume(Timer.Sample)}에 넘긴다.
     *
     * <p>processByMessage가 checked exception(IOException)을 던져서 Runnable로 감쌀 수 없어
     * Sample 방식을 쓴다.
     */
    public Timer.Sample startConsume() {
        return Timer.start(registry);
    }

    /**
     * 메시지 처리 구간 계측 종료. 반드시 finally에서 호출해 실패 건도 함께 기록한다.
     */
    public void stopConsume(Timer.Sample sample) {
        sample.stop(notificationConsumeTimer);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Public Methods: Service 계층에서 호출할 메서드들
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 알림 전송 성공 시 호출
     *
     * 📝 사용 시점:
     * - 알림이 성공적으로 전송되었을 때
     * - try-catch의 정상 처리 부분에서 호출
     *
     * 📊 효과:
     * - notification_sent_total{status="success"} 값이 1 증가
     */
    public void incrementSuccess() {
        notificationSentSuccess.increment();  // Counter를 1 증가시킴
    }

    /**
     * 알림 전송 실패 시 호출
     *
     * 📝 사용 시점:
     * - 알림 전송 중 예외가 발생했을 때
     * - catch 블록에서 호출
     *
     * 📊 효과:
     * - notification_sent_total{status="failure"} 값이 1 증가
     */
    public void incrementFailure() {
        notificationSentFailure.increment();
    }

    /**
     * 재시도 발생 시 호출
     *
     * 📝 사용 시점:
     * - 알림 전송 실패 후 재시도할 때
     * - notification.increaseRetryCount() 호출 직후
     *
     * 📊 효과:
     * - notification_retry_total 값이 1 증가
     *
     * 💡 활용:
     * - 재시도 횟수가 많다 = 네트워크 불안정 or Kafka 장애 의심
     * - Grafana에서 재시도 추이를 모니터링
     */
    public void incrementRetry() {
        notificationRetry.increment();
    }

    /**
     * DLQ로 메시지 이동 시 호출
     *
     * 📝 사용 시점:
     * - 최대 재시도 횟수를 초과했을 때
     * - notification.markAsFailed() 호출 직후
     *
     * 📊 효과:
     * - notification_dlq_total 값이 1 증가
     *
     * ⚠️ 주의:
     * - DLQ 메시지가 쌓이면 반드시 확인 필요
     * - Grafana Alert 설정 권장 (DLQ > 10건 시 Slack 알림 등)
     */
    public void incrementDLQ() {
        dlqMessageCount.increment();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 추가 확장 가능한 메서드 (선택사항)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 특정 타입별 알림 전송 수 증가
     *
     * 📝 사용 예:
     * - incrementSuccessByType("LIVE_BROADCAST")
     * - incrementSuccessByType("PRODUCT_RESTOCK")
     *
     * 📊 메트릭:
     * - notification_sent_total{status="success",type="LIVE_BROADCAST"}
     * - notification_sent_total{status="success",type="PRODUCT_RESTOCK"}
     *
     * 💡 활용:
     * - 알림 타입별 통계 확인
     * - 어떤 타입의 알림이 많이 전송되는지 파악
     *
     * @param type 알림 타입 (LIVE_BROADCAST, PRODUCT_RESTOCK 등)
     */
    // public void incrementSuccessByType(String type) {
    //     Counter.builder("notification.sent")
    //             .tag("status", "success")
    //             .tag("type", type)
    //             .register(registry)
    //             .increment();
    // }

    /**
     * 실패 원인별 통계
     *
     * 📝 사용 예:
     * - incrementFailureByReason("kafka_down")
     * - incrementFailureByReason("network_timeout")
     *
     * 📊 메트릭:
     * - notification_sent_total{status="failure",reason="kafka_down"}
     *
     * 💡 활용:
     * - 실패 원인 분석
     * - 가장 빈번한 실패 원인 파악 → 우선 개선 대상 선정
     *
     * @param reason 실패 원인
     */
    // public void incrementFailureByReason(String reason) {
    //     Counter.builder("notification.sent")
    //             .tag("status", "failure")
    //             .tag("reason", reason)
    //             .register(registry)
    //             .increment();
    // }
}
