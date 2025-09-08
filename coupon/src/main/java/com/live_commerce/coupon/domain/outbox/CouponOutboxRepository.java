package com.live_commerce.coupon.domain.outbox;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CouponOutboxRepository extends JpaRepository<CouponOutbox, Long> {

  /**
   * 'FOR UPDATE SKIP LOCKED'는 비관적 락의 한 종류로, 데이터를 잠그는 방식으로 다른 트랜잭션이 데이터를 수정하지 못하도록 합니다.
   * 이 방식은 **배치 락(batch lock)**으로 사용되며, 여러 프로세스가 동시에 `coupon_outbox` 테이블의 `PENDING` 상태 레코드를 처리할 때
   * **중복 처리를 방지**하고, 각 레코드는 **하나의 프로세스만 처리**하게 됩니다.
   *
   * `LIMIT :limit`: 한 번에 처리할 **최대 레코드 수**를 설정합니다. 이를 통해 배치 크기를 제어할 수 있습니다.
   * `FOR UPDATE SKIP LOCKED`: 이미 잠금이 걸린 레코드는 **건너뛰고**, 잠금이 없는 레코드만 선택하여 처리합니다.
   */
  @Query(value = """
      SELECT *
          FROM coupons.coupon_outbox
      WHERE status = 'PENDING'
      ORDER BY occurred_at
      LIMIT :limit -- 한번에 처리할 최대 레코드 수
      FOR UPDATE SKIP LOCKED -- 잠금된 레코드는 건너뛰고, 처리할 수 있는 레코드만 선택
      """, nativeQuery = true)
  List<CouponOutbox> lockNextBatch(@Param("limit") int limit);
}
