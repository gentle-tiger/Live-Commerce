package com.live_commerce.coupon.infrastructure.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class RedisCacheConfig {

  @Bean
  public RedisCacheConfiguration redisCacheConfiguration() {

    // Redis 캐시 기본 구성
    // - key 직렬화: StringRedisSerializer → 사람이 읽기 쉬운 문자열 키
    // - value 직렬화: GenericJackson2JsonRedisSerializer(om) → DTO를 JSON으로 저장(JDK 직렬화 불필요)
    // - null 값 캐싱 금지: disableCachingNullValues() → 불필요한 null 엔트리 방지
    //   (참고) TTL 등은 application.yml의 spring.cache.redis.time-to-live 로 관리 가능
    return RedisCacheConfiguration.defaultCacheConfig()
        .serializeKeysWith(RedisSerializationContext.SerializationPair
            .fromSerializer(new StringRedisSerializer()))
        .serializeValuesWith(RedisSerializationContext.SerializationPair
            .fromSerializer(new GenericJackson2JsonRedisSerializer()))
        .disableCachingNullValues();

  }
}
