# Thali_CordovaPlugin_BtLibrary

### Goals
This project is intended to be used with the https://github.com/thaliproject/Thali_CordovaPlugin plugin to implement the Android Bluetooth communication library needed for it.

### Usage
1. For command line build process, you should use gradle, thus you need to set system environment variable ANDROID_BUILD to gradle

2. for android implementation you need to build the p2p library to local maven (development time only)

Get maven and install it locally: http://maven.apache.org/download.cgi

get the library from: https://github.com/thaliproject/Thali_CordovaPlugin_BtLibrary

go to root of the library project and build with "gradlew build install" and the library should be visible in <user folder>\.m2\repository\org\thaliproject\p2p\btconnectorlib\btconnectorlib2\0.0.0
 
