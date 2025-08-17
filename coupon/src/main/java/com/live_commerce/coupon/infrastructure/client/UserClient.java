package com.live_commerce.coupon.infrastructure.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user", url = "${gateway.base-url}", path = "/api/v1")
public interface UserClient {
 // @GetMapping("/users/{userId}") // 경로 변수 반영해야하는데, 일단은 보류(이전 코드에서 돌아간 거 같아서)
  @GetMapping("/users")
  UUID getUserId(@PathVariable UUID userId);
}
