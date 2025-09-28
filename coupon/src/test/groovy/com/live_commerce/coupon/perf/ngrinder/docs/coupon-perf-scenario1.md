# 쿠폰 서비스 nGrinder 시나리오 1 실행 가이드

## 목적
- `CouponUseConflictSimulation.groovy` 스크립트를 이용해 **Exactly-once** 쿠폰 사용 보장을 nGrinder에서 재현합니다.
- IDE에서 스크립트의 import 인식 문제 없이 개발하고, 로컬에서 JAR 없이 `:coupon:perf` 모듈만 가져와 테스트할 수 있도록 합니다.

## 사전 준비
1. **인프라 기동**: `docker compose up -d postgres redis zookeeper kafka`로 의존 인프라를 올립니다.
2. **구성 서비스 실행**: Config Server(18080) → Eureka(19090) 순으로 구동합니다.
3. **쿠폰 서비스 실행**: `coupon` 모듈을 `./gradlew :coupon:bootRun` 등으로 띄워 REST API를 준비합니다.
4. **모니터링 확인**: Prometheus/Grafana가 기동 중인지 확인하고 관련 대시보드에서 `coupon_used_total`, `coupon_use_conflict_total` 패널을 모니터링합니다.

## IDE 설정
- 루트 프로젝트를 임포트하면 `:coupon:perf` 서브모듈이 자동으로 추가됩니다.
- `coupon/perf/ngrinder` 디렉터리는 Groovy SourceSet으로 선언되어 있으므로, IntelliJ 기준으로 별도 수동 설정 없이 자동 완성과 컴파일 하이라이트가 동작합니다.

## Gradle 작업
- 스크립트 정적 분석: `./coupon/gradlew :coupon:perf:compileGroovy`
- 의존성 확인: `./coupon/gradlew :coupon:perf:dependencies`

> ⚠️ 샌드박스 환경에서는 Gradle 배포본 다운로드가 제한될 수 있으니, 내부 네트워크에서 실행할 때 Gradle wrapper가 인터넷에 접근 가능한지 확인하세요.

## nGrinder에 업로드
1. nGrinder 콘솔에서 **Script → Add**로 이동합니다.
2. `coupon/perf/ngrinder/CouponUseConflictSimulation.groovy` 파일을 업로드합니다.
3. `BASE_URL`, `COUPON_ID`, `ACCESS_TOKEN` 시스템 프로퍼티를 시나리오 환경에 맞게 설정합니다.
4. VUser=50, Run count=1, Ramp-up=5s로 테스트를 실행합니다.

## 기대 결과 & 체크포인트
- 성공 1건, 충돌 49건 수준으로 로그와 메트릭이 기록됩니다.
- Grafana의 `Conflicts/s`, `Retries/s`, `Success Ratio` 패널에서 경합 현상이 시각화됩니다.
- `increase(coupon_used_total[1m])` ≈ 1, `increase(coupon_use_conflict_total[1m])` ≈ 49를 확인합니다.
- DB에서 대상 쿠폰이 한 번만 사용 완료 상태인지 검증합니다.

이 가이드는 README를 수정하지 않고도 쿠폰 부하 테스트를 빠르게 수행할 수 있도록 돕습니다.
