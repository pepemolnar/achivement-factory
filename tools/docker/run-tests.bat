@echo off
rem Runs the Gradle test gate in Docker (needs Docker Desktop running). Output also goes to .docker-test.log
rem Usage: run-tests.bat [gradle tasks]   default: unit tests + androidTest compile + debug APK
cd /d "%~dp0..\.."
docker run --rm -v "%cd%:/project" -v af-gradle-home:/root/.gradle -v af-android-sdk:/opt/android-sdk -e GRADLE_TASKS="%*" eclipse-temurin:21-jdk bash /project/tools/docker/test-in-docker.sh
echo.
echo Finished. Full output is in .docker-test.log - you can close this window.
pause
