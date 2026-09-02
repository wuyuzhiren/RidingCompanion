@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
set "JAVA_HOME=C:\android-build\jdk\jdk-17.0.20.1+1"
set "ANDROID_HOME=C:\android-build\sdk"
set "GRADLE_HOME=C:\android-build\gradle-8.9"
if not exist "%JAVA_HOME%\bin\java.exe" ( echo [ERROR] JDK not found & pause & exit /b 1 )
if not exist "%GRADLE_HOME%\bin\gradle.bat" ( echo [ERROR] Gradle not found & pause & exit /b 1 )
set "VER_FILE=version.properties"
if not exist "%VER_FILE%" ( echo versionCode=0 > "%VER_FILE%" & echo versionName=1.0.0 >> "%VER_FILE%" )
for /f "tokens=2 delims==" %%a in ('findstr /b "versionCode" "%VER_FILE%"') do set "CODE=%%a"
for /f "tokens=2 delims==" %%a in ('findstr /b "versionName" "%VER_FILE%"') do set "NAME=%%a"
if not defined CODE set CODE=0
if not defined NAME set NAME=1.0.0
set /a NEWCODE=CODE+1
for /f "tokens=1,2,3 delims=." %%x in ("%NAME%") do ( set /a PATCH=%%z+1 & set "NEWMAJOR=%%x" & set "NEWMINOR=%%y" )
set "NEWNAME=%NEWMAJOR%.%NEWMINOR%.%PATCH%"
( echo versionCode=%NEWCODE% & echo versionName=%NEWNAME% ) > "%VER_FILE%"
echo. & echo [Version] %CODE% -^> %NEWCODE%   ^(%NAME% -^> %NEWNAME%^) & echo.
if not exist "app\src\main\res\drawable\avatar_closed.jpg" (
  echo [Decode] avatar_closed.jpg...
  powershell -NoProfile -Command "[IO.File]::WriteAllBytes('app\src\main\res\drawable\avatar_closed.jpg', [Convert]::FromBase64String([IO.File]::ReadAllText('binary-assets\avatar_closed.jpg.b64')))"
)
if not exist "app\src\main\res\drawable\avatar_open.jpg" (
  echo [Decode] avatar_open.jpg...
  powershell -NoProfile -Command "[IO.File]::WriteAllBytes('app\src\main\res\drawable\avatar_open.jpg', [Convert]::FromBase64String([IO.File]::ReadAllText('binary-assets\avatar_open.jpg.b64')))"
)
call "%GRADLE_HOME%\bin\gradle.bat" assembleRelease --no-daemon
if errorlevel 1 ( echo [BUILD FAILED] & pause & exit /b 1 )
set "OUT=%~dp0release"
if not exist "%OUT%" mkdir "%OUT%"
set "SRC=%~dp0app\build\outputs\apk\release\app-release.apk"
if not exist "%SRC%" ( echo [ERROR] APK not found & pause & exit /b 1 )
set "OUTFILE=riding_app_v%NEWNAME%_build%NEWCODE%.apk"
copy /y "%SRC%" "%OUT%\%OUTFILE%" >nul
echo. & echo ============================================================ & echo  BUILD OK! & echo  Version: v%NEWNAME%  ^(build %NEWCODE%^) & echo  File: %OUT%\%OUTFILE% & echo ============================================================ & echo.
pause
endlocal