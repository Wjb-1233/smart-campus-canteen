@echo off
setlocal
cd /d %~dp0

echo [1/3] Import SQL if needed (utf8mb4)...
mysql --default-character-set=utf8mb4 -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS campus_canteen DEFAULT CHARACTER SET utf8mb4;" 2>nul
mysql --default-character-set=utf8mb4 -uroot -p123456 campus_canteen -e "SOURCE %~dp0sql/01_schema.sql" 2>nul
mysql --default-character-set=utf8mb4 -uroot -p123456 campus_canteen -e "SOURCE %~dp0sql/02_seed.sql" 2>nul

echo [2/3] Start backend...
start "canteen-backend" cmd /k "cd /d %~dp0backend && mvn -DskipTests spring-boot:run"

echo [3/3] Start frontend...
start "canteen-frontend" cmd /k "cd /d %~dp0frontend && npm run dev -- --host 127.0.0.1 --port 5173"

echo.
echo Backend:  http://localhost:8080/api
echo Frontend: http://127.0.0.1:5173
echo Demo login: 2021001001 / 123456
endlocal
