
@echo OFF

rem   This script is for building the final Windows executable image. This is based
rem   closely on the Mac script in build-mac-release-package.sh. However, there are
rem   some differences.
rem   
rem   Most importantly, jpackage does not seem to work on windows: selecting either
rem   exe or msi types for output generates an unsupported-type error. The
rem   previously-used Launch4j tool does not support bundling the runtime, and we
rem   should not expect the user to have a Java 11 runtime preinstalled -- after all,
rem   there is no downloadable JRE for Java 11.
rem   
rem   Workaround 1: Use Launch4j to build an EXE that references a jlink-produced Java
rem   runtime in the same directry, then distribute that EXE with the runtime as a ZIP
rem   file. Users can unzip, put wherever they like, then click the exe. As long as
rem   the JRE stays in the same directory, should work fine.
rem   
rem   Workaround 2: Use NSIS, as described here:
rem     https://netnix.org/2018/07/19/windows-exe-bundled-with-openjdk/
rem   This will silently unzip the JRE to a temp folder (but cache the result), and
rem   will unzip the jar as well, then execute them. Basically, it is a silent
rem   installer that runs every time you click the EXE.

rem   Source files needed:
rem     logisim-evolution.jar
rem     logisim.ico
rem     LICENSE
rem     logisim-l4j.xml
rem     logisim-win-install.nsi

rem set JDEPS="C:\Program Files\Java\jdk-11.0.4+11\bin\jdeps.exe"
rem set JLINK="C:\Program Files\Java\jdk-11.0.4+11\bin\jlink.exe"
set JDEPS="C:\Program Files\AdoptOpenJDK\jdk-11.0.4.11-hotspot\bin\jdeps.exe"
set JLINK="C:\Program Files\AdoptOpenJDK\jdk-11.0.4.11-hotspot\bin\jlink.exe"
rem set PACKAGER="C:\Program Files\Java\jdk-14\bin\jpackage.exe"
set LAUNCH4J="C:\Program Files (x86)\Launch4j\launch4jc.exe"
set NSIS="c:\Program Files (x86)\NSIS\Bin\makensis.exe"

rem Using print-module-deps appears to be the correct way to get the dependencies.
echo Detecting java module dependencies...
%JDEPS% --print-module-deps logisim-evolution.jar > module-deps.txt
set /p DETECTED_MODULES=<module-deps.txt

set MODULES=java.base,java.desktop,java.logging,java.prefs
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
  %JLINK% --no-header-files --no-man-pages --compress=2 --strip-debug ^
        --add-modules "%MODULES%" --output "%JAVA_RUNTIME%"
  goto runtime_built
:runtime_ok
  echo Using previously built custom java runtime (from jlink).
:runtime_built

rem   rem installer type can be exe or msi, but exe does not seem to work
rem   set INSTALLER_TYPE="msi"
rem   set OUTPUT=.
rem   set JAR=logisim-evolution.jar
rem   set VERSION=4.0.1
rem   rem FILE_ASSOCIATIONS="file-associations.properties"
rem   set APP_ICON="logisim.ico"
rem   rem JAVA_APP_IDENTIFIER="edu.holycross.cs.kwalsh.logisim"
rem   
rem   rem Prepare input files
rem   echo Preparing input files...
rem   rmdir /S /Q win-staging
rem   mkdir win-staging
rem   copy LICENSE LICENSE.txt
rem   copy LICENSE.txt win-staging\
rem   copy %JAR% win-staging\
rem   
rem   rem Prepare installer customizations: background image, postinstall script
rem   rem echo Preparing installer customizations...
rem   rem rm -rf win-resources
rem   rem mkdir -p win-resources
rem   rem cp win-installer-background.png win-resources/Logisim-Evolution-background.png
rem   
rem     rem Build the app and installer package
rem     echo Building %INSTALLER_TYPE% ...
rem     %PACKAGER% ^
rem       --package-type %INSTALLER_TYPE% ^
rem       --input win-staging ^
rem       --output "%OUTPUT%" ^
rem       --name "Logisim-Evolution" ^
rem       --main-class com.cburch.logisim.Main ^
rem       --main-jar "%JAR%" ^
rem       --app-version "%VERSION%" ^
rem       --copyright "(c) 2019 Kevin Walsh" ^
rem       --description "Digital logic designer and simulator." ^
rem       --vendor "Kevin Walsh" ^
rem       --add-modules "%MODULES%" ^
rem       --runtime-image "%JAVA_RUNTIME%" ^
rem       --icon "%APP_ICON%" ^
rem       --file-associations "%FILE_ASSOCIATIONS%" ^
rem       --identifier "%JAVA_APP_IDENTIFIER%" ^
rem       --resource-dir mac-resources ^
rem       --license-file LICENSE.txt ^
rem       --win-dir-chooser ^
rem       --win-menu
rem   
rem   rem rmdir /S /Q win-staging
rem   rem rm -rf mac-resources
rem   move Logisim-Evolution-%VERSION%.exe Logisim-Evolution-%VERSION%-HC.exe

rem echo Creating executable wrapper...
rem %LAUNCH4J% logisim-l4j.xml

echo Creating ZIP package for distribution...
del Logisim-Evolution-4.0.1hc.zip
rmdir /S /Q Logisim-Evolution-4.0.1hc
mkdir Logisim-Evolution-4.0.1hc
copy LICENSE Logisim-Evolution-4.0.1hc\LICENSE.txt
copy logisim-evolution-4.0.1hc.exe Logisim-Evolution-4.0.1hc
xcopy /s logisim-evolution-runtime Logisim-Evolution-4.0.1hc\logisim-evolution-runtime\
powershell.exe -nologo -noprofile -command "& { Add-Type -A 'System.IO.Compression.FileSystem'; [IO.Compression.ZipFile]::CreateFromDirectory('Logisim-Evolution-4.0.1hc', 'Logisim-Evolution-4.0.1hc.zip'); }"
del logisim-evolution-4.0.1hc.exe
rmdir /S /Q Logisim-Evolution-4.0.1hc

echo Creating self-contained executable...
copy logisim-evolution.jar logisim-evolution-4.0.1hc.jar
%NSIS% logisim-win-install.nsi
