@echo off
chcp 65001 >nul
cd /d %~dp0

echo ==========================================
echo   zhaopin-auto GitHub Push Script
echo ==========================================
echo.

REM Check gh auth
gh auth status >nul 2>&1
if %errorlevel% neq 0 (
    echo [INFO] GitHub CLI not authenticated. Starting login...
    gh auth login
)

echo.
echo Step 1: Creating GitHub repo...
gh repo create zhaopin-auto --public --source=. --remote=origin --push --description "AI-powered ZhiLian auto apply tool. Based on get_jobs by loks666."

if %errorlevel% neq 0 (
    echo.
    echo [WARN] gh repo create failed. Trying manual push...
    echo Please create repo manually at https://github.com/new
    echo Then run:
    echo   git remote remove origin
    echo   git remote add origin https://github.com/YOUR_USERNAME/zhaopin-auto.git
    echo   git push -u origin main --force
    pause
    exit /b 1
)

echo.
echo ==========================================
echo   Done! Repo created and pushed.
echo ==========================================
pause
