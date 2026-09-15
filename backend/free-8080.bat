@echo off
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr LISTENING') do (
  taskkill /F /PID %%a >nul 2>&1
)
exit /b 0
