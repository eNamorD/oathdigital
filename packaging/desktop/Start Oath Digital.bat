@echo off
rem Double-click to start Oath Digital. Close this window or press Ctrl-C to stop.
setlocal
cd /d "%~dp0"
set "OATH_LAUNCH=desktop"
call "bin\oathdigital.bat" %*
set "status=%ERRORLEVEL%"
if not "%status%"=="0" (
  echo.
  echo Oath Digital stopped with an error ^(exit %status%^).
  pause
)
exit /b %status%
