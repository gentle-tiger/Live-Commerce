package com.live_commerce.notification.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.live_commerce.notification.application.alert.AlertSender;
import com.live_commerce.notification.infrastructure.kafka.event.NotificationCreatedEvent;
import com.live_commerce.notification.domain.model.Notification;
import com.live_commerce.notification.domain.repository.NotificationRepository;
import com.live_commerce.notification.infrastructure.kafka.producer.NotificationEventProducer;
import com.live_commerce.notification.presentation.dto.request.NotificationCreateRequest;
import com.live_commerce.notification.presentation.dto.request.UserInfo;
import com.live_commerce.notification.presentation.dto.response.NotificationCreateResponse;
import com.live_commerce.notification.presentation.dto.response.NotificationResponse;
import com.live_commerce.notification.presentation.dto.response.ReadNotificationListResponse;

// ✨ 추가: Prometheus 메트릭 수집을 위한 import
import com.live_commerce.notification.infrastructure.metrics.NotificationMetrics;
import io.micrometer.core.instrument.Timer;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 알림 발송은 Kafka 단일 경로로만 동작한다.
 *
 * <p>스케줄러가 만기(scheduledAt <= now) 미발송 레코드를 조회해 notification-created 토픽으로 발행하고,
 * 실제 발송은 {@code NotificationEventConsumer} → {@link #processByMessage} 에서만 일어난다.
 * 과거에 존재하던 "DB Polling 후 즉시 동기 발송" 경로(checkScheduledNotifications →
 * ConsoleAlertSender)는 같은 레코드를 두 경로가 동시에 집어가 중복 발송이 가능해 제거했다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final NotificationRepository notificationRepository;

  private final NotificationEventProducer producer;
  private final ObjectMapper objectMapper;
  private final AlertSender alertSender;

  // ✨ 추가: Prometheus 메트릭 수집을 위한 NotificationMetrics 주입
  // @RequiredArgsConstructor가 자동으로 생성자 주입 처리
  private final NotificationMetrics metrics;

  public NotificationCreateResponse createNotificationForLiveBroadcast(
      NotificationCreateRequest request) {

    if (notificationRepository.existsByTargetId(request.targetId())) {
      throw new IllegalStateException("해당 알림은 이미 등록되었습니다.");
    }
    Notification notification = Notification.reserve(
        request.notificationType(),
        request.targetId(),
        request.scheduledAt()
    );

    notificationRepository.save(notification);
    log.info("라이브 방송 알림 등록 완료");
    return NotificationCreateResponse.from(notification);
  }

  public ReadNotificationListResponse getAllNotifications() {
    List<Notification> notifications = notificationRepository.findAll();
    List<NotificationResponse> responseList = notifications.stream()
        .map(NotificationResponse::from)
        .toList();
    return new ReadNotificationListResponse(responseList);
  }

  /**
   * 발송 대상 사용자 목록.
   *
   * <p>TODO: resources/data/users.json 고정 파일 의존을 걷어내고 실제 구독자를 조회해야 한다.
   * 지금 남겨둔 이유는 구독자 조회 경로({@code LiveBroadcastClient#getSubscribersWithTitle})가
   * livebroadcast 서비스 기동을 전제로 해서, 이 서비스 단독으로는 알림 경로를 재현/부하측정할 수
   * 없기 때문이다. 알림 경로 정리(Kafka 단일화)와 구독자 연동은 서로 독립적인 변경이라,
   * 경로부터 확정한 뒤 별도로 교체한다.
   */
  public List<UserInfo> getAllUsersFromJson() throws IOException {
    File file = new File(getClass().getClassLoader().getResource("data/users.json").getFile());
    return objectMapper.readValue(file, new TypeReference<List<UserInfo>>() {
    });
  }

  public void deleteNotification(UUID targetId) {
    Notification notification = notificationRepository.findByTargetIdAndDeletedStatusFalse(targetId)
        .orElseThrow(() -> new NoSuchElementException("알림이 존재하지 않습니다."));
    notification.markAsDeleted(String.valueOf(notification.getTargetId()));
    notificationRepository.save(notification);
  }

  /**
   * 테스트/데모 전용 수동 트리거. 운영 발송은 {@link #publishScheduledNotifications()}의 스케줄러가 담당한다.
   *
   * <p>용도: 스케줄러 주기(60초)를 기다리지 않고 발행→소비 경로를 즉시 확인하기 위한 것.
   * 부하 테스트에서 발행 시점을 통제할 때도 이 경로를 쓴다. 비즈니스 로직은 스케줄러와 완전히 동일하다.
   */
  public void triggerKafkaNotifications() throws IOException {
    publishScheduledNotifications();
  }

  /**
   * 만기된 미발송 알림을 조회해 Kafka로 발행한다. 실제 발송은 Consumer가 수행한다.
   * fixedDelay: 이전 실행이 끝난 뒤 60초 후 재실행(발행이 밀릴 때 중첩 실행 방지).
   */
  @Scheduled(fixedDelay = 60_000)
  public void publishScheduledNotifications() {
    List<Notification> list = notificationRepository.findAllByScheduledAtLessThanEqualAndIsSentFalse(
        LocalDateTime.now());
    for (Notification n : list) {
      NotificationCreatedEvent msg = new NotificationCreatedEvent(
          n.getId(), n.getType(), n.getTargetId(), n.getScheduledAt()
      );
      producer.sendNotificationCreated(msg);
    }
  }

  /**
   * Kafka 메시지를 받아서 실제 알림을 전송하는 메서드
   *
   * 📊 메트릭 수집:
   * - 알림 전송 성공 시: metrics.incrementSuccess()
   * - 알림 전송 실패 시: metrics.incrementFailure()
   * - 재시도 발생 시: metrics.incrementRetry()
   * - DLQ 이동 시: metrics.incrementDLQ()
   * - 메시지 1건 처리 전체 소요시간: notification.consume (Timer)
   * - 수신자 1명 발송 소요시간: notification.send (Timer)
   *
   * @param msg Kafka에서 받은 알림 생성 이벤트
   * @throws IOException JSON 파일 읽기 실패 시
   */
  public void processByMessage(NotificationCreatedEvent msg) throws IOException {
    // 📊 발송 지연(P95/P99)의 기준이 되는 구간. 조기 return·예외 모두 finally에서 기록된다.
    Timer.Sample consumeSample = metrics.startConsume();
    try {
      doProcessByMessage(msg);
    } finally {
      metrics.stopConsume(consumeSample);
    }
  }

  private void doProcessByMessage(NotificationCreatedEvent msg) throws IOException {
    Notification notification = notificationRepository.findById(msg.notificationId())
        .orElseThrow(() -> new IllegalArgumentException("알림 없음: " + msg.notificationId()));

    // 이미 전송된 알림은 처리하지 않음 (멱등성 보장)
    if (notification.isSent()) {
      return;
    }

    // users.json에서 테스트 사용자 목록 가져오기
    // TODO: 실제로는 DB에서 구독자 목록을 가져와야 함
    List<UserInfo> users = getAllUsersFromJson();

    boolean allSuccess = true;  // 모든 사용자에게 성공적으로 전송되었는지 여부
    for (UserInfo user : users) {
      try {
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 알림 전송 시도 (📊 notification.send Timer로 구간 계측)
        // 실패해도 소요시간은 기록되고 예외는 아래 catch로 그대로 전파된다.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        metrics.recordSend(() -> alertSender.send(user.id(), user.name(), "[kafka] messsage 테스트"));

        // ✨ 메트릭 수집: 알림 전송 성공
        // Prometheus Counter 1 증가: notification_sent_total{status="success"}
        metrics.incrementSuccess();

        log.info("✅ 알림 전송 성공: userId={}, name={}", user.id(), user.name());

      } catch (Exception e) {
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 알림 전송 실패 처리
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        allSuccess = false;

        // ✨ 메트릭 수집: 알림 전송 실패
        // Prometheus Counter 1 증가: notification_sent_total{status="failure"}
        metrics.incrementFailure();

        log.warn("⚠️ 알림 전송 실패: userId={}, {}", user.id(), e.getMessage());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 재시도 처리
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        notification = notification.increaseRetryCount();  // 재시도 횟수 증가

        // ✨ 메트릭 수집: 재시도 발생
        // Prometheus Counter 1 증가: notification_retry_total
        metrics.incrementRetry();

        log.info("🔄 재시도 횟수 증가: notificationId={}, retryCount={}/5",
            notification.getId(), notification.getRetryCount());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 최대 재시도 횟수 초과 시 DLQ로 이동
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (notification.getRetryCount() >= 5) {
          notification = notification.markAsFailed();  // 실패로 마킹

          // ✨ 메트릭 수집: DLQ로 이동
          // Prometheus Counter 1 증가: notification_dlq_total
          metrics.incrementDLQ();

          log.error("🔴 DLQ 이동: notificationId={}, 최대 재시도 횟수(5회) 초과",
              notification.getId());
        }
      }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 알림 상태 저장
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    if (allSuccess) {
      notification = notification.markAsSent();  // 성공으로 마킹
      log.info("✅ 모든 사용자에게 알림 전송 완료: notificationId={}", notification.getId());
    }
    notificationRepository.save(notification);
  }
}
