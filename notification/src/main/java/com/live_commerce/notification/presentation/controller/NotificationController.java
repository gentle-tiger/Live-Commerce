package com.live_commerce.notification.presentation.controller;

import com.live_commerce.notification.application.service.NotificationService;
import com.live_commerce.notification.infrastructure.common.ResponseUtil;
import com.live_commerce.notification.presentation.common.ApiResponse;
import com.live_commerce.notification.presentation.dto.request.NotificationCreateRequest;
import com.live_commerce.notification.presentation.dto.response.NotificationCreateResponse;
import com.live_commerce.notification.presentation.dto.response.ReadNotificationListResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationService notificationService;

  // 방송 알림 등록
  @PostMapping("/broadcasts")
  public ResponseEntity<ApiResponse<NotificationCreateResponse>> createNotificationForLiveBroadcast(
      @Valid @RequestBody NotificationCreateRequest request) {
    NotificationCreateResponse response = notificationService.createNotificationForLiveBroadcast(
        request);
    return ResponseUtil.success(response);
  }

  // 알림 목록 조회
  @GetMapping
  public ResponseEntity<ApiResponse<ReadNotificationListResponse>> getAllNotifications() {
    ReadNotificationListResponse response = notificationService.getAllNotifications();
    return ResponseUtil.success(response);
  }

  @DeleteMapping("/{targetId}")
  public ResponseEntity<ApiResponse<String>> deleteNotification(@PathVariable UUID targetId){

    notificationService.deleteNotification(targetId);
    return ResponseUtil.success("알림 삭제가 성공적으로 완료되었습니다.");
  }

  /**
   * ⚠️ 테스트/데모 전용 엔드포인트 (운영 발송 경로 아님).
   *
   * <p>운영에서는 NotificationService의 @Scheduled(fixedDelay = 60_000)가 만기 레코드를 조회해
   * Kafka로 발행하고, Consumer가 실제 발송한다. 이 API는 그 스케줄러 주기를 기다리지 않고
   * 발행→소비 경로를 즉시 태우기 위한 수동 트리거이며, 부하 테스트에서 발행 시점을 통제할 때 쓴다.
   *
   * <p>과거에 함께 있던 POST /trigger-scheduled-notifications(= DB Polling 후 동기 직접 발송)는
   * Kafka 경로와 같은 레코드를 집어가 중복 발송 위험이 있어 경로째로 제거했다.
   */
  @PostMapping("/trigger-kafka-notifications")
  public ResponseEntity<ApiResponse<String>> triggerKafkaNotifications() throws IOException {
   notificationService.triggerKafkaNotifications();
   return ResponseUtil.success("kafka 알림 발생 정상 트리거 성공");
  }


}
