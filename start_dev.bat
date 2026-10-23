@echo off
title Live Meeting Copilot Launcher
echo ===============================================
echo   Starting Live Meeting Copilot (Live Engine)
echo ===============================================

echo [1/2] Starting Backend FastAPI on port 8000...
start cmd /k "cd backend && ..\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload"

echo [2/2] Starting Frontend Vite on port 5173...
start cmd /k "cd frontend && npm.cmd run dev"

echo.
echo Both services are launching in separate windows!
echo Open in browser: http://localhost:5173
echo Or directly on:  http://localhost:8000
pause
