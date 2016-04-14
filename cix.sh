#!/bin/bash
set -euo pipefail
echo "Running with SQ=$SQ_VERSION"

 
cd its
mvn -Dsonar.runtimeVersion="$SQ_VERSION" -Dmaven.test.redirectTestOutputToFile=false verify -B -e -V



