@echo off
REM Launcher for MATSim MMUST simulation
REM Place this file, matsim-simulation.jar, and the jre21\ folder all in the same directory
REM Then double-click run.bat or run it from cmd

REM Get directory of this script so paths work from anywhere
SET DIR=%~dp0

REM Use bundled JRE if present, fall back to system java
IF EXIST "%DIR%jre21\bin\java.exe" (
    SET JAVA="%DIR%jre21\bin\java.exe"
) ELSE (
    SET JAVA=java
)

REM Run the simulation with 4GB heap — adjust -Xmx if the machine has less RAM
%JAVA% -Xmx4g -jar "%DIR%matsim-simulation.jar" "%DIR%full_config.xml"

pause
