# 📊 Prometheus 모니터링 설정 가이드

> 알림(Notification) 서비스에 커스텀 메트릭을 추가하여 실시간 모니터링 구현

---

## 📌 목표

알림 기능의 실시간 통계를 Prometheus + Grafana로 모니터링

**추가할 메트릭:**
- 총 알림 전송 수
- 성공/실패 건수
- 재시도 횟수
- DLQ에 쌓인 메시지 수

---

## 📋 사전 확인 사항

### ✅ 이미 설치된 것들
- `build.gradle`에 Prometheus 의존성 추가됨
  ```gradle
  implementation 'io.micrometer:micrometer-registry-prometheus'
  implementation 'org.springframework.boot:spring-boot-starter-actuator'
  ```

- `prometheus.yml`에 notification-service 등록됨
  ```yaml
  - job_name: 'notification-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:19110']
  ```

---

## 🚀 STEP 1: Actuator 설정 확인 및 추가

### 📖 학습 내용

**Spring Boot Actuator란?**
- Spring Boot 애플리케이션의 상태를 외부에서 확인할 수 있게 해주는 도구
- `/actuator/health`, `/actuator/prometheus` 같은 엔드포인트 제공

**왜 필요한가?**
- Prometheus가 우리 서비스에서 메트릭을 가져가려면 엔드포인트가 필요
- Actuator가 이 엔드포인트를 제공해줌

### 📝 작업 내용

`notification/src/main/resources/application.yml` 확인 및 설정 추가

**설정 예시:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics  # 외부에 노출할 엔드포인트
  endpoint:
    prometheus:문ㅈ서 제외 
      enabled: true  # Prometheus 엔드포인트 활성화
  metrics:
    tags:
      application: notification-service  # 메트릭에 태그 추가 (구분용)
```

**설정 의미:**
- `include`: 외부에서 접근 가능한 엔드포인트 목록
- `prometheus.enabled`: Prometheus 형식의 메트릭 노출 활성화
- `tags.application`: 모든 메트릭에 자동으로 `application=notification-service` 태그 추가

### ✅ 완료 조건
- [ ] application.yml 설정 추가
- [ ] 서비스 실행 후 `http://localhost:19110/actuator/prometheus` 접근 가능 확인

---

## 🚀 STEP 2: NotificationMetrics 클래스 생성

### 📖 학습 내용

**Micrometer란?**
- Spring Boot에서 메트릭을 수집하는 라이브러리
- Prometheus, Grafana 등 여러 모니터링 시스템과 호환됨

**Counter란?**
- 계속 증가하는 숫자를 기록하는 메트릭
- 예: 총 요청 수, 총 에러 수
- 절대 감소하지 않음 (리셋되거나 증가만 함)

**Gauge란?**
- 오르락내리락하는 숫자를 기록하는 메트릭
- 예: 현재 메모리 사용량, DLQ에 쌓인 메시지 수

### 📝 작업 내용

`notification/src/main/java/.../infrastructure/metrics/NotificationMetrics.java` 생성

**파일 내용:**
```java
package com.live_commerce.notification.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 알림(Notification) 서비스의 비즈니스 메트릭을 수집하는 클래스
 * Prometheus + Grafana에서 실시간으로 모니터링 가능
 */
@Component
public class NotificationMetrics {

    // Counter: 계속 증가하는 숫자를 기록 (알림 전송 횟수)
    private final Counter notificationSentSuccess;    // 성공한 알림 수
    private final Counter notificationSentFailure;    // 실패한 알림 수
    private final Counter notificationRetry;          // 재시도한 알림 수
    private final Counter dlqMessageCount;            // DLQ로 이동한 메시지 수

    /**
     * 생성자: MeterRegistry를 받아서 메트릭 등록
     *
     * @param registry Spring Boot가 자동으로 주입해주는 MeterRegistry
     *                 여기에 메트릭을 등록하면 Prometheus가 수집 가능
     */
    public NotificationMetrics(MeterRegistry registry) {

        // 1. 알림 전송 성공 Counter
        this.notificationSentSuccess = Counter.builder("notification.sent")
                .description("알림 전송 성공 횟수")
                .tag("status", "success")  // 태그로 성공/실패 구분
                .register(registry);

        // 2. 알림 전송 실패 Counter
        this.notificationSentFailure = Counter.builder("notification.sent")
                .description("알림 전송 실패 횟수")
                .tag("status", "failure")
                .register(registry);

        // 3. 재시도 Counter
        this.notificationRetry = Counter.builder("notification.retry")
                .description("알림 재시도 횟수")
                .register(registry);

        // 4. DLQ 메시지 Counter
        this.dlqMessageCount = Counter.builder("notification.dlq")
                .description("DLQ로 이동한 메시지 수")
                .register(registry);
    }

    /**
     * 알림 전송 성공 시 호출
     * Counter를 1 증가시킴
     */
    public void incrementSuccess() {
        notificationSentSuccess.increment();
    }

    /**
     * 알림 전송 실패 시 호출
     * Counter를 1 증가시킴
     */
    public void incrementFailure() {
        notificationSentFailure.increment();
    }

    /**
     * 재시도 발생 시 호출
     * Counter를 1 증가시킴
     */
    public void incrementRetry() {
        notificationRetry.increment();
    }

    /**
     * DLQ로 메시지 이동 시 호출
     * Counter를 1 증가시킴
     */
    public void incrementDLQ() {
        dlqMessageCount.increment();
    }
}
```

**메트릭 이름 규칙:**
- `notification.sent`: 알림 전송 관련
- `notification.retry`: 재시도 관련
- `notification.dlq`: DLQ 관련
- 태그(`status=success/failure`)로 세부 구분

### ✅ 완료 조건
- [ ] NotificationMetrics.java 파일 생성
- [ ] 컴파일 에러 없이 빌드 성공

---

## 🚀 STEP 3: Service에 메트릭 수집 코드 추가

### 📖 학습 내용

**어디에 메트릭 수집 코드를 넣어야 하나?**
- 비즈니스 로직이 실행되는 곳 (Service 계층)
- 알림 전송 성공/실패를 판단할 수 있는 지점

**주의할 점:**
- try-catch로 예외 처리하면서 메트릭 수집
- 메트릭 수집 실패가 비즈니스 로직을 막으면 안 됨

### 📝 작업 내용

`NotificationService.java` 수정

**수정할 메서드:**
1. `processByMessage()` - Kafka 메시지 처리
2. `processNotification()` - 알림 발송 로직

**추가할 코드 예시:**
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ConsoleAlertSender consoleAlertSender;
    private final NotificationEventProducer producer;
    private final ObjectMapper objectMapper;
    private final AlertSender alertSender;

    // ✨ 추가: NotificationMetrics 주입
    private final NotificationMetrics metrics;

    // ... (기존 코드)

    public void processByMessage(NotificationCreatedEvent msg) throws IOException {
        Notification notification = notificationRepository.findById(msg.notificationId())
            .orElseThrow(() -> new IllegalArgumentException("알림 없음: " + msg.notificationId()));

        if (notification.isSent()) {
            return;
        }

        List<UserInfo> users = getAllUsersFromJson();

        boolean allSuccess = true;
        for (UserInfo user : users) {
            try {
                alertSender.send(user.id(), user.name(), "[kafka] messsage 테스트");

                // ✨ 추가: 성공 시 메트릭 수집
                metrics.incrementSuccess();

            } catch (Exception e) {
                allSuccess = false;
                log.warn("⚠️ 알림 전송 실패: userId={}, {}", user.id(), e.getMessage());

                // ✨ 추가: 실패 시 메트릭 수집
                metrics.incrementFailure();

                // ✨ 추가: 재시도 증가
                notification = notification.increaseRetryCount();
                metrics.incrementRetry();

                // 최대 재시도 초과 시 DLQ 처리
                if (notification.getRetryCount() >= 5) {
                    notification = notification.markAsFailed();

                    // ✨ 추가: DLQ 메트릭 수집
                    metrics.incrementDLQ();

                    log.error("🔴 DLQ 이동: notificationId={}", notification.getId());
                }
            }
        }

        if (allSuccess) {
            notification = notification.markAsSent();
        }
        notificationRepository.save(notification);
    }
}
```

### ✅ 완료 조건
- [ ] NotificationService에 메트릭 수집 코드 추가
- [ ] 컴파일 에러 없이 빌드 성공

---

## 🚀 STEP 4: 테스트 및 확인

### 📝 확인 순서

#### 1. 서비스 실행
```bash
cd livecommerce-backend/notification
./gradlew bootRun
```

#### 2. Actuator 엔드포인트 확인
브라우저 또는 curl로 접근:
```bash
curl http://localhost:19110/actuator/prometheus
```

**확인할 내용:**
```
# HELP notification_sent_total 알림 전송 성공 횟수
# TYPE notification_sent_total counter
notification_sent_total{application="notification-service",status="success"} 0.0

# HELP notification_sent_total 알림 전송 실패 횟수
# TYPE notification_sent_total counter
notification_sent_total{application="notification-service",status="failure"} 0.0

# HELP notification_retry_total 알림 재시도 횟수
# TYPE notification_retry_total counter
notification_retry_total{application="notification-service"} 0.0

# HELP notification_dlq_total DLQ로 이동한 메시지 수
# TYPE notification_dlq_total counter
notification_dlq_total{application="notification-service"} 0.0
```

#### 3. 알림 발송 테스트
```bash
# 알림 등록
curl -X POST http://localhost:19110/api/v1/notifications/broadcasts \
  -H "Content-Type: application/json" \
  -d '{
    "notificationType": "LIVE_BROADCAST",
    "targetId": "550e8400-e29b-41d4-a716-446655440001",
    "scheduledAt": "2026-01-21T19:00:00"
  }'

# Kafka 트리거 (알림 전송)
curl -X POST http://localhost:19110/api/v1/notifications/trigger-kafka-notifications
```

#### 4. 메트릭 변화 확인
다시 `/actuator/prometheus` 접근해서 숫자가 증가했는지 확인
```
notification_sent_total{application="notification-service",status="success"} 3.0
```

#### 5. Grafana 대시보드에서 확인
- Grafana 접속: `http://localhost:3000`
- Query 예시:
  ```promql
  rate(notification_sent_total{status="success"}[5m])  # 5분간 초당 성공률
  notification_dlq_total  # 현재 DLQ 메시지 수
  ```

### ✅ 완료 조건
- [ ] `/actuator/prometheus`에서 커스텀 메트릭 확인
- [ ] 알림 전송 후 메트릭 숫자 증가 확인
- [ ] Grafana에서 그래프로 확인 (선택사항)

---

## 📊 예상 결과

### Prometheus 쿼리 예시
```promql
# 총 알림 전송 수
sum(notification_sent_total)

# 성공률 (%)
sum(notification_sent_total{status="success"}) / sum(notification_sent_total) * 100

# 최근 5분간 초당 실패율
rate(notification_sent_total{status="failure"}[5m])

# DLQ에 쌓인 메시지 수
notification_dlq_total
```

### Grafana 대시보드 패널 구성
1. **알림 전송 현황** (게이지)
   - 성공, 실패, 재시도 건수

2. **시간별 전송 추이** (라인 그래프)
   - 시간에 따른 성공/실패 변화

3. **DLQ 알림** (싱글 스탯)
   - DLQ 메시지 수 + 임계치 경고

---

## 🎯 다음 단계 (선택사항)

### 1. 더 세밀한 메트릭 추가
- 알림 타입별 통계 (`tag("type", "LIVE_BROADCAST")`)
- 실패 원인별 분류 (`tag("reason", "kafka_down")`)

### 2. Histogram 추가
- 알림 전송 소요 시간 측정
```java
Timer.builder("notification.send.duration")
     .description("알림 전송 소요 시간")
     .register(registry);
```

### 3. Alert Rule 설정
- DLQ 메시지 10건 초과 시 Slack 알림
- 실패율 50% 초과 시 경고

---

## 📚 참고 자료

- [Spring Boot Actuator 공식 문서](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer 공식 문서](https://micrometer.io/docs)
- [Prometheus 쿼리 가이드](https://prometheus.io/docs/prometheus/latest/querying/basics/)

---

**작성일**: 2026-01-21
**작성자**: Claude + 사용자
**버전**: 1.0
