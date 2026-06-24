
@echo OFF

rem   This script is for building the final Windows executable image. This is based
rem   closely on the Mac script in build-mac-release-package.sh. However, there are
rem   some differences.
rem   
rem   Most importantly, until (maybe) recently, jpackage does not seem to work
rem   on windows: selecting either exe or msi types for output generates an
rem   unsupported-type error. The previously-used Launch4j tool does not support
rem   bundling the runtime, and we should not expect the user to have a recent
rem   Java runtime preinstalled -- after all, there is no downloadable JRE for
rem   recent Java releases.
rem   
rem   Workaround 1: Use Launch4j to build an EXE that references a jlink-produced Java
rem   runtime in the same directory, then distribute that EXE with the runtime as a ZIP
rem   file. Users can unzip, put wherever they like, then click the exe. As long as
rem   the JRE stays in the same directory, should work fine, but it is not ideal.
rem   
rem   Workaround 2: Use NSIS, as described here:
rem     https://netnix.org/2018/07/19/windows-exe-bundled-with-openjdk/
rem   This will silently unzip the JRE to a temp folder (but cache the result), and
rem   will unzip the jar as well, then execute them. Basically, it is a silent
rem   installer that runs every time you click the EXE. We adopt this strategy.

rem   Source files needed:
rem     logisim-evolution.jar
rem     logisim.ico
rem     LICENSE
rem     logisim-l4j.xml
rem     logisim-win-install.nsi

rem   How to run this script:
rem     - Change into this directory.
rem     - Ensure the correct, release-ready logisim-evolution.jar file is
rem       present in this directory.
rem     - Ensure the current LTS version of java is installed.
rem     - Edit JAVA_VER and the install paths below.
rem     - run in cmd.exe

rem Configurable Version Info and Install Paths -- edit these before each release
set JAVA_VER="Eclipse Adoptium OpenJDK Temurin-25.0.3_9"
set JDEPS="C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin\jdeps.exe"
set JLINK="C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin\jlink.exe"
set LAUNCH4J="C:\Program Files (x86)\Launch4j\launch4jc.exe"
set NSIS="c:\Program Files (x86)\NSIS\Bin\makensis.exe"

echo Gathering info...
set /p VERSION=<VERSION
set /p COPYRIGHT_YEAR=<COPYRIGHT_YEAR
set VER_HC=%VERSION:-HC=hc%
set VER_BASE=%VERSION:-HC=%
set VER_DOTDOT=%VER_BASE%.00000
set BUILD_DIR=%~dp0
echo Version: %VERSION% ^(%VER_HC%^, %VER_DOTDOT%^), Copyright year: %COPYRIGHT_YEAR%
echo BuildDir: %BUILD_DIR%

rem Using print-module-deps appears to be the correct way to get the dependencies.
echo Detecting java module dependencies...
%JDEPS% --print-module-deps --ignore-missing-deps logisim-evolution.jar > module-deps.txt
set /p DETECTED_MODULES=<module-deps.txt

set MODULES=java.base,java.desktop,java.logging,java.management,java.net.http,java.prefs,jdk.httpserver
echo Detected java module dependencies: %DETECTED_MODULES%

set JAVA_RUNTIME=logisim-evolution-runtime

if "%DETECTED_MODULES%" == "%MODULES%" goto modules_ok
  echo ERROR: This differs from expected!
  echo      : Module dependencies must have changed!
  echo      : Within build-packager.sh, set MODULES=\"%DETECTED_MODULES%\"
  echo      : Then delete .\%JAVA_RUNTIME% and try running this again.
  exit /b
:modules_ok

if EXIST "%JAVA_RUNTIME%" goto runtime_ok
  echo Building custom java runtime (using jlink)...
  %JLINK% --no-header-files --no-man-pages --strip-debug ^
        --add-modules "%MODULES%" --output "%JAVA_RUNTIME%"
  goto runtime_built
:runtime_ok
  echo Using previously built custom java runtime (from jlink).
:runtime_built

echo Generating logisim-l4j.xml from template...
powershell -NoProfile -Command "(Get-Content 'logisim-l4j.xml.template') -replace '@@VER_HC@@',$env:VER_HC -replace '@@BUILD_DIR@@',$env:BUILD_DIR | Set-Content 'logisim-l4j.xml'" || goto :error
echo Creating executable wrapper...
%LAUNCH4J% logisim-l4j.xml || goto :error

echo Creating ZIP package for distribution...
IF EXIST Logisim-Evolution-%VER_HC%-windows.zip del Logisim-Evolution-%VER_HC%-windows.zip || goto :error
IF EXIST Logisim-Evolution-%VER_HC% rmdir /S /Q Logisim-Evolution-%VER_HC% || goto :error
mkdir Logisim-Evolution-%VER_HC% || goto :error
copy LICENSE Logisim-Evolution-%VER_HC%\LICENSE.txt || goto :error
copy logisim-evolution-%VER_HC%.exe Logisim-Evolution-%VER_HC% || goto :error
xcopy /s logisim-evolution-runtime Logisim-Evolution-%VER_HC%\logisim-evolution-runtime\ || goto :error
powershell.exe -nologo -noprofile -command "& { Add-Type -A 'System.IO.Compression.FileSystem'; [IO.Compression.ZipFile]::CreateFromDirectory('Logisim-Evolution-%VER_HC%', 'Logisim-Evolution-%VER_HC%-windows.zip'); }" || goto :error
IF EXIST logisim-evolution-%VER_HC%.exe del logisim-evolution-%VER_HC%.exe || goto :error
rmdir /S /Q Logisim-Evolution-%VER_HC% || goto :error

echo Creating self-contained executable...
echo Generating logisim-win-install.nsi from template...
powershell -NoProfile -Command "(Get-Content 'logisim-win-install.nsi.template') -replace '@@VER_HC@@',$env:VER_HC -replace '@@VER_DOTDOT@@',$env:VER_DOTDOT -replace '@@COPYRIGHT_YEAR@@',$env:COPYRIGHT_YEAR -replace '@@JAVA_VER@@',$env:JAVA_VER | Set-Content 'logisim-win-install.nsi'" || goto :error
copy logisim-evolution.jar logisim-evolution-%VER_HC%.jar || goto :error
%NSIS% logisim-win-install.nsi || goto :error

echo =======================================
echo Success, maybe?
echo =======================================

goto :EOF

:error
echo =======================================
echo Failed with error #%errorlevel%.
echo =======================================
exit /b %errorlevel%

