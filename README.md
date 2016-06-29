# Thali Android Connector Library #

This Android library is part of [Thali Project](http://thaliproject.org/) and
provides means to discover other peer-to-peer devices and establish connections
using insecure RFCOMM (Bluetooth) sockets.

This project is intended to be used with the
[Thali Cordova Plug-in](https://github.com/thaliproject/Thali_CordovaPlugin),
but can be used to build native Android applications.

## Usage ##

### Prerequisites ###

Download and install Maven: http://maven.apache.org/download.cgi

In case you decide to use the development phase versions (not the version in the
master branch), you may need to compile the library into your local Maven
repository.

The version in the master branch can be found from Bintray repository:
https://bintray.com/thali

### Building the library ###

1. Using command line tool, navigate to location where you have the library
   cloned or downloaded
2. Go to `BtConnectorLib` folder, where you should find `gradlew` and
   `gradlew.bat` files
3. Build and install the library into your local Maven repository:

    **Windows:**
    ```
    gradlew build install
    ```
    
    **Linux/Mac:**
    ```
    ./gradlew build install
    ```
    
    * If you run into a permission denied issue in Linux or Mac, make sure that
      the `gradlew` file has execution permission; run command
      `chmod 744 gradlew`

If the library was built and installed successfully, you should now see the
library in your local Maven repository:
```
<user folder>\.m2\repository\org\thaliproject\p2p\btconnectorlib\btconnectorlib2\<version number>
```
 
### Code of Conduct
This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/). For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.
