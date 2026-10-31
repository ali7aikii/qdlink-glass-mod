#!/bin/bash
# build.sh - turns our edited QDLink files back into installable APK files.
# It rebuilds the main app from the "decoded" folder, compiles our new screens
# (the Java files in "mod-src") and adds them to the app as an extra code file,
# then lines up (zipalign) and signs (apksigner) the main APK and all 19 "config"
# split APKs with OUR key. The finished files land in the "out" folder.
# How to run:  bash /Users/alialhayki/testing/qdlink-mod/scripts/build.sh

# Stop the script as soon as any command fails, so we never ship a half-built app
set -e

# ---- Settings (constants) ----

# The main project folder that holds everything
PROJECT_FOLDER="/Users/alialhayki/testing/qdlink-mod"

# The untouched files we unpacked from the .xapk download
ORIGINAL_FOLDER="$PROJECT_FOLDER/original"

# The name of the main app file inside ORIGINAL_FOLDER
MAIN_APK_NAME="com.neusoft.qdrivelink.apk"

# The folder apktool unpacked the main app into (this is where we edit the design)
DECODED_FOLDER="$PROJECT_FOLDER/decoded"

# Our new Java code (Home screen, Clock screen, weather, now playing)
MOD_SOURCE_FOLDER="$PROJECT_FOLDER/mod-src"

# QDLink already has classes.dex and classes2.dex; our code becomes the third one
MOD_DEX_NAME="classes3.dex"

# A scratch folder for in-between files (deleted and recreated every build)
BUILD_FOLDER="$PROJECT_FOLDER/build"

# The folder with the finished, signed files for the phone
OUT_FOLDER="$PROJECT_FOLDER/out"

# Our own signing key file (created the first time you run this script)
KEYSTORE_FILE="$PROJECT_FOLDER/qdlink-mod.keystore"

# The name of the key inside the keystore file
KEY_ALIAS="qdlinkmod"

# Where Homebrew put Java (needed by javac, keytool, apktool and apksigner)
JAVA_FOLDER="/opt/homebrew/opt/openjdk"

# Where the Android tools were downloaded
ANDROID_SDK_FOLDER="/opt/homebrew/share/android-commandlinetools"

# The Android build tools (zipalign, apksigner and d8)
BUILD_TOOLS_FOLDER="$ANDROID_SDK_FOLDER/build-tools/35.0.0"

# The Android 15 library our Java code is compiled against
ANDROID_JAR="$ANDROID_SDK_FOLDER/platforms/android-35/android.jar"

# Our code must still run on Android 5.0 (API 21), like QDLink itself
LOWEST_ANDROID_API="21"

# Extra Java setting for apksigner that hides harmless "restricted method" warnings
APKSIGNER_JAVA_OPTION="-J-enable-native-access=ALL-UNNAMED"

# The signature check only looks at phones with Android 7.0 (API 24) or newer.
# Older checks print many harmless "not protected by signature" warnings.
CHECK_FROM_ANDROID_API="24"


# ---- Small helper functions ----

# Print a step title so you can follow along in the terminal
print_step() {
    echo ""
    echo "==== $1 ===="
}

# Stop with a plain-English error message
stop_with_error() {
    echo ""
    echo "ERROR: $1"
    exit 1
}

# Check that one program or file exists, or stop with a helpful message
check_tool_exists() {
    tool_path="$1"
    tool_name="$2"
    if [ -e "$tool_path" ]; then
        echo "Found $tool_name"
    else
        stop_with_error "$tool_name was not found at $tool_path. Install the tools first."
    fi
}

# Line up (zipalign) and sign (apksigner) one APK file
align_and_sign_apk() {
    input_apk="$1"
    apk_file_name=$(basename "$input_apk")
    aligned_apk="$BUILD_FOLDER/aligned-$apk_file_name"
    signed_apk="$OUT_FOLDER/$apk_file_name"

    # zipalign puts the files inside the APK on fixed boundaries so Android can read them fast.
    # -P 16 = line up native libraries for 16 KB memory pages (also works on 4 KB phones)
    "$BUILD_TOOLS_FOLDER/zipalign" -f -P 16 4 "$input_apk" "$aligned_apk"

    # apksigner removes the old signature and signs the file with our key.
    # The password is read from the QDLINK_KEY_PASSWORD variable, so you only type it once.
    "$BUILD_TOOLS_FOLDER/apksigner" "$APKSIGNER_JAVA_OPTION" sign \
        --ks "$KEYSTORE_FILE" \
        --ks-key-alias "$KEY_ALIAS" \
        --ks-pass env:QDLINK_KEY_PASSWORD \
        --key-pass env:QDLINK_KEY_PASSWORD \
        --out "$signed_apk" \
        "$aligned_apk"

    echo "Signed $apk_file_name"
}


# ---- The main steps ----

# ---- Step 1: make sure every tool we need is installed ----
print_step "Step 1: checking tools"
export JAVA_HOME="$JAVA_FOLDER"
export PATH="$JAVA_FOLDER/bin:$PATH"
check_tool_exists "$JAVA_FOLDER/bin/java" "Java"
check_tool_exists "$JAVA_FOLDER/bin/javac" "javac (Java compiler)"
check_tool_exists "$JAVA_FOLDER/bin/keytool" "keytool"
check_tool_exists "/opt/homebrew/bin/apktool" "apktool"
check_tool_exists "$BUILD_TOOLS_FOLDER/zipalign" "zipalign"
check_tool_exists "$BUILD_TOOLS_FOLDER/apksigner" "apksigner"
check_tool_exists "$BUILD_TOOLS_FOLDER/d8" "d8 (dex converter)"
check_tool_exists "$ANDROID_JAR" "Android 15 library (android.jar)"

# ---- Step 2: unpack the main app the first time (later runs keep your edits) ----
print_step "Step 2: checking the decoded folder"
if [ -d "$DECODED_FOLDER" ]; then
    echo "Decoded folder already exists, keeping your edits"
else
    echo "Unpacking $MAIN_APK_NAME (first run only)"
    # -s = leave the app's code (classes.dex) exactly as it is; we only change design files
    apktool d -s -o "$DECODED_FOLDER" "$ORIGINAL_FOLDER/$MAIN_APK_NAME"
fi

# ---- Step 3: ask for the key password once ----
print_step "Step 3: key password"
# -s hides what you type, like any password box
read -s -p "Type the password for your signing key: " QDLINK_KEY_PASSWORD
echo ""
if [ -z "$QDLINK_KEY_PASSWORD" ]; then
    stop_with_error "The password can not be empty."
fi
# Share the password only with the programs this script starts (it is never saved to a file)
export QDLINK_KEY_PASSWORD

# ---- Step 4: create our signing key the first time ----
print_step "Step 4: checking the signing key"
if [ -f "$KEYSTORE_FILE" ]; then
    echo "Signing key already exists"
else
    echo "Creating a new signing key (first run only)"
    keytool -genkeypair \
        -keystore "$KEYSTORE_FILE" \
        -alias "$KEY_ALIAS" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass:env QDLINK_KEY_PASSWORD \
        -keypass:env QDLINK_KEY_PASSWORD \
        -dname "CN=QDLink Personal Mod"
fi

# ---- Step 5: start with empty build and out folders ----
print_step "Step 5: cleaning old build files"
rm -rf "$BUILD_FOLDER" "$OUT_FOLDER"
mkdir -p "$BUILD_FOLDER" "$OUT_FOLDER"

# ---- Step 6: compile our new screens into an Android code file ----
print_step "Step 6: compiling the new Home and Clock screens"
classes_folder="$BUILD_FOLDER/mod-classes"
dex_folder="$BUILD_FOLDER/mod-dex"
mkdir -p "$classes_folder" "$dex_folder"

# javac turns our .java files into .class files (Java's compiled form).
# --release 11 = Java 11 style code, which the Android tools understand.
javac --release 11 -Xlint:-options -encoding UTF-8 \
    -cp "$ANDROID_JAR" \
    -d "$classes_folder" \
    "$MOD_SOURCE_FOLDER"/com/qdmod/*.java

# d8 turns the .class files into one classes.dex file (Android's compiled form)
class_files=$(find "$classes_folder" -name "*.class")
"$BUILD_TOOLS_FOLDER/d8" --release \
    --min-api "$LOWEST_ANDROID_API" \
    --lib "$ANDROID_JAR" \
    --output "$dex_folder" \
    $class_files

if [ -f "$dex_folder/classes.dex" ]; then
    cp "$dex_folder/classes.dex" "$BUILD_FOLDER/$MOD_DEX_NAME"
    echo "Compiled our code into $MOD_DEX_NAME"
else
    stop_with_error "d8 did not create classes.dex"
fi

# ---- Step 7: rebuild the main app from the decoded folder ----
print_step "Step 7: rebuilding the main app"
rebuilt_main_apk="$BUILD_FOLDER/$MAIN_APK_NAME"
# apktool keeps old results in decoded/build and may reuse them ("resources have not changed").
# Deleting that folder and using -f (force all) makes it rebuild everything from the current files.
rm -rf "$DECODED_FOLDER/build"
apktool b -f -o "$rebuilt_main_apk" "$DECODED_FOLDER"

# ---- Step 8: add our code file into the rebuilt app ----
# Android loads classes.dex, classes2.dex, classes3.dex ... automatically (Android 5.0 and newer)
print_step "Step 8: adding our code to the app"
# -j = store just the file name (no folder path) inside the APK
zip -j -q "$rebuilt_main_apk" "$BUILD_FOLDER/$MOD_DEX_NAME"
echo "Added $MOD_DEX_NAME"

# ---- Step 8b: show the version that was built, so you can match it in the installer ----
built_version=$("$BUILD_TOOLS_FOLDER/aapt2" dump badging "$rebuilt_main_apk" | grep "^package:" | sed -E "s/.*versionName='([^']*)'.*/\1/")
echo "Built version name: $built_version"

# ---- Step 9: check that the app really works as ONE file (no split parts needed) ----
# The language texts, the screen images and the native library were moved into the main app.
# Here we make sure nothing still says "extra parts required".
print_step "Step 9: checking the app is standalone"

# 9a: the native library must be inside the app and stored UNcompressed
# (the manifest says extractNativeLibs=false, so Android reads it straight from the file)
library_info=$(unzip -v "$rebuilt_main_apk" | grep "lib/arm64-v8a/libsspLib.so" || true)
if [ -z "$library_info" ]; then
    stop_with_error "libsspLib.so is missing from the app (lib/arm64-v8a)."
fi
case "$library_info" in
    *Stored*) echo "OK  libsspLib.so is inside the app and stored uncompressed" ;;
    *) stop_with_error "libsspLib.so is compressed, Android would refuse to load it." ;;
esac

# 9b: the manifest must not ask for split parts
manifest_text=$("$BUILD_TOOLS_FOLDER/aapt2" dump xmltree --file AndroidManifest.xml "$rebuilt_main_apk")
if echo "$manifest_text" | grep -q -E "requiredSplitTypes|isSplitRequired|vending.splits"; then
    stop_with_error "The manifest still asks for split parts."
else
    echo "OK  the manifest asks for no split parts"
fi

# ---- Step 10: align and sign the app ----
print_step "Step 10: signing the app"
align_and_sign_apk "$rebuilt_main_apk"

# ---- Step 11: check that the signed file is valid ----
print_step "Step 11: verifying the signature"
signed_apk="$OUT_FOLDER/$MAIN_APK_NAME"
if "$BUILD_TOOLS_FOLDER/apksigner" "$APKSIGNER_JAVA_OPTION" verify --min-sdk-version "$CHECK_FROM_ANDROID_API" "$signed_apk"; then
    echo "OK  $MAIN_APK_NAME"
else
    stop_with_error "The signature check failed."
fi

# apksigner also writes a small .idsig helper file; the phone does not need it
rm -f "$OUT_FOLDER"/*.idsig

# ---- Step 12: give the file a name with the build time in it ----
# (like QDLink-mod-20261031-1005.apk) so the phone can never mix up a new build with an older copy
print_step "Step 12: naming the file"
build_time=$(date +%Y%m%d-%H%M)
final_apk="$OUT_FOLDER/QDLink-mod-$build_time.apk"
mv "$signed_apk" "$final_apk"
echo "Created $final_apk"

# ---- Step 13: show what to copy to the phone ----
print_step "Done"
final_name=$(basename "$final_apk")
echo "Built version name: $built_version"
echo "Copy $final_name to your phone and tap it to install (no split installer needed)."
