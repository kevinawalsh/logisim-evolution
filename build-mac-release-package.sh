#!/bin/bash

# This script is for building the final MacOS executable image. This is a
# comically complicated procedure, for many reasons:
# - Everything below Java 11 is becoming quickly obsolete and difficult to
#   support. Libraries and tools are disappearing, packages won't install, etc.
#   Solution: Use Java 11, at least for now.
# - Homebrew installs a broken version of AdoptOpenJDK-11 (as of Aug 13, 2018).
#   The jmod files included in the install contain the wrong sha256 hashes, so
#   jlink fails when attempting to build the modularized java-runtime.
#   Workaround: Install AdoptOpenJDK-11 by downloading the mac tar.gz directly
#   from github, copy into /Library/Java/JavaVirtualMachines/jdk-11.0.4+11,
#   fix the ownership and permissions, and hope for the best.
# - JDK-11 does not include javapackager, jpackager, jpackage, or any similar
#   tools. There appears to be no official way to package java applications as
#   of JDK 11, and there will not be until Java 13 at the earliest. Yes, this
#   seems incomprehensible that there could be no way to package a java
#   application for distribution any more.
#   There is an unofficial backported jpackager tool discussed here:
#   https://mail.openjdk.java.net/pipermail/openjfx-dev/2018-September/022500.html
#   It is also possible to install early access JDK 14 (or early access JDK 13),
#   which come with a new jpackage tool, which apparently can be used to package
#   files created with JDK 11. Both of these appear to be broken in various
#   ways: the backport can't deal with license files correctly, and neither sets
#   the permissions correctly on the installed files.
#   Workaround: Install early access JDK 14, and use that jpackage tool with
#   the JDK 11 runtime and build artifacts, combined with a postinstall script
#   to fix the permissions.
# - MacOS is becoming increasingly (and rapidly) more strict about running code
#   from unsigned/unofficial sources. It seems that the most recent versions of
#   MacOS may not be able to run unsigned sources at all, at least not without
#   extensive warnings and fiddling with security settings in likely unsafe
#   ways. It appears to no longer be tenable to ship unsigned code for MacOS.
#   Solution: Get developer keys and sign MacOS installers / executables.
# - Apple's University Developer Program claims to allow for obtaining
#   appropriate keys. However, the documentation for creating these keys is
#   vague, outdated, and contradictory, at best, and an extended back-and-forth
#   with our university account holder and with Apple yields no results. It
#   seems that the necessary signing keys are simply no longer possible to
#   generate within the University Developer Program.
#   Workaround: Use a paid, Individual Apple Developer account to obtain signing
#   keys.
# - My current AppleID is enrolled in our University Developer Program. Apple
#   confirms that each AppleID can be enrolled in at most one developer program.
#   Workaround: Create and use a separate AppleID for just Logisim Evolution.
#   Also work on an entirely fresh Mac hardware and account, since the AppleID
#   is engrained fairly thoroughly in MacOS.
# - jpackage (via Apple's pkgbuild/productbuild) does not set file permissions
#   (chmod) correctly on the installed application by default. jpackage does not
#   appear to have a way to pass custom arguments to pkgbuild/productbuild, and
#   it isn't clear there are any options that would help (the "preserve file
#   ownership" options might, but that doesn't say anything about preserving
#   file permissions).
#   Workaround: use a postinstall script to chmod all the files to reasonable
#   permissions.
# - On my rarely-used mac, jpackage from jdk-14-ea-16 seems to have entirely
#   disappeared, without a trace. Perhaps some auto update? The download links
#   on oracle's site for that package are dead/404. The original download
#   does not contain jpackage. The build scripts do not work without it.
#   Workaround: Download and install new jdk-14-ea-32 release, which seems to
#   have jpaackage again.
# - The new jpackage has renamed lots of options, so fix those below:
#    '--package-type' is now '--type'
#    '--output' is now '--dest'
#    '--mac-bundle-identifier' is now '--mac-package-identifier'
#    '--mac-bundle-name' is now '--mac-package-name'
#    '--identifier' isn ow '--mac-package-signing-prefix'
#    '--add-modules' and '--runtime-image' are now mutually exclusive, so
#       eliminate the former.
# - Code signing may *appear* to fail with the new jpackage with an error like:
#   Running [codesign, --verify, /var/folders/kb/zswdtzg94bs52lqrts5sssdr0000gp/T/jdk.incubator.jpackage13637540241510693609/images/image-5430644754281798291/Logisim-Evolution.app/Contents/MacOS/libapplauncher.dylib]
#   /var/folders/kb/zswdtzg94bs52lqrts5sssdr0000gp/T/jdk.incubator.jpackage13637540241510693609/images/image-5430644754281798291/Logisim-Evolution.app/Contents/MacOS/libapplauncher.dylib: code object is not signed at all
#   In architecture: x86_64
#   Running [codesign, -s, Developer ID Application: Kevin Walsh (GDM3S3ULJA), --prefix, edu.holycross.cs.kwalsh.logisim, -vvvv, /var/folders/kb/zswdtzg94bs52lqrts5sssdr0000gp/T/jdk.incubator.jpackage13637540241510693609/images/image-5430644754281798291/Logisim-Evolution.app/Contents/MacOS/libapplauncher.dylib]
#   error: The specified item could not be found in the keychain.
#   java.io.IOException: Command [codesign, -s, Developer ID Application: Kevin Walsh (GDM3S3ULJA), --prefix, edu.holycross.cs.kwalsh.logisim, -vvvv, /var/folders/kb/zswdtzg94bs52lqrts5sssdr0000gp/T/jdk.incubator.jpackage13637540241510693609/images/image-5430644754281798291/Logisim-Evolution.app/Contents/MacOS/libapplauncher.dylib] exited with 1 code
#   	at jdk.incubator.jpackage/jdk.incubator.jpackage.internal.Executor.executeExpectSuccess(Executor.java:73)
#   	at jdk.incubator.jpackage/jdk.incubator.jpackage.internal.IOUtils.exec(IOUtils.java:179)
#   	at jdk.incubator.jpackage/jdk.incubator.jpackage.internal.IOUtils.exec(IOUtils.java:150)
#   	at jdk.incubator.jpackage/jdk.incubator.jpackage.internal.MacAppImageBuilder.lambda$signAppBundle$16(MacAppImageBuilder.java:804)
#   This, however, just means that the Developer ID Application and Developer ID Installer keys (not the certificates)
#   are missing from the keychain. This can be fixed by going into XCode, preferences, Keys, and create a new one of each.
#   Or, for x86 Macs, since XCode isn't available for that platform, from a Mac with the needed keys in the keychain, export
#   the keys (using a password to encrypt the files), copy the key files over to the x86 Mac, import them to the login keychain,
#   and try again.

set -e # die on error
#set -x # debug output

APPLE_TEAM_ID="GDM3S3ULJA"
APPLE_TEAM_ID_NAME="Developer ID Application: Kevin Walsh (${APPLE_TEAM_ID})"

if ! git diff --quiet --ignore-submodules -- \
   || [ -n "$(git ls-files --others --exclude-standard)" ]; then
  echo "** Working tree dirty (unstaged and/or untracked) **"
  exit 1
fi

arch=`uname -m`
if [ "$arch" == "arm64" ]; then
  ARCH_SUFFIX=""
  echo "#### Building MacOS release for arm64 (newer m1, m2, etc.)"
elif [ "$arch" == "x86_64" ]; then
  ARCH_SUFFIX="-x86"
  echo "#### Building MacOS release for x86 (older platforms)"
else
  echo "Unrecognized system architecture"
  exit 1
fi

VERSION_HC=`cat VERSION`
VERSION=`sed -E 's/^([0-9]+\.[0-9]+\.[0-9]+)-HC$/\1/' VERSION`
if [ "${VERSION}-HC" != "${VERSION_HC}" ]; then
  echo "Bad parse of VERSION file, format must be x.y.z-HC"
  exit 1
fi
echo "Release version: $VERSION Holy Cross Edition"

JAR=logisim-evolution-${VERSION}hc.jar

CRASH_LINK=`awk '/^issues:/ { print $2; }' contact.txt`
CRASH_EMAIL=`awk '/^contact:/ { print $2; }' contact.txt`
SRC_LINK=`awk '/^source:/ { print $2; }' contact.txt`
HOME_LINK=`awk '/^website:/ { print $2; }' contact.txt`
DOCS_LINK=`awk '/^docs:/ { print $2; }' contact.txt`
RELEASES_LINK=`awk '/^releases:/ { print $2; }' contact.txt`
echo "Crash contact email is: $CRASH_EMAIL"
echo "Crash contact link is: $CRASH_LINK"
echo "Source code link is: $SRC_LINK"
echo "Main webpage link is: $HOME_LINK"
echo "Online documentation link is: $DOCS_LINK"
echo "Releases link is: $RELEASES_LINK"

COPYRIGHT_YEAR=`sed -n '1p' COPYRIGHT_YEAR`
echo "Copyright year is: $COPYRIGHT_YEAR"

JAVA_RUNTIME="java-runtime-mac"

# Using list-deps is recommended by one tutorial, but it seems to over-estimate
# the modules needed. Perhaps it (harmlessly)includes transitive dependencies?
# MODULES=`jdeps --list-deps $JAR | paste -d, -s`
# MODULES=java.base,java.datatransfer,java.desktop,java.logging,java.prefs,java.xml

# Using print-module-deps appears to be the correct way to get the dependencies.
echo "Detecting ignored java modules..."
DETECTED_MISSING=`jdeps --print-module-deps $JAR | awk '/not found$/ { print $3; }' | cut -d. -f1 | sort -u | paste -d, -s -`
MISSING="android"
echo "Detected ignored java modules: ${DETECTED_MISSING}"

if [ "${DETECTED_MISSING}" != "${MISSING}" ]; then
  echo "ERROR: This differs from expected!"
  echo "     : Module dependencies must have changed!"
  echo "     : First, confirm whether it is okay to ignore these missing modules."
  echo "     : If so, within build-packager.sh, set MISSING=\"${DETECTED_MISSING}\""
  echo "     : Then delete ./${JAVA_RUNTIME} and try running this again."
  exit 1
fi

echo "Detecting java module dependencies..."
DETECTED_MODULES=`jdeps --print-module-deps --ignore-missing-deps $JAR`
MODULES="java.base,java.desktop,java.logging,java.management,java.net.http,java.prefs,jdk.httpserver"
echo "Detected java module dependencies: ${DETECTED_MODULES}"
  
if [ "${DETECTED_MODULES}" != "${MODULES}" ]; then
  echo "ERROR: This differs from expected!"
  echo "     : Module dependencies must have changed!"
  echo "     : Within build-packager.sh, set MODULES=\"${DETECTED_MODULES}\""
  echo "     : Then delete ./${JAVA_RUNTIME} and try running this again."
  exit 1
fi

if [ ! -e "${JAVA_RUNTIME}" ]; then
  echo "Building custom java runtime (using jlink)..."
  jlink --no-header-files --no-man-pages --compress=2 --strip-debug \
        --add-modules "${MODULES}" --output "${JAVA_RUNTIME}"
else
  echo "Using previously-built custom java runtime (from jlink)."
fi

INSTALLER_TYPE="pkg" # Options: dmg or pkg
OUTPUT="."
FILE_ASSOCIATIONS="file-associations.properties"
APP_ICON="logisim.icns"
JAVA_APP_IDENTIFIER="edu.holycross.cs.kwalsh.logisim"


# Prepare input files
echo "Preparing input files..."
rm -rf mac-staging
mkdir -p mac-staging
cp LICENSE "${JAR}" mac-staging/

# jSerialComm includes jnilib native code, which must be signed, or removed
(
  cd mac-staging

  # remove non-OSX native libs
  jar tf "$JAR" | grep -E '\.(dll|so)$' > native-libs-other.txt
  if [ ! -s "native-libs-other.txt" ]; then
    echo "Note: No non-OSX native libs found in $JAR"
  else
    echo "Removing non-OSX native libs from $JAR ..."
    sed 's/^/ *  /' native-libs-other.txt 
    zip -q -d "${JAR}" '*.dll' '*.so'
  fi
  rm -f native-libs-other.txt

  if [ "$ARCH_SUFFIX" == "-x86" ]; then
    jar tf "$JAR" | grep -E '\.(jnilib|dylib)$' | grep -E '^OSX/(x86|x86_64)/' > native-libs.txt
    jar tf "$JAR" | grep -E '\.(jnilib|dylib)$' | grep -E -v '^OSX/(x86|x86_64)/' > native-libs-other.txt
  else
    jar tf "$JAR" | grep -E '\.(jnilib|dylib)$' | grep '^OSX/aarch64/' > native-libs.txt
    jar tf "$JAR" | grep -E '\.(jnilib|dylib)$' | grep -v '^OSX/aarch64/' > native-libs-other.txt
  fi

  if [ ! -s "native-libs-other.txt" ]; then
    echo "Note: No mismatched-architecture OSX native libs found in $JAR"
  else
    echo "Removing mismatched-architecture OSX native libs from $JAR ..."
    while IFS= read -r path; do
      echo " *  $path"
      zip -q -d "$JAR" "$path"
    done < native-libs-other.txt
  fi
  rm -f native-libs-other.txt

  if [ ! -s "native-libs.txt" ]; then
    echo "Note: No matched-architecture OSX native libs found in $JAR"
  else

    echo "Extracting matched-architecture OSX native libs from $JAR ..."
    mkdir -p native-libs
    while IFS= read -r path; do
      echo " *  $path"
      ( cd "native-libs" && jar xf "../$JAR" "$path" )
    done < native-libs.txt

    echo "Removing apple quarantine flags, if present ..."
    xattr -dr com.apple.quarantine "native-libs" || true

    echo "Signing native libs ..."
    while IFS= read -r path; do
      f="native-libs/$path"
      codesign --force --timestamp --options runtime --sign "$APPLE_TEAM_ID_NAME" "$f"
      codesign --verify --verbose=2 "$f"
    done < native-libs.txt

    echo "Repackaging native libs ..."
    while IFS= read -r path; do
      jar uf "$JAR" -C "native-libs" "$path"
    done < native-libs.txt

    rm -rf native-libs
  fi
  rm -f native-libs.txt
)

# Prepare installer customizations: background image, postinstall script
echo "Preparing installer customizations..."
rm -rf mac-resources
mkdir -p mac-resources
cp mac-installer-background.png mac-resources/Logisim-Evolution-background.png

cat <<END >> mac-resources/postinstall
#!/bin/bash
chmod -R go+rX INSTALL_LOCATION/Logisim-Evolution.app
END
chmod a+rx mac-resources/postinstall

# Build the app and installer package
echo "Building ${INSTALLER_TYPE} ..."
PACKAGER=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin/jpackage
${PACKAGER} \
  --type ${INSTALLER_TYPE} \
  --input mac-staging \
  --dest "${OUTPUT}" \
  --name "Logisim-Evolution" \
  --main-class com.cburch.logisim.Main \
  --main-jar "${JAR}" \
  --java-options "--add-opens=java.desktop/com.apple.eawt.event=ALL-UNNAMED --enable-native-access=ALL-UNNAMED" \
  --app-version "${VERSION}" \
  --copyright "(c) ${COPYRIGHT_YEAR} Kevin Walsh" \
  --description "Digital logic designer and simulator." \
  --vendor "Kevin Walsh" \
  --runtime-image "${JAVA_RUNTIME}" \
  --icon "${APP_ICON}" \
  --mac-package-identifier "Logisim-Evolution-HC" \
  --mac-package-name "Logisim HC" \
  --mac-sign \
  --file-associations "${FILE_ASSOCIATIONS}" \
  --mac-package-signing-prefix "${JAVA_APP_IDENTIFIER}" \
  --resource-dir mac-resources \
  --license-file LICENSE \
  --verbose
# --add-modules "${MODULES}"

rm -rf mac-staging
rm -rf mac-resources
mv "Logisim-Evolution-${VERSION}.pkg" "Logisim-Evolution-${VERSION}-HC${ARCH_SUFFIX}.pkg"

cat <<ENDNOTE

# To notarize, run this command:

ALTOOLPW=enter-app-specific-password-here
xcrun notarytool submit --apple-id "kwalsh@holycross.edu" --team-id "$APPLE_TEAM_ID" --password "\$ALTOOLPW" Logisim-Evolution-${VERSION}-HC${ARCH_SUFFIX}.pkg

# Then later, try:
xcrun notarytool history --apple-id "kwalsh@holycross.edu" --team-id "$APPLE_TEAM_ID" --password "\$ALTOOLPW"

# And if that works, then try:
SUBMISSION_ID=whatever-from-previous-command
xcrun notarytool info --apple-id "kwalsh@holycross.edu" --team-id "$APPLE_TEAM_ID" --password "\$ALTOOLPW" "\$SUBMISSION_ID"

# And if that works, then try:
xcrun notarytool log --apple-id "kwalsh@holycross.edu" --team-id "$APPLE_TEAM_ID" --password "\$ALTOOLPW" "\$SUBMISSION_ID" > mac-notarize${ARCH_SUFFIX}.log
cat mac-notarize${ARCH_SUFFIX}.log

# Check for warnings and errors, then finally:
xcrun stapler staple Logisim-Evolution-${VERSION}-HC${ARCH_SUFFIX}.pkg

ENDNOTE
