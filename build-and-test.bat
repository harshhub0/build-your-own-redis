@echo off
echo ========================================================
echo Building Redis in Java and Running Comprehensive Tests
echo ========================================================

if not exist "bin" mkdir bin

echo Compiling Java source files...
dir /s /b src\main\java\*.java > sources.txt
javac -encoding UTF-8 -d bin @sources.txt
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed!
    del sources.txt
    pause
    exit /b 1
)
del sources.txt

echo.
echo Compiling and Running Test Suite...
java -ea -cp bin com.redis.test.TestRunner
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Tests failed!
    pause
    exit /b 1
)

echo.
echo Build and tests completed successfully!
