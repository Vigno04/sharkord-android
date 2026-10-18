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
echo Copying and renaming APKs to scripts\apk-creation...
for %%f in ("app\build\outputs\apk\release\*.apk") do (
    set "fname=%%~nxf"
    if not "!fname:arm64-v8a=!"=="!fname!" (
        copy /y "%%f" "scripts\apk-creation\sharkord-v8a.apk" >nul
    ) else if not "!fname:armeabi-v7a=!"=="!fname!" (
        copy /y "%%f" "scripts\apk-creation\sharkord-v7a.apk" >nul
    ) else if not "!fname:universal=!"=="!fname!" (
        copy /y "%%f" "scripts\apk-creation\sharkord-universal.apk" >nul
    ) else if not "!fname:x86_64=!"=="!fname!" (
        copy /y "%%f" "scripts\apk-creation\sharkord-x86_64.apk" >nul
    )
)

if %errorlevel% neq 0 (
    echo [ERROR] Failed to copy release APKs!
    pause
    exit /b %errorlevel%
)

echo.
echo [SUCCESS] Release APKs built and copied to scripts\apk-creation:
dir /b "scripts\apk-creation\sharkord-*.apk"
echo.
pause

