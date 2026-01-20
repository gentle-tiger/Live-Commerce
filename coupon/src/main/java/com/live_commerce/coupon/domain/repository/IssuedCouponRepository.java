package com.live_commerce.coupon.domain.repository;

import com.live_commerce.coupon.domain.model.CouponUseStatus;
import com.live_commerce.coupon.domain.model.IssuedCoupon;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface IssuedCouponRepository extends JpaRepository<IssuedCoupon, UUID> {

  /**
   * 특정 사용자가 사용할 수 있는 단일 쿠폰 (use 처리용)
   */
  Optional<IssuedCoupon> findByIdAndUserIdAndStatus(UUID id, UUID userId, CouponUseStatus status);

  /**
   * 특정 사용자(userId)의 “만료 전(expires_at > now) + 미사용(status == ACTIVE)” 목록
   */
  Page<IssuedCoupon> findByUserIdAndStatusAndExpiresAtAfter(
      UUID userId, CouponUseStatus status, LocalDateTime now, Pageable pageable);


  /**
   * [BATCH] 만료 대상 일괄 마킹 쿼리.
   * - 대상: status=ACTIVE 이면서 expires_at <= now()
   * - 이유: 대량 처리 시 엔티티 로딩 없이 한 방 UPDATE가 가장 빠르고 안전(네이티브 SQL 사용)
   * - 트랜잭션: 스케줄러 메서드에서 @Transactional로 호출
   * - JPA 캐시:
   *      - clearAutomatically=true 로 1차 캐시 갱신 보장(일관성), (메서드가 실행되기 직전, 현재 영속성 컨텍스트(1차 캐시)의 변경분을 DB에 먼저 반영함)
   *      - flushAutomatically=true 로 실행 전 flush, (메서드가 성공적으로 실행된 직후, EntityManager.clear()를 호출해 1차 캐시를 비움)
   *                                이전에 영속화돼 있던 IssuedCoupon 엔티티가 캐시에 남아 옛값을 돌려주는 문제를 막아줌.
   * - 인덱스: (expires_at) WHERE status='ACTIVE' 부분 인덱스 또는 (status, expires_at) 복합 인덱스 권장
   * - 반환: EXPIRED로 전이된 행 수(로그/메트릭 집계에 사용)
   * - 스키마: default_schema=coupons 설정이면 테이블명만, 아니면 coupons.p_issued_coupon로 완전수식

   * 작은 주의사항
   * clearAutomatically=true는 해당 쿼리 직후 전체 1차 캐시를 비웁니다. 같은 트랜잭션에서 이어지는 로직이 많다면, 필요한 엔티티는 다시 조회해야 해요. (대신 stale 문제는 없어짐)
   * 스키마가 coupons라면 테이블명을 coupons.p_issued_coupon로 완전수식하거나, Spring JPA의 default_schema=coupons 설정을 유지하세요.
   * 트랜잭션은 필수, flush/clear는 보이는 효과가 확실하고, 벌크 UPDATE는 DB에 맡겨 한 번에 갱신한다는 뜻이에요.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
        UPDATE p_issued_coupon
            SET status = 'EXPIRED'
        WHERE status = 'ACTIVE'
            AND expires_at <= now()
      """, nativeQuery = true)
  int bulkExpireActiveBeforeNow();



  @Deprecated
  Optional<IssuedCoupon> findByIdAndUserIdAndIsUsedFalse(UUID couponId, UUID userId);

  @Deprecated
  List<IssuedCoupon> findByUserId(UUID userId);

  Optional<IssuedCoupon> findByIdAndUserId(UUID couponId, UUID userId);
}
