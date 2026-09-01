# ============================================================
# claw-agent 服务状态检查 (Windows PowerShell)
# 用法: .\deploy\scripts\status.ps1
# ============================================================

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent (Split-Path -Parent $ScriptDir)

function Check-App {
    param(
        [string]$AppDir,
        [string]$AppName,
        [int]$Port
    )
    
    $PidFile = Join-Path $AppDir "$AppName.pid"
    
    Write-Host "[$AppName] " -NoNewline
    
    if (-not (Test-Path $PidFile)) {
        Write-Host "未运行 (无 PID 文件)" -ForegroundColor Red
        return $false
    }
    
    $PID = Get-Content $PidFile
    
    try {
        $Process = Get-Process -Id $PID -ErrorAction Stop
        Write-Host "运行中 (PID: $PID, 端口: $Port)" -ForegroundColor Green
        return $true
    } catch {
        Write-Host "未运行 (PID: $PID 已失效)" -ForegroundColor Red
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
        return $false
    }
}

Write-Host "=========================================="
Write-Host "  claw-agent 服务状态"
Write-Host "=========================================="

$BackendDir = Join-Path $ProjectDir "backend"
$FrontendDir = Join-Path $ProjectDir "frontend"

$BackendOk = Check-App -AppDir $BackendDir -AppName "claw-backend" -Port 8080
$FrontendOk = Check-App -AppDir $FrontendDir -AppName "claw-frontend" -Port 3000

Write-Host "=========================================="
Write-Host ""
Write-Host "健康检查:"

# 后端健康检查
if ($BackendOk) {
    try {
        $Response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 5
        if ($Response.StatusCode -eq 200) {
            Write-Host "[Backend] 健康检查通过" -ForegroundColor Green
        } else {
            Write-Host "[Backend] 健康检查失败" -ForegroundColor Red
        }
    } catch {
        Write-Host "[Backend] 健康检查失败" -ForegroundColor Red
    }
}

# 前端健康检查
if ($FrontendOk) {
    try {
        $Response = Invoke-WebRequest -Uri "http://localhost:3000" -UseBasicParsing -TimeoutSec 5
        if ($Response.StatusCode -eq 200) {
            Write-Host "[Frontend] 健康检查通过" -ForegroundColor Green
        } else {
            Write-Host "[Frontend] 健康检查失败" -ForegroundColor Red
        }
    } catch {
        Write-Host "[Frontend] 健康检查失败" -ForegroundColor Red
    }
}
