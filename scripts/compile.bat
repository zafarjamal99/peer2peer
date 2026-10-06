@echo off
if not exist out mkdir out
javac -encoding UTF-8 -d out src\p2p\*.java
if %ERRORLEVEL% EQU 0 (
  echo Compilation successful.
) else (
  echo Compilation failed.
  exit /b 1
)
