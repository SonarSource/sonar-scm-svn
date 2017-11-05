#!/bin/bash
set -euo pipefail
echo "Running with SQ=$SQ_VERSION"

export JAVA_HOME=/opt/sonarsource/jvm/java-1.9.0-sun-x64
export PATH=$JAVA_HOME/bin:$PATH
 
cd its
mvn -Dsonar.runtimeVersion="$SQ_VERSION" -Dmaven.test.redirectTestOutputToFile=false verify -B -e -V



