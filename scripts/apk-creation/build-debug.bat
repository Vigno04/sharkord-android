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
echo Copying APKs to scripts\apk-creation...
copy /y "app\build\outputs\apk\debug\*.apk" "scripts\apk-creation\"

if %errorlevel% neq 0 (
    echo [ERROR] Failed to copy the APKs!
    pause
    exit /b %errorlevel%
)

echo.
echo [SUCCESS] Debug APKs built and copied to scripts\apk-creation:
dir /b "%~dp0app-*-debug.apk"
echo.
pause
