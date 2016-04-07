#!/bin/sh

### START - JXcore Test Server --------.............................
### Testing environment prepares separate packages for each node.
### Package builder calls this script with each node's IP address
### Make sure multiple calls to this script file compiles the application file

NORMAL_COLOR='\033[0m'
RED_COLOR='\033[0;31m'
GREEN_COLOR='\033[0;32m'
GRAY_COLOR='\033[0;37m'

LOG() {
  COLOR="$1"
  TEXT="$2"
  echo -e "${COLOR}$TEXT ${NORMAL_COLOR}"
}


ERROR_ABORT() {
  if [[ $? != 0 ]]
  then
    LOG $RED_COLOR "compilation aborted\n"
    exit -1
  fi
}
### END - JXcore Test Server   --------

RUN_IN_CI=$?

cd BtConnectorLib
# Build the library
./gradlew assembleRelease;ERROR_ABORT

# Build APK for android tests
./gradlew assembleReleaseAndroidTest;ERROR_ABORT

# Run unit tests
./gradlew testRelease

# Back to root
cd ..;ERROR_ABORT

if [ $RUN_IN_CI == 0 ]
then
  # A hack workround due to the fact that CI server doesn't allow relative paths outside
  # of the original parent folder as a path to the build output binaries.
  rm -rf btconnectorlib2-release-androidTest-unsigned.apk;ERROR_ABORT
  cp -R BtConnectorLib/btconnectorlib2/build/outputs/apk/btconnectorlib2-release-androidTest-unsigned.apk btconnectorlib2-release-androidTest-unsigned.apk;ERROR_ABORT
fi
