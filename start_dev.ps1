# Live Meeting Copilot — One-Click Dev Launcher
Write-Host "===============================================" -ForegroundColor Green
Write-Host "  Starting Live Meeting Copilot (Live Engine) " -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Green

# 1. Start Backend FastAPI
$backendPath = Join-Path $PSScriptRoot "backend"
$venvPython = Join-Path $PSScriptRoot ".venv\Scripts\python.exe"

Write-Host "`n[1/2] Starting Backend Server (http://127.0.0.1:8000)..." -ForegroundColor Yellow
$backendJob = Start-Process -FilePath $venvPython -ArgumentList "-m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload" -WorkingDirectory $backendPath -PassThru

# 2. Start Frontend Vite
$frontendPath = Join-Path $PSScriptRoot "frontend"
Write-Host "[2/2] Starting Frontend Vite Server (http://localhost:5173)..." -ForegroundColor Yellow
$frontendJob = Start-Process -FilePath "npm.cmd" -ArgumentList "run dev" -WorkingDirectory $frontendPath -PassThru

Write-Host "`nBoth services are running!" -ForegroundColor Green
Write-Host "Open on computer: http://localhost:5173 (or http://localhost:8000)" -ForegroundColor White
Write-Host "Open on phone:     http://<YOUR_LOCAL_IP>:5173 (or http://<YOUR_LOCAL_IP>:8000)" -ForegroundColor White
Write-Host "`nPress Ctrl+C or close this terminal to stop." -ForegroundColor Gray

try {
    Wait-Process -Id $backendJob.Id, $frontendJob.Id
} finally {
    Stop-Process -Id $backendJob.Id, $frontendJob.Id -ErrorAction SilentlyContinue
}
