@echo off
chcp 65001 >nul
title smart-campus-canteen backend
set JAVA_HOME=D:\jdk-17
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d C:\Users\Lenovo\Desktop\smart-campus-canteen\backend
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr LISTENING') do taskkill /F /PID %%a >nul 2>&1
timeout /t 2 /nobreak >nul
if not exist target\smart-canteen-1.0.0.jar (
  echo JAR missing, packaging...
  call "D:\apache-maven-3.8.1\apache-maven-3.8.1\bin\mvn.cmd" -DskipTests package
)
echo Starting http://localhost:8080/api
"%JAVA_HOME%\bin\java.exe" -jar target\smart-canteen-1.0.0.jar
pause
