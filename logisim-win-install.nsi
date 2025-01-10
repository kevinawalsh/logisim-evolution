!include WinMessages.nsh
!include FileFunc.nsh
 
SilentInstall silent
RequestExecutionLevel user
ShowInstDetails hide
 
OutFile "Logisim-Evolution-5.0.4hc.exe"
Icon "logisim.ico"
VIProductVersion 5.0.4.00000
VIAddVersionKey ProductName "Logisim-Evolution-5.0.4hc"
VIAddVersionKey LegalCopyright "Copyright (c) 2025 Kevin Walsh"
VIAddVersionKey FileDescription "Digital logic designer and simulator"
VIAddVersionKey FileVersion 5.0.4.00000
VIAddVersionKey ProductVersion "5.0.4hc / Adoptium OpenJDK Temurin-17.0.7_7 (x64)"
VIAddVersionKey InternalName "Logisim-Evolution-HC"
VIAddVersionKey OriginalFilename "Logisim-Evolution-5.0.4hc.exe"
 
Section
  SetOverwrite off
 
  SetOutPath "$TEMP\logisim-evolution-runtime"
  File /r "logisim-evolution-runtime\*"
 
  InitPluginsDir
  SetOutPath $PluginsDir
  File "logisim-evolution-5.0.4hc.jar"
  SetOutPath $TEMP
  ${GetParameters} $R0
  nsExec::Exec '"$TEMP\logisim-evolution-runtime\bin\javaw.exe" -jar $PluginsDir\logisim-evolution-5.0.4hc.jar $R0'
  RMDir /r $PluginsDir
SectionEnd
