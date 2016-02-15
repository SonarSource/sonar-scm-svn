#!/bin/bash

set -euo pipefail

function configureTravis {
  mkdir ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v27 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install                                                                                                                                    
}
configureTravis

case "$TARGET" in

CI)
  regular_mvn_build_deploy_analyze
  ;;

IT)
  start_xvfb

  mvn verify -Dsource.skip=true -Denforcer.skip=true -Danimal.sniffer.skip=true -Dmaven.test.skip=true -B -e -V

  cd its
  mvn -Dsonar.runtimeVersion="$SQ_VERSION" -Dmaven.test.redirectTestOutputToFile=false verify -B -e -V
  ;;

*)
  echo "Unexpected TARGET value: $TARGET"
  exit 1
  ;;

esac
