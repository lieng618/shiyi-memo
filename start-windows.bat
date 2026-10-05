@echo off
rem Double-click launcher for start-windows.ps1.
rem Windows always opens .ps1 files in Notepad on double-click, so this .bat
rem is the entry point: it runs the .ps1 with the execution policy bypassed
rem for this session only (no system policy change).
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-windows.ps1"
echo.
echo --------------------------------------------------------
echo Script finished (or failed - see messages above).
echo Press any key to close this window...
pause >nul
