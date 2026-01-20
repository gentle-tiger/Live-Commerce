# Live Commerce Services Management Script
# 모든 서비스 관리를 하나의 스크립트로 통합

param(
    [Parameter(Position=0)]
    [ValidateSet("start", "stop", "restart", "test-coupon", "help")]
    [string]$Command = "help",

    # start/restart 옵션
    [switch]$SkipDocker,
    [switch]$SkipConfig,
    [switch]$SkipEureka,
    [switch]$SkipGateway,
    [switch]$SkipUser,
    [switch]$SkipCoupon,

    # stop 옵션
    [switch]$KeepDocker,
    [switch]$Force,

    # test-coupon 옵션
    [string]$CouponId,
    [string]$UserId = "61b130f2-d4d6-4eeb-b3e3-b9504ea9d752",
    [int]$Requests = 50
)

$ErrorActionPreference = "Stop"
$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path

#region 공통 함수

function Write-ColorOutput($ForegroundColor, $Message) {
    Write-Host $Message -ForegroundColor $ForegroundColor
}

function Load-EnvFile {
    param([string]$EnvFilePath)

    if (Test-Path $EnvFilePath) {
        Write-ColorOutput Cyan "[ENV] .env 파일 로드 중..."

        Get-Content $EnvFilePath | ForEach-Object {
            $line = $_.Trim()
            if ($line -eq "" -or $line.StartsWith("#")) { return }

            if ($line -match "^([^=]+)=(.*)$") {
                $key = $matches[1].Trim()
                $value = $matches[2].Trim() -replace "^[`"']|[`"']$", ""
                [Environment]::SetEnvironmentVariable($key, $value, "Process")
                Write-ColorOutput Gray "  - $key 설정됨"
            }
        }
        Write-ColorOutput Green "[ENV] 환경 변수 로드 완료!`n"
    }
}

function Test-TcpPort {
    param([string]$HostName, [int]$Port, [int]$TimeoutMs = 2000)

    try {
        $tcpClient = New-Object System.Net.Sockets.TcpClient
        $connection = $tcpClient.BeginConnect($HostName, $Port, $null, $null)
        $wait = $connection.AsyncWaitHandle.WaitOne($TimeoutMs, $false)

        if ($wait) {
            try {
                $tcpClient.EndConnect($connection)
                $tcpClient.Close()
                return $true
            } catch { return $false }
        } else {
            $tcpClient.Close()
            return $false
        }
    } catch { return $false }
}

function Wait-ForService {
    param([string]$ServiceName, [string]$Url, [int]$MaxRetries = 30, [int]$RetryDelay = 5)

    Write-ColorOutput Yellow "[$ServiceName] 서비스 시작 대기 중..."

    for ($i = 1; $i -le $MaxRetries; $i++) {
        try {
            $response = Invoke-WebRequest -Uri $Url -Method Get -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($response.StatusCode -eq 200) {
                Write-ColorOutput Green "[$ServiceName] 서비스가 준비되었습니다!"
                return $true
            }
        } catch { }

        Write-ColorOutput Gray "[$ServiceName] 대기 중... ($i/$MaxRetries)"
        Start-Sleep -Seconds $RetryDelay
    }

    Write-ColorOutput Red "[$ServiceName] 서비스 시작 타임아웃!"
    return $false
}

function Wait-ForTcpPort {
    param([string]$ServiceName, [string]$HostName, [int]$Port, [int]$MaxRetries = 30, [int]$RetryDelay = 3)

    Write-ColorOutput Yellow "[$ServiceName] 포트 $Port 연결 대기 중..."

    for ($i = 1; $i -le $MaxRetries; $i++) {
        if (Test-TcpPort -HostName $HostName -Port $Port) {
            Write-ColorOutput Green "[$ServiceName] 포트 $Port 연결 성공!"
            return $true
        }

        Write-ColorOutput Gray "[$ServiceName] 포트 대기 중... ($i/$MaxRetries)"
        Start-Sleep -Seconds $RetryDelay
    }

    Write-ColorOutput Red "[$ServiceName] 포트 연결 타임아웃!"
    return $false
}

function Wait-ForDockerInfrastructure {
    Write-ColorOutput Cyan "`n=== [Docker] 인프라 서비스 준비 상태 확인 중 ==="

    Write-ColorOutput Yellow "`n[Docker] 서비스 초기화 대기 중..."
    for ($i = 1; $i -le 5; $i++) {
        Write-Host "$i " -NoNewline -ForegroundColor Cyan
        Start-Sleep -Seconds 1
    }
    Write-Host ""

    $services = @(
        @{Name = "PostgreSQL"; HostName = "localhost"; Port = 5432},
        @{Name = "Redis"; HostName = "localhost"; Port = 6379},
        @{Name = "Zookeeper"; HostName = "localhost"; Port = 2181},
        @{Name = "Kafka"; HostName = "localhost"; Port = 9092},
        @{Name = "Zipkin"; Url = "http://localhost:9411/health"},
        @{Name = "Prometheus"; Url = "http://localhost:9090/-/healthy"},
        @{Name = "Grafana"; Url = "http://localhost:3000/api/health"}
    )

    $allReady = $true
    foreach ($service in $services) {
        if ($service.Port) {
            $ready = Wait-ForTcpPort -ServiceName $service.Name -HostName $service.HostName -Port $service.Port -MaxRetries 20 -RetryDelay 3
        } else {
            $ready = Wait-ForService -ServiceName $service.Name -Url $service.Url -MaxRetries 20 -RetryDelay 3
        }

        if (-not $ready) {
            Write-ColorOutput Red "[$($service.Name)] 서비스 준비 실패"
            $allReady = $false
        }
    }

    if ($allReady) {
        Write-ColorOutput Green "`n[Docker] 모든 인프라 서비스가 준비되었습니다!"
        return $true
    } else {
        Write-ColorOutput Red "`n[Docker] 일부 인프라 서비스가 준비되지 않았습니다."
        return $false
    }
}

function Start-GradleService {
    param([string]$ServiceName, [string]$Module, [string]$HealthCheckUrl)

    Write-ColorOutput Cyan "`n=== [$ServiceName] 시작 중 ==="

    $logFile = "$SCRIPT_DIR\logs\$Module.log"
    New-Item -ItemType Directory -Force -Path "$SCRIPT_DIR\logs" | Out-Null

    $envVars = @{}
    Get-ChildItem env: | ForEach-Object { $envVars[$_.Name] = $_.Value }

    $job = Start-Job -ScriptBlock {
        param($scriptDir, $module, $logFile, $envVars)

        foreach ($key in $envVars.Keys) {
            [Environment]::SetEnvironmentVariable($key, $envVars[$key], "Process")
        }

        Set-Location $scriptDir
        & "$scriptDir\gradlew.bat" :$module`:bootRun *>&1 | Tee-Object -FilePath $logFile
    } -ArgumentList $SCRIPT_DIR, $Module, $logFile, $envVars

    Write-ColorOutput Green "[$ServiceName] 백그라운드에서 실행 중 (Job ID: $($job.Id))"
    Write-ColorOutput Gray "로그 파일: $logFile"

    if ($HealthCheckUrl) {
        $ready = Wait-ForService -ServiceName $ServiceName -Url $HealthCheckUrl
        if (-not $ready) {
            Write-ColorOutput Red "[$ServiceName] 시작 실패. 로그를 확인하세요: $logFile"
            throw "서비스 시작 실패: $ServiceName"
        }
    }
}

#endregion

#region Command Handlers

function Invoke-StartServices {
    Write-ColorOutput Cyan @"
╔═══════════════════════════════════════════════╗
║   Live Commerce Services Auto Starter        ║
╚═══════════════════════════════════════════════╝
"@

    Load-EnvFile -EnvFilePath "$SCRIPT_DIR\.env"

    # 1. Docker
    if (-not $SkipDocker) {
        Write-ColorOutput Cyan "`n=== [Docker] 인프라 서비스 시작 중 ==="
        try {
            docker compose up -d
            Write-ColorOutput Green "[Docker] Docker Compose 명령이 실행되었습니다."

            $infraReady = Wait-ForDockerInfrastructure
            if (-not $infraReady) {
                throw "Docker 인프라 서비스 준비 실패"
            }
        } catch {
            Write-ColorOutput Red "[Docker] 실행 실패. Docker Desktop이 실행 중인지 확인하세요."
            throw
        }
    } else {
        Write-ColorOutput Yellow "[Docker] 스킵됨 (-SkipDocker)"
    }

    # 2. Config Server
    if (-not $SkipConfig) {
        Start-GradleService -ServiceName "Config Server" -Module "config" -HealthCheckUrl "http://localhost:18080/actuator/health"
    } else { Write-ColorOutput Yellow "[Config] 스킵됨 (-SkipConfig)" }

    # 3. Eureka Server
    if (-not $SkipEureka) {
        Start-GradleService -ServiceName "Eureka Server" -Module "eureka" -HealthCheckUrl "http://localhost:19090/actuator/health"
    } else { Write-ColorOutput Yellow "[Eureka] 스킵됨 (-SkipEureka)" }

    # 4. Gateway
    if (-not $SkipGateway) {
        Start-GradleService -ServiceName "Gateway" -Module "gateway" -HealthCheckUrl "http://localhost:19091/actuator/health"
    } else { Write-ColorOutput Yellow "[Gateway] 스킵됨 (-SkipGateway)" }

    # 5. User Service
    if (-not $SkipUser) {
        Start-GradleService -ServiceName "User Service" -Module "user" -HealthCheckUrl "http://localhost:19120/actuator/health"
    } else { Write-ColorOutput Yellow "[User] 스킵됨 (-SkipUser)" }

    # 6. Coupon Service
    if (-not $SkipCoupon) {
        Start-GradleService -ServiceName "Coupon Service" -Module "coupon" -HealthCheckUrl "http://localhost:19100/actuator/health"
    } else { Write-ColorOutput Yellow "[Coupon] 스킵됨 (-SkipCoupon)" }

    Write-ColorOutput Green @"

╔═══════════════════════════════════════════════╗
║   모든 서비스가 성공적으로 시작되었습니다!    ║
╚═══════════════════════════════════════════════╝

실행 중인 서비스:
- Config Server:  http://localhost:18080
- Eureka Server:  http://localhost:19090
- Gateway:        http://localhost:19091
- User Service:   http://localhost:19120
- Coupon Service: http://localhost:19100

로그 확인: .\logs\ 폴더 참조
서비스 종료: .\services.ps1 stop
Job 확인: Get-Job
"@
}

function Invoke-StopServices {
    $ErrorActionPreference = "Continue"

    Write-ColorOutput Cyan @"
╔═══════════════════════════════════════════════╗
║   Live Commerce Services Stopper             ║
╚═══════════════════════════════════════════════╝
"@

    # 1. Gradle Job 종료
    Write-ColorOutput Yellow "`n=== [Jobs] 실행 중인 Gradle Job 종료 중 ==="
    $jobs = Get-Job | Where-Object { $_.State -eq "Running" }

    if ($jobs.Count -gt 0) {
        foreach ($job in $jobs) {
            Write-ColorOutput Gray "Job ID $($job.Id) 종료 중..."
            Stop-Job -Id $job.Id
            Remove-Job -Id $job.Id -Force
        }
        Write-ColorOutput Green "모든 Gradle Job이 종료되었습니다."
    } else {
        Write-ColorOutput Gray "실행 중인 Job이 없습니다."
    }

    # 2. Gradle 데몬 종료
    Write-ColorOutput Yellow "`n=== [Gradle] Gradle 데몬 종료 중 ==="
    try {
        & "$SCRIPT_DIR\gradlew.bat" --stop
        Write-ColorOutput Green "Gradle 데몬이 종료되었습니다."
    } catch {
        Write-ColorOutput Gray "Gradle 데몬 종료 실패 또는 이미 종료됨."
    }

    # 3. Java 프로세스 강제 종료 (선택)
    if ($Force) {
        Write-ColorOutput Yellow "`n=== [Java] Java 프로세스 강제 종료 중 ==="
        $javaProcesses = Get-Process | Where-Object { $_.ProcessName -like "*java*" }

        if ($javaProcesses.Count -gt 0) {
            foreach ($proc in $javaProcesses) {
                try {
                    Write-ColorOutput Gray "Java 프로세스 $($proc.Id) 종료 중..."
                    Stop-Process -Id $proc.Id -Force
                } catch {
                    Write-ColorOutput Red "프로세스 $($proc.Id) 종료 실패"
                }
            }
            Write-ColorOutput Green "Java 프로세스가 종료되었습니다."
        } else {
            Write-ColorOutput Gray "실행 중인 Java 프로세스가 없습니다."
        }
    }

    # 4. Docker Compose 종료
    if (-not $KeepDocker) {
        Write-ColorOutput Yellow "`n=== [Docker] 인프라 서비스 종료 중 ==="
        try {
            docker compose down
            Write-ColorOutput Green "Docker 서비스가 종료되었습니다."
        } catch {
            Write-ColorOutput Red "Docker 종료 실패. Docker Desktop이 실행 중인지 확인하세요."
        }
    } else {
        Write-ColorOutput Yellow "[Docker] 유지됨 (-KeepDocker)"
    }

    Write-ColorOutput Green @"

╔═══════════════════════════════════════════════╗
║   모든 서비스가 종료되었습니다!              ║
╚═══════════════════════════════════════════════╝
"@
}

function Invoke-RestartServices {
    Write-ColorOutput Cyan @"
╔═══════════════════════════════════════════════╗
║   Live Commerce Services Restarter           ║
╚═══════════════════════════════════════════════╝
"@

    Write-ColorOutput Yellow "`n=== 서비스 종료 중 ==="
    Invoke-StopServices

    Start-Sleep -Seconds 3

    Write-ColorOutput Yellow "`n=== 서비스 시작 중 ==="
    Invoke-StartServices

    Write-ColorOutput Green @"

╔═══════════════════════════════════════════════╗
║   서비스 재시작 완료!                        ║
╚═══════════════════════════════════════════════╝
"@
}

function Invoke-TestCoupon {
    Write-ColorOutput Cyan @"
╔═══════════════════════════════════════════════╗
║   Coupon Concurrency Test                    ║
╚═══════════════════════════════════════════════╝
"@

    if (-not $CouponId) {
        Write-ColorOutput Red "오류: -CouponId 파라미터가 필요합니다."
        Write-ColorOutput Yellow "사용법: .\services.ps1 test-coupon -CouponId <쿠폰ID> [-Requests 50]"
        Write-ColorOutput Yellow "`n예시:"
        Write-ColorOutput Gray '  $couponId = "80ffdaa4-f2e5-4693-9472-0182c86f5021"'
        Write-ColorOutput Gray '  .\services.ps1 test-coupon -CouponId $couponId -Requests 50'
        return
    }

    $URL = "http://localhost:19100/api/v2/issued-coupons/$CouponId/use"

    Write-ColorOutput Yellow "`n설정:"
    Write-ColorOutput Gray "  - 쿠폰 ID: $CouponId"
    Write-ColorOutput Gray "  - 사용자 ID: $UserId"
    Write-ColorOutput Gray "  - 동시 요청: $Requests"
    Write-ColorOutput Gray "  - URL: $URL"

    $results = @()
    $jobs = @()

    Write-ColorOutput Green "`n=== $Requests 개 동시 요청 테스트 시작 ==="

    1..$Requests | ForEach-Object {
        $jobs += Start-Job -ScriptBlock {
            param($url, $userId)

            $headers = @{
                "X-User-Id" = $userId
                "X-User-Username" = "xodnd8384"
                "X-User-Role" = "MASTER"
                "Content-Type" = "application/json"
            }

            try {
                $response = Invoke-WebRequest -Uri $url -Method PATCH -Headers $headers -Body "{}" -UseBasicParsing -TimeoutSec 10
                return $response.StatusCode
            } catch {
                if ($_.Exception.Response) {
                    return $_.Exception.Response.StatusCode.value__
                }
                return "ERROR"
            }
        } -ArgumentList $URL, $UserId
    }

    Write-ColorOutput Yellow "요청 전송 완료. 응답 대기 중..."
    $results = $jobs | Wait-Job | Receive-Job

    Write-ColorOutput Green "`n=== 테스트 완료 ==="

    # 결과 분석
    Write-ColorOutput Cyan "`n=== HTTP 상태 코드 분포 ==="
    $results | Group-Object | Select-Object Name, Count | Format-Table -AutoSize

    $successCount = ($results | Where-Object { $_ -eq 200 }).Count
    $conflictCount = ($results | Where-Object { $_ -eq 409 }).Count
    $forbiddenCount = ($results | Where-Object { $_ -eq 403 }).Count
    $errorCount = ($results | Where-Object { $_ -eq "ERROR" }).Count

    Write-ColorOutput Yellow "`n=== 결과 요약 ==="
    Write-ColorOutput Gray "총 응답 수: $($results.Count)"
    Write-ColorOutput Green "성공 (200): $successCount"
    Write-ColorOutput Yellow "충돌 (409): $conflictCount"
    Write-ColorOutput Yellow "금지 (403): $forbiddenCount"
    Write-ColorOutput Red "오류 (ERROR): $errorCount"

    if ($successCount -eq 1 -and ($conflictCount + $forbiddenCount) -eq ($Requests - 1)) {
        Write-ColorOutput Green "`n✅ Exactly-Once 보장 검증 성공!"
        Write-ColorOutput Gray "정확히 1개만 성공, 나머지는 충돌/금지 처리됨"
    } elseif ($successCount -gt 1) {
        Write-ColorOutput Red "`n❌ Exactly-Once 보장 실패!"
        Write-ColorOutput Red "경고: $successCount 개가 성공했습니다. (1개만 성공해야 함)"
    } else {
        Write-ColorOutput Yellow "`n⚠️  예상치 못한 결과"
        Write-ColorOutput Yellow "결과를 확인하세요."
    }

    # 정리
    $jobs | Remove-Job
}

function Show-Help {
    Write-ColorOutput Cyan @"
╔═══════════════════════════════════════════════╗
║   Live Commerce Services Manager             ║
╚═══════════════════════════════════════════════╝

사용법:
  .\services.ps1 <command> [options]

명령어:
  start          모든 서비스 시작
  stop           모든 서비스 종료
  restart        모든 서비스 재시작
  test-coupon    쿠폰 동시성 테스트
  help           이 도움말 표시

예시:
"@

    Write-ColorOutput Yellow "`n### 서비스 시작"
    Write-ColorOutput Gray "  .\services.ps1 start"
    Write-ColorOutput Gray "  .\services.ps1 start -SkipDocker           # Docker는 이미 실행 중"
    Write-ColorOutput Gray "  .\services.ps1 start -SkipDocker -SkipConfig"

    Write-ColorOutput Yellow "`n### 서비스 종료"
    Write-ColorOutput Gray "  .\services.ps1 stop"
    Write-ColorOutput Gray "  .\services.ps1 stop -KeepDocker            # Docker 유지"
    Write-ColorOutput Gray "  .\services.ps1 stop -Force                 # Java 프로세스 강제 종료"

    Write-ColorOutput Yellow "`n### 서비스 재시작"
    Write-ColorOutput Gray "  .\services.ps1 restart"
    Write-ColorOutput Gray "  .\services.ps1 restart -KeepDocker"

    Write-ColorOutput Yellow "`n### 쿠폰 동시성 테스트"
    Write-ColorOutput Gray '  $couponId = "80ffdaa4-f2e5-4693-9472-0182c86f5021"'
    Write-ColorOutput Gray '  .\services.ps1 test-coupon -CouponId $couponId'
    Write-ColorOutput Gray '  .\services.ps1 test-coupon -CouponId $couponId -Requests 100'

    Write-ColorOutput Yellow "`n옵션:"
    Write-ColorOutput Gray "  start/restart:"
    Write-ColorOutput Gray "    -SkipDocker    Docker Compose 실행 스킵"
    Write-ColorOutput Gray "    -SkipConfig    Config Server 스킵"
    Write-ColorOutput Gray "    -SkipEureka    Eureka Server 스킵"
    Write-ColorOutput Gray "    -SkipGateway   Gateway 스킵"
    Write-ColorOutput Gray "    -SkipUser      User Service 스킵"
    Write-ColorOutput Gray "    -SkipCoupon    Coupon Service 스킵"

    Write-ColorOutput Gray "`n  stop:"
    Write-ColorOutput Gray "    -KeepDocker    Docker 서비스 유지"
    Write-ColorOutput Gray "    -Force         Java 프로세스 강제 종료"

    Write-ColorOutput Gray "`n  test-coupon:"
    Write-ColorOutput Gray "    -CouponId      테스트할 쿠폰 ID (필수)"
    Write-ColorOutput Gray "    -UserId        사용자 ID (기본값: 61b130f2-...)"
    Write-ColorOutput Gray "    -Requests      동시 요청 수 (기본값: 50)"

    Write-ColorOutput Yellow "`n서비스 포트:"
    Write-ColorOutput Gray "  Config Server:  http://localhost:18080"
    Write-ColorOutput Gray "  Eureka Server:  http://localhost:19090"
    Write-ColorOutput Gray "  Gateway:        http://localhost:19091"
    Write-ColorOutput Gray "  User Service:   http://localhost:19120"
    Write-ColorOutput Gray "  Coupon Service: http://localhost:19100"
}

#endregion

#region Main

switch ($Command) {
    "start"       { Invoke-StartServices }
    "stop"        { Invoke-StopServices }
    "restart"     { Invoke-RestartServices }
    "test-coupon" { Invoke-TestCoupon }
    "help"        { Show-Help }
    default       { Show-Help }
}

#endregion
