#!/usr/bin/env bash
# Runs the project's Gradle gate inside an eclipse-temurin:21-jdk container (see run-tests.bat).
# The Android SDK and Gradle home live in named Docker volumes, so only the first run downloads them.
set -uo pipefail
LOG=/project/.docker-test.log
exec > >(tee "$LOG") 2>&1

export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
TASKS="${GRADLE_TASKS:-}"
[ -z "${TASKS// /}" ] && TASKS=":app:testDebugUnitTest :app:assembleDebugAndroidTest :app:assembleDebug"

echo "== tools"
apt-get update -qq >/dev/null && apt-get install -y -qq unzip git curl >/dev/null || { echo "apt failed"; exit 1; }

if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "== downloading Android command-line tools"
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    for v in 13114758 11076708; do
        curl -fsSL -o /tmp/clt.zip "https://dl.google.com/android/repository/commandlinetools-linux-${v}_latest.zip" && break
    done
    unzip -q /tmp/clt.zip -d /tmp/clt && rm -rf "$ANDROID_HOME/cmdline-tools/latest" && mv /tmp/clt/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
fi
SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
echo "== SDK packages"
yes | "$SDKM" --licenses >/dev/null 2>&1
"$SDKM" "platforms;android-36" "build-tools;36.0.0" "platform-tools" | grep -v '^\[' || true

git config --global --add safe.directory /project
cd /project
echo "== gradle $TASKS"
# Invoked via java rather than ./gradlew: the working-tree gradlew has CRLF line endings.
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
    --no-daemon --console=plain --project-cache-dir /tmp/gradle-project-cache $TASKS
code=$?
echo "GRADLE EXIT CODE: $code"
exit $code
