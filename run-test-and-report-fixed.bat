@echo off
setlocal

set REPORT_DIR=target\surefire-reports
set MVN_LOG=mvn-test.log

echo ========================================
echo RUNNING MVN TEST (LOG REDIRECTED)
echo ========================================

if exist "%REPORT_DIR%" rmdir /s /q "%REPORT_DIR%"
if exist "%MVN_LOG%" del /f /q "%MVN_LOG%"

call mvn test > "%MVN_LOG%" 2>&1
set MVN_EXIT_CODE=%ERRORLEVEL%

echo.
echo ========================================
echo MAVEN EXIT CODE: %MVN_EXIT_CODE%
echo ========================================

if not exist "%REPORT_DIR%" (
    echo [FATAL] Surefire reports not found
    echo See %MVN_LOG%
    exit /b 1
)

echo.
echo ========================================
echo TEST SUMMARY (FROM XML)
echo ========================================

powershell -NoProfile -ExecutionPolicy Bypass ^
  -File parse-surefire-report.ps1 ^
  -ReportDir "%REPORT_DIR%"

echo.
echo ========================================
echo RAW MAVEN LOG
echo ========================================
echo See file: %MVN_LOG%

exit /b %MVN_EXIT_CODE%
