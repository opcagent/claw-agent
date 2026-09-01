# ============================================================
# claw-agent 停止所有服务 (Windows PowerShell)
# 用法: .\deploy\scripts\stop-all.ps1
# ============================================================

$ErrorActionPreference = "Continue"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent (Split-Path -Parent $ScriptDir)

function Stop-App {
    param(
        [string]$AppDir,
        [string]$AppName
    )
    
    $PidFile = Join-Path $AppDir "$AppName.pid"
    
    if (-not (Test-Path $PidFile)) {
        Write-Host "[$AppName] PID 文件不存在，跳过" -ForegroundColor Gray
        return
    }
    
    $PID = Get-Content $PidFile
    
    try {
        $Process = Get-Process -Id $PID -ErrorAction Stop
        Write-Host "[$AppName] 停止中 (PID: $PID)..." -ForegroundColor Cyan
        
        Stop-Process -Id $PID -Force
        Start-Sleep -Seconds 2
        
        Write-Host "[$AppName] 已停止" -ForegroundColor Green
    } catch {
        Write-Host "[$AppName] 进程不存在 (PID: $PID)，清理 PID 文件" -ForegroundColor Gray
    }
    
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}

Write-Host "=========================================="
Write-Host "  claw-agent 服务停止"
Write-Host "=========================================="

$BackendDir = Join-Path $ProjectDir "backend"
$FrontendDir = Join-Path $ProjectDir "frontend"

Stop-App -AppDir $BackendDir -AppName "claw-backend"
Stop-App -AppDir $FrontendDir -AppName "claw-frontend"

Write-Host "=========================================="
Write-Host "  所有服务已停止" -ForegroundColor Green
Write-Host "=========================================="
