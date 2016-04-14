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
  #regular_mvn_build_deploy_analyze
  SONAR_PROJECT_VERSION=`maven_expression "project.version"`
 
  # Do not deploy a SNAPSHOT version but the release version related to this build
  set_maven_build_version $TRAVIS_BUILD_NUMBER
 
  # the profile "deploy-sonarsource" is defined in parent pom v28+
  mvn org.jacoco:jacoco-maven-plugin:prepare-agent deploy \
    -Pdeploy-sonarsource \
    -B -e -V
  ;;

*)
  echo "Unexpected TARGET value: $TARGET"
  exit 1
  ;;

esac
