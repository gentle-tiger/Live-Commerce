package com.live_commerce.coupon.domain.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "coupon_outbox")
@Getter
//@Setter // 사용 시 주석 해제
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponOutbox {


  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String aggregateType;

  @Column(nullable = false)
  private String aggregateId; // UUID string

  @Column(nullable = false)
  private String eventType;

  @Column(columnDefinition = "jsonb", nullable = false)
  private String payload;

  @Column(columnDefinition = "jsonb")
  private String headers;

  @Column(nullable = false)
  private LocalDateTime occurredAt;

  private LocalDateTime publishedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OutboxStatus status;

  @Column(nullable = false)
  private Integer attempts;

  @Column(nullable = false)
  private String partitionKey;

  @Lob // 뭐지?
  private String errorMessage;


  public static CouponOutbox pending(
      String aggregateType,
      String aggregateId,
      String eventType,
      String payload,
      String headers,
      String partitionKey
  ) {
    return CouponOutbox.builder()
        .aggregateType(aggregateType)
        .aggregateId(aggregateId)
        .eventType(eventType)
        .payload(payload)
        .headers(headers)
        .occurredAt(LocalDateTime.now())
        .status(OutboxStatus.PENDING) // default
        .attempts(0)
        .partitionKey(partitionKey).build();
  }
}
