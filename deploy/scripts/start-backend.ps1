# ============================================================
# claw-agent 后端启动脚本 (Windows PowerShell)
# 用法: .\deploy\scripts\start-backend.ps1 [-Profile prod|dev]
# ============================================================

param(
    [string]$Profile = "prod"
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent (Split-Path -Parent $ScriptDir)
$BackendDir = Join-Path $ProjectDir "backend"

$AppName = "claw-backend"
$JarFile = "backend-1.0.0-SNAPSHOT.jar"
$PidFile = Join-Path $BackendDir "$AppName.pid"
$LogDir = Join-Path $BackendDir "logs"
$LogFile = Join-Path $LogDir "$AppName.log"

# JVM 参数（按服务器配置调整）
$JavaOpts = @("-Xms512m", "-Xmx2g", "-XX:+UseG1GC", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=$LogDir")

# Spring 配置
$SpringOpts = @("--spring.profiles.active=$Profile")

Set-Location $BackendDir

# 检查是否已运行
if (Test-Path $PidFile) {
    $PID = Get-Content $PidFile
    try {
        $Process = Get-Process -Id $PID -ErrorAction Stop
        Write-Host "[$AppName] 已在运行 (PID: $PID)" -ForegroundColor Yellow
        exit 1
    } catch {
        Remove-Item $PidFile -Force
    }
}

# 创建必要目录
New-Item -ItemType Directory -Force -Path "data\uploads" | Out-Null
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

# 检查 JAR 是否存在
$JarPath = Join-Path "target" $JarFile
if (-not (Test-Path $JarPath)) {
    Write-Host "[$AppName] JAR 文件不存在，开始构建..." -ForegroundColor Cyan
    mvn clean package -DskipTests -q
}

# 启动应用
Write-Host "[$AppName] 启动中 (profile=$Profile)..." -ForegroundColor Cyan

$JavaArgs = $JavaOpts + @("-jar", $JarPath) + $SpringOpts
$Process = Start-Process -FilePath "java" -ArgumentList $JavaArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput $LogFile -RedirectStandardError (Join-Path $LogDir "$AppName-error.log")

$Process.Id | Out-File $PidFile -Force

Start-Sleep -Seconds 2

try {
    $Running = Get-Process -Id $Process.Id -ErrorAction Stop
    Write-Host "[$AppName] 启动成功 (PID: $($Process.Id))" -ForegroundColor Green
    Write-Host "[$AppName] 日志: Get-Content $LogFile -Wait"
} catch {
    Write-Host "[$AppName] 启动失败，请检查日志: $LogFile" -ForegroundColor Red
    exit 1
}
