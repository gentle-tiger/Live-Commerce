# Live Commerce 서비스 자동 실행 가이드

이 가이드는 Live Commerce 멀티 모듈 프로젝트의 모든 서비스를 자동으로 실행하는 방법을 설명합니다.

## 개요

기존에는 각 서비스를 수동으로 5번 이상 클릭해서 실행해야 했지만, 이제 **한 번의 명령**으로 모든 서비스를 순차적으로 실행할 수 있습니다.

### 실행 순서

```
Docker Compose (인프라)
    ↓
Config Server (설정 관리)
    ↓
Eureka Server (서비스 디스커버리)
    ↓
Gateway (API 게이트웨이)
    ↓
User Service
    ↓
Coupon Service
```

## 통합 스크립트

**하나의 스크립트**로 모든 서비스 관리:

```powershell
.\services.ps1 <command> [options]
```

| 명령어 | 설명 |
|--------|------|
| `start` | 모든 서비스 시작 |
| `stop` | 모든 서비스 종료 |
| `restart` | 모든 서비스 재시작 |
| `test-coupon` | 쿠폰 동시성 테스트 |
| `help` | 도움말 표시 |

## 사용 방법

### 1. 전체 서비스 시작

```powershell
.\services.ps1 start
```

**실행 내용:**
- Docker Compose로 인프라 서비스 시작 (PostgreSQL, Redis, Kafka 등)
- Config → Eureka → Gateway → User → Coupon 순차 실행
- 각 서비스의 헬스체크 대기
- 로그를 `logs/` 폴더에 저장

### 2. 특정 서비스 스킵

Docker가 이미 실행 중이면 스킵:
```powershell
.\services.ps1 start -SkipDocker
```

여러 서비스 스킵:
```powershell
.\services.ps1 start -SkipDocker -SkipConfig -SkipEureka
```

**사용 가능한 옵션:**
- `-SkipDocker` : Docker Compose 실행 스킵
- `-SkipConfig` : Config Server 스킵
- `-SkipEureka` : Eureka Server 스킵
- `-SkipGateway` : Gateway 스킵
- `-SkipUser` : User Service 스킵
- `-SkipCoupon` : Coupon Service 스킵

### 3. 서비스 종료

모든 서비스 종료:
```powershell
.\services.ps1 stop
```

Docker는 유지하고 Spring Boot만 종료:
```powershell
.\services.ps1 stop -KeepDocker
```

Java 프로세스 강제 종료:
```powershell
.\services.ps1 stop -Force
```

### 4. 서비스 재시작

```powershell
.\services.ps1 restart
```

Docker는 유지하고 재시작:
```powershell
.\services.ps1 restart -KeepDocker
```

### 5. 쿠폰 동시성 테스트 (신규!)

50개 동시 요청으로 Exactly-Once 보장 검증:

```powershell
# 쿠폰 ID 설정
$couponId = "80ffdaa4-f2e5-4693-9472-0182c86f5021"

# 테스트 실행
.\services.ps1 test-coupon -CouponId $couponId
```

요청 수 변경:
```powershell
.\services.ps1 test-coupon -CouponId $couponId -Requests 100
```

**예상 결과:**
- 200 OK: 정확히 1개 ✅
- 403/409: 나머지 전부 (충돌 처리) ✅

## 로그 확인

### 로그 파일 위치
각 서비스의 로그는 `logs/` 폴더에 저장됩니다:
```
logs/
├── config.log
├── eureka.log
├── gateway.log
├── user.log
└── coupon.log
```

### 실시간 로그 보기

PowerShell에서 실시간으로 로그 확인:
```powershell
Get-Content logs/coupon.log -Wait
```

### Job 상태 확인

실행 중인 Job 목록:
```powershell
Get-Job
```

특정 Job의 출력 보기:
```powershell
Receive-Job -Id <JobId>
```

Job 종료:
```powershell
Stop-Job -Id <JobId>
Remove-Job -Id <JobId>
```

## 서비스 포트

| 서비스 | 포트 | URL |
|--------|------|-----|
| Config Server | 18080 | http://localhost:18080 |
| Eureka Server | 19090 | http://localhost:19090 |
| Gateway | 19091 | http://localhost:19091 |
| User Service | 19120 | http://localhost:19120 |
| Coupon Service | 19100 | http://localhost:19100 |
| PostgreSQL | 5432 | localhost:5432 |
| Redis | 6379 | localhost:6379 |
| Kafka | 9092 | localhost:9092 |
| Kafka UI | 8080 | http://localhost:8080 |
| Zipkin | 9411 | http://localhost:9411 |
| Prometheus | 9090 | http://localhost:9090 |
| Grafana | 3000 | http://localhost:3000 |

## 트러블슈팅

### 문제: 스크립트 실행 권한 오류

**에러:**
```
이 시스템에서 스크립트를 실행할 수 없으므로...
```

**해결:**
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### 문제: Docker 실행 실패

**에러:**
```
[Docker] 실행 실패. Docker Desktop이 실행 중인지 확인하세요.
```

**해결:**
1. Docker Desktop 실행
2. 또는 `-SkipDocker` 옵션 사용

### 문제: 서비스 시작 타임아웃

**에러:**
```
[Eureka Server] 서비스 시작 타임아웃!
```

**해결:**
1. 로그 파일 확인: `logs/eureka.log`
2. 포트 충돌 확인: `netstat -ano | findstr :19090`
3. Gradle 캐시 삭제 후 재시도:
   ```powershell
   .\gradlew.bat clean
   .\services.ps1 start
   ```

### 문제: 포트 충돌

**해결:**
```powershell
# 특정 포트를 사용 중인 프로세스 찾기
netstat -ano | findstr :<포트번호>

# 프로세스 종료
taskkill /PID <프로세스ID> /F
```

### 문제: Gradle 데몬 문제

**해결:**
```powershell
.\gradlew.bat --stop
.\services.ps1 start
```

## PC 재부팅 후 사용

PC를 재부팅한 후:

```powershell
# 1. Docker Desktop 실행 (자동 실행 설정 권장)

# 2. 스크립트 실행
.\services.ps1 start
```

**Tip:** Docker Desktop을 시작 프로그램에 등록하면 PC 재부팅 시 자동 실행됩니다.

## 개발 워크플로우 예시

### 일반적인 작업 시작
```powershell
# 전체 서비스 시작
.\services.ps1 start

# 작업...

# 종료
.\services.ps1 stop -KeepDocker
```

### Coupon 서비스만 재시작
```powershell
# 1. Coupon Job만 종료
Get-Job | Where-Object { $_.Name -like "*coupon*" } | Stop-Job
Get-Job | Where-Object { $_.Name -like "*coupon*" } | Remove-Job

# 2. Coupon만 다시 시작
.\gradlew.bat :coupon:bootRun
```

### 인프라만 유지하고 Spring Boot만 재시작
```powershell
.\services.ps1 stop -KeepDocker
.\services.ps1 start -SkipDocker
```

## 추가 팁

### 1. PowerShell 함수 설정

PowerShell 프로필에 함수 추가:
```powershell
# PowerShell 프로필 편집
notepad $PROFILE

# 아래 내용 추가 (경로는 본인 환경에 맞게 수정)
$ServicesScript = "C:\path\to\project\services.ps1"
function lc-start { & $ServicesScript start @args }
function lc-stop { & $ServicesScript stop @args }
function lc-restart { & $ServicesScript restart @args }
function lc-test { & $ServicesScript test-coupon @args }
```

사용:
```powershell
lc-start
lc-stop -KeepDocker
lc-restart
lc-test -CouponId "쿠폰ID"
```

### 2. Windows 터미널 바로가기

Windows 터미널 설정에서 프로젝트 디렉토리를 기본 경로로 설정하면 더 편리합니다.

### 3. 자동 시작 배치 파일

PC 시작 시 자동 실행하려면:
```batch
@echo off
start /min powershell -ExecutionPolicy Bypass -File "C:\...\services.ps1" start
```

## 빠른 참조

### 자주 쓰는 명령어
```powershell
# 일반 작업 시작
.\services.ps1 start

# Docker는 이미 실행 중
.\services.ps1 start -SkipDocker

# 전체 종료 (Docker 포함)
.\services.ps1 stop

# Spring Boot만 종료 (Docker 유지)
.\services.ps1 stop -KeepDocker

# 재시작
.\services.ps1 restart -KeepDocker

# 쿠폰 동시성 테스트
$cid = "쿠폰ID"
.\services.ps1 test-coupon -CouponId $cid

# 도움말
.\services.ps1 help
```

### 전체 명령어 옵션

**start/restart 옵션:**
- `-SkipDocker`, `-SkipConfig`, `-SkipEureka`, `-SkipGateway`, `-SkipUser`, `-SkipCoupon`

**stop 옵션:**
- `-KeepDocker` : Docker 유지
- `-Force` : Java 프로세스 강제 종료

**test-coupon 옵션:**
- `-CouponId` : 쿠폰 ID (필수)
- `-UserId` : 사용자 ID (기본값: 61b130f2-...)
- `-Requests` : 동시 요청 수 (기본값: 50)

## 문의 및 개선

스크립트 개선 사항이나 버그는 팀에 공유해주세요!
