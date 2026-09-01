# ============================================================
# claw-agent 前端启动脚本 (Windows PowerShell)
# 用法: .\deploy\scripts\start-frontend.ps1 [-Port 3000]
# ============================================================

param(
    [int]$Port = 3000
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent (Split-Path -Parent $ScriptDir)
$FrontendDir = Join-Path $ProjectDir "frontend"

$AppName = "claw-frontend"
$PidFile = Join-Path $FrontendDir "$AppName.pid"
$LogDir = Join-Path $FrontendDir "logs"
$LogFile = Join-Path $LogDir "$AppName.log"

Set-Location $FrontendDir

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

# 创建日志目录
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

# 检查依赖
if (-not (Test-Path "node_modules")) {
    Write-Host "[$AppName] 安装依赖..." -ForegroundColor Cyan
    npm install
}

# 检查构建产物
if (-not (Test-Path ".next")) {
    Write-Host "[$AppName] 构建前端..." -ForegroundColor Cyan
    npm run build
}

# 设置后端地址
$BackendUrl = $env:BACKEND_URL
if (-not $BackendUrl) {
    $BackendUrl = "http://localhost:8080"
    $env:BACKEND_URL = $BackendUrl
}

# 启动应用
Write-Host "[$AppName] 启动中 (port=$Port, backend=$BackendUrl)..." -ForegroundColor Cyan

$Process = Start-Process -FilePath "npm" -ArgumentList "run", "start", "--", "-p", $Port -WindowStyle Hidden -PassThru -RedirectStandardOutput $LogFile -RedirectStandardError (Join-Path $LogDir "$AppName-error.log")

$Process.Id | Out-File $PidFile -Force

Start-Sleep -Seconds 3

try {
    $Running = Get-Process -Id $Process.Id -ErrorAction Stop
    Write-Host "[$AppName] 启动成功 (PID: $($Process.Id))" -ForegroundColor Green
    Write-Host "[$AppName] 访问: http://localhost:$Port"
} catch {
    Write-Host "[$AppName] 启动失败，请检查日志: $LogFile" -ForegroundColor Red
    exit 1
}
