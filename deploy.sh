#!/usr/bin/env bash
# if you want to publish a release, make sure
# the version number in the project is a whole
# integer, not a `-SNAPSHOT` build. otherwise
# the build will be deployed to the staging repo
START_DIR=$(cd `dirname $0` && pwd )
echo $START_DIR


function update_demos() {
  NV=$1
  GRADLE_DEMO=$START_DIR/samples/gradle-demo
  MAVEN_DEMO=$START_DIR/samples/maven-demo
  pwd
  cd $START_DIR
  cd $MAVEN_DEMO && ./mvnw versions:set -DnewVersion=$NV
  pwd
  cd $START_DIR
  cd $GRADLE_DEMO && echo "$NV" > version.txt
  pwd
  cd $START_DIR
  pwd
}

echo "this script will prompt you for the GPG passphrase"
export GPG_TTY=$(tty)

## RELEASE
echo "setting release version..."
mvn build-helper:parse-version versions:set \
  -DnewVersion=\${parsedVersion.majorVersion}.\${parsedVersion.minorVersion}.\${parsedVersion.incrementalVersion}
RELEASE_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

echo "the release version is $RELEASE_VERSION "
echo "deploying..."
mvn versions:commit                           # accept the release version
mvn -DskipTests=true -P publish clean deploy  # deploy to maven central
update_demos "${RELEASE_VERSION}"
git commit -am "releasing ${RELEASE_VERSION}" # release the main version
TAG_NAME=v${RELEASE_VERSION}
git tag -a $TAG_NAME -m "release tag ${TAG_NAME}"
git push origin "$TAG_NAME"

## BACK TO THE LAB AGAIN
mvn build-helper:parse-version versions:set \
  -DnewVersion=\${parsedVersion.majorVersion}.\${parsedVersion.minorVersion}.\${parsedVersion.nextIncrementalVersion}-SNAPSHOT
echo "the next snapshot version is $(mvn help:evaluate -Dexpression=project.version -q -DforceStdout) "
mvn versions:commit
SNAPSHOT_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
update_demos $SNAPSHOT_VERSION
git commit -am "moving to $SNAPSHOT_VERSION"
git push
