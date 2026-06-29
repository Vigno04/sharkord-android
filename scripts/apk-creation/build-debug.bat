@echo off
setlocal enabledelayedexpansion

echo Building Debug APK...
cd /d "%~dp0..\.."

call gradlew.bat assembleDebug
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Build failed!
    pause
    exit /b %errorlevel%
)

echo.
echo Copying APK to scripts\apk-creation...
copy /y "app\build\outputs\apk\debug\app-debug.apk" "scripts\apk-creation\sharkord-debug.apk"

if %errorlevel% neq 0 (
    echo [ERROR] Failed to copy the APK!
    pause
    exit /b %errorlevel%
)

echo.
echo [SUCCESS] APK built and copied to:
echo %~dp0sharkord-debug.apk
echo.
pause
