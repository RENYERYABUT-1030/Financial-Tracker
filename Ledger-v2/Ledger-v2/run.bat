@echo off
setlocal
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
    echo Java JDK 8 or newer is required. Install the JDK and add its bin folder to PATH.
    pause
    exit /b 1
)
if not exist out mkdir out
javac -encoding UTF-8 -d out src\FinancialTracker.java
if errorlevel 1 (
    echo Compilation failed.
    pause
    exit /b 1
)
java -cp out FinancialTracker
if errorlevel 1 pause
