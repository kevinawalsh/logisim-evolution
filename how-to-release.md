# How to build releases

1. Check git status, ensure on holycross branch all work is committed.

2. Bump version, copyright year, and contact info in various files:
   - VERSION, COPYRIGHT\_YEAR, and contact.txt
   - README.md
   - logisim-win-install.nsi
   - build-mac-release-package.sh
   - build-mac-release-package-x86.sh
   - build-win-release-package.bat
   - logisim-l4j.xml

3. ant cleanall && ant jar publish-docs
   - Copy logisim-evolution.jar to logisim-evolution-${VERSION}hc.jar
   - Artifact for release: logisim-evolution-${VERSION}hc.jar

4. On a MacOS arm64 platform: ./build-mac-release-package.sh
   - skim output, sanity check
   - follow printed instructions to notarize, etc.
   - Artifact for release: Logisim-Evolution-${VERSION}-HC.pkg

5. On a MacOS x86 platform: ./build-mac-release-package.sh
   - copy needed files anywhere, including:
        LICENSE
        logisim-evolution.jar
        file-associations.properties
        logisim.icns
        mac-installer-background.png
        build-mac-release-package.sh
   - adjust PACKAGER path in build-mac-release-package.sh
   - skim output, sanity check
   - follow printed instructions to notarize, etc.
   - Artifact for release: Logisim-Evolution-${VERSION}-HC-x86.pkg

6. On Windows platform:
   - install Eclipse Adoptium Temurin, appropriate version (e.g. version 17)
   - install NSIS
   - install Launch4j
   - copy needed files into C:\Users\kwalsh\Downloads\logisim-evolution\
     including:
        LICENSE
        build-win-release-package.bat
        logisim.ico
        logisim-evolution.jar
        logisim-l4j.xml
        logisim-win-install.nsi
   - update build-win-release-package.bat paths for hotspot, Launch4j, NSIS, etc.
   - run build-win-release-package.bat
   - Artifact for release: Logisim-Evolution-${VERSION}hc-windows.zip
   - Artifact for release: Logisim-Evolution-${VERSION}hc.exe

7. Create github release, and upload all artifacts
   - Ensure github pages are showing new version of docs
