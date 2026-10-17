@echo off
setlocal enabledelayedexpansion

echo ==============================================
echo Building Sharkord Release APK
echo ==============================================
cd /d "%~dp0..\.."

rem Check default keystore location if not provided
if "%SIGNING_STORE_PATH%"=="" (
    if exist "D:\Backup\Dev\Personal\sharkord-android\keys\release-key.jks" (
        set "SIGNING_STORE_PATH=D:\Backup\Dev\Personal\sharkord-android\keys\release-key.jks"
    ) else if exist "keys\release-key.jks" (
        set "SIGNING_STORE_PATH=keys\release-key.jks"
    )
)

if "%SIGNING_STORE_PATH%"=="" (
    set /p "SIGNING_STORE_PATH=Enter path to keystore (.jks): "
)

if "%SIGNING_KEY_ALIAS%"=="" (
    set /p "SIGNING_KEY_ALIAS=Enter key alias (press enter for default 'key0'): "
    if "!SIGNING_KEY_ALIAS!"=="" set "SIGNING_KEY_ALIAS=key0"
)

if "%SIGNING_STORE_PASSWORD%"=="" (
    set /p "SIGNING_STORE_PASSWORD=Enter keystore password: "
)

if "%SIGNING_KEY_PASSWORD%"=="" (
    set "SIGNING_KEY_PASSWORD=!SIGNING_STORE_PASSWORD!"
)

echo.
echo Compiling and signing Release APK...
call gradlew.bat assembleRelease
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Release build failed!
    pause
    exit /b %errorlevel%
)

echo.
echo Copying APK to scripts\apk-creation...
if exist "app\build\outputs\apk\release\app-release.apk" (
    copy /y "app\build\outputs\apk\release\app-release.apk" "scripts\apk-creation\sharkord-release.apk"
) else if exist "app\build\outputs\apk\release\app-release-unsigned.apk" (
    copy /y "app\build\outputs\apk\release\app-release-unsigned.apk" "scripts\apk-creation\sharkord-release-unsigned.apk"
)

if %errorlevel% neq 0 (
    echo [ERROR] Failed to copy release APK!
    pause
    exit /b %errorlevel%
)

echo.
echo [SUCCESS] Release APK built and copied to scripts\apk-creation!
echo.
pause
