#!/bin/bash
export JAVA_HOME=/home/z/jdk-21
export ANDROID_HOME=/home/z/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
export GRADLE_OPTS="-Dorg.gradle.daemon=false"
cd /home/z/my-project/veritas-android
exec java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon "$@"
