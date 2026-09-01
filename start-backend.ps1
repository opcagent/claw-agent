# ============================================================
# claw-agent 后端启动脚本（Windows PowerShell）
# 自动加载 .env 环境变量并启动 Spring Boot 应用
# ============================================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  claw-agent Backend Starter" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查 .env 文件是否存在
if (-not (Test-Path ".env")) {
    Write-Host "[ERROR] .env file not found!" -ForegroundColor Red
    Write-Host "Please copy .env.example to .env and configure it:" -ForegroundColor Yellow
    Write-Host "  Copy-Item .env.example .env" -ForegroundColor Gray
    exit 1
}

Write-Host "[INFO] Loading environment variables from .env..." -ForegroundColor Green

# 读取 .env 文件并设置环境变量
Get-Content .env | Where-Object { $_ -match '^\w+=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    $value = $value.Trim()
    
    # 跳过空值
    if ([string]::IsNullOrWhiteSpace($value)) {
        Write-Host "  [SKIP] $name (empty value)" -ForegroundColor Gray
        return
    }
    
    # 设置进程级环境变量
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    Write-Host "  [OK] $name=$value" -ForegroundColor Gray
}

Write-Host ""
Write-Host "[INFO] Starting Spring Boot application..." -ForegroundColor Green
Write-Host ""

# 切换到 backend 目录并启动应用
Set-Location backend

# 方式 1：使用 Maven 直接运行（开发环境推荐）
mvn spring-boot:run

# 方式 2：如果已打包，使用 jar 运行（生产环境）
# java -jar target/backend-1.0.0-SNAPSHOT.jar
