#!/bin/sh
set -e
GRADLE_OPTS="$GRADLE_OPTS -Dorg.gradle.jvmargs=-Xmx2048m"
exec gradle "$@"
