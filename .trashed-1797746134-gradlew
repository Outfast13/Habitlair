#!/bin/sh
set -e
APP_BASE_NAME=`basename "$0"`
APP_HOME="`pwd -P`"
GRADLE_OPTS="$GRADLE_OPTS -Dorg.gradle.jvmargs=-Xmx2048m"
exec gradle "$@"
