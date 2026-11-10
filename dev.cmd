@echo off
rem Project-local build wrapper (Windows).
rem Usage: dev build ^| dev runClient ^| dev runGameTestServer ^| dev ^<any gradle task/args^>
rem
rem Everything (mise, JDK, Gradle distribution, Gradle caches, Minecraft artifacts,
rem temp files) is kept inside this folder:
rem   .tools\mise\{data,cache,state,tmp,config}  - mise directories
rem   .gradle-home\                              - GRADLE_USER_HOME (set in mise.toml [env])
rem   .tools\tmp\                                - TMP/TEMP for the build
setlocal
set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

set "MISE_DATA_DIR=%ROOT%\.tools\mise\data"
set "MISE_CACHE_DIR=%ROOT%\.tools\mise\cache"
set "MISE_STATE_DIR=%ROOT%\.tools\mise\state"
set "MISE_TMP_DIR=%ROOT%\.tools\mise\tmp"
set "MISE_CONFIG_DIR=%ROOT%\.tools\mise\config"
rem Trust this project's mise.toml without an interactive prompt, never touch global config.
set "MISE_TRUSTED_CONFIG_PATHS=%ROOT%"
set "MISE_YES=1"
set "MISE_HIDE_UPDATE_WARNING=1"
set "TMP=%ROOT%\.tools\tmp"
set "TEMP=%ROOT%\.tools\tmp"

for %%D in ("%MISE_DATA_DIR%" "%MISE_CACHE_DIR%" "%MISE_STATE_DIR%" "%MISE_TMP_DIR%" "%MISE_CONFIG_DIR%" "%TMP%") do (
  if not exist "%%~D" mkdir "%%~D"
)

set "MISE_BIN="
for /f "delims=" %%M in ('where mise 2^>nul') do if not defined MISE_BIN set "MISE_BIN=%%M"
if not defined MISE_BIN (
  set "MISE_BIN=%ROOT%\.tools\bin\mise.exe"
  if not exist "%ROOT%\.tools\bin\mise.exe" (
    echo [dev] mise not found on PATH, installing a project-local copy into .tools\bin ... 1>&2
    powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\gradle\bootstrap-mise.ps1" -Destination "%ROOT%\.tools\bin"
    if errorlevel 1 exit /b 1
  )
)

cd /d "%ROOT%"
"%MISE_BIN%" exec -- "%ROOT%\gradlew.bat" %*
exit /b %ERRORLEVEL%
