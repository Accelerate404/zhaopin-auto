@echo off
chcp 65001 >nul
cd /d %~dp0

echo ==========================================
echo   zhaopin-auto: ZhiLian AI Auto Apply
echo ==========================================
echo.

if not exist ".env" (
    echo [ERROR] .env not found. Copy .env_template to .env and fill in your API key.
    echo         copy .env_template .env
    pause
    exit /b 1
)

if not exist "resume.md" (
    echo [ERROR] resume.md not found. Create it with your resume content.
    pause
    exit /b 1
)

set JAR=target\get_jobs-v2.0.1-jar-with-dependencies.jar

if not exist "%JAR%" (
    echo [ERROR] %JAR% not found. Run: mvn clean package -DskipTests
    pause
    exit /b 1
)

REM Check Java
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java not found. Please install JDK 17 and add to PATH.
    pause
    exit /b 1
)

echo Starting... Please scan QR code to login ZhiLian in the browser.
echo.
java -cp "%JAR%" zhilian.ZhiLian
pause
