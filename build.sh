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

# The build has sometimes failed with the default value of maximum open
# files per process, which is 256. Doubling it here to 512 to workaround
# that issue.
ulimit -n 512;ERROR_ABORT

PROJECT_ROOT=$(pwd)

# A hack to workaround an issue where the install scripts assume that the
# folder of the Thali Cordova BT Library plugin is called exactly Thali_CordovaPlugin_BtLibrary,
# but this isn't always the case in the CI.
THALI_BT_DIRECTORY="../Thali_CordovaPlugin_BtLibrary"
if [ ! -d "$THALI_BT_DIRECTORY" ]
then
  cp -R . $THALI_BT_DIRECTORY;ERROR_ABORT
  cd $THALI_BT_DIRECTORY;ERROR_ABORT
fi

RUN_IN_CI=$?

cd BtConnectorLib
# Build the library
./gradlew assembleRelease;ERROR_ABORT

# Build APK for android tests
./gradlew assembleReleaseAndroidTest;ERROR_ABORT

# Run unit tests
./gradlew testRelease

# Back to root
cd $PROJECT_ROOT;ERROR_ABORT

if [ $RUN_IN_CI == 0 ]
then
  # A hack workround due to the fact that CI server doesn't allow relative paths outside
  # of the original parent folder as a path to the build output binaries.
  rm -rf btconnectorlib2-release-androidTest-unsigned.apk;ERROR_ABORT
  cp -R BtConnectorLib/btconnectorlib2/build/outputs/apk/btconnectorlib2-release-androidTest-unsigned.apk btconnectorlib2-release-androidTest-unsigned.apk;ERROR_ABORT
fi
