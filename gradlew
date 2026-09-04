#!/bin/sh
# Gradle start up script for POSIX
app_path=$0
while [ -h "$app_path" ]; do
  ls=$(ls -ld "$app_path")
  link=${ls#*' -> '}
  case $link in
    /*) app_path=$link ;;
    *) app_path=$(dirname "$app_path")/$link ;;
  esac
done
APP_HOME=$(cd "$(dirname "$app_path")" && pwd -P) || exit
APP_BASE_NAME=${0##*/}
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ]; then
  JAVACMD=$JAVA_HOME/bin/java
else
  JAVACMD=java
fi

exec "$JAVACMD" \
  "-Dorg.gradle.appname=$APP_BASE_NAME" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
