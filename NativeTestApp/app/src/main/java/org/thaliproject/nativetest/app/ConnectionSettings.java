package org.thaliproject.nativetest.app;

import java.util.UUID;

/**
 * Created by evabishchevich on 1/4/17.
 */

public class ConnectionSettings {

    // Service type and UUID has to be application/service specific.
    // The app will only connect to peers with the matching values.
    public static final String SERVICE_TYPE = "ThaliTestSampleApp._tcp";
    public static final String SERVICE_UUID_AS_STRING = "b6a44ad1-d319-4b3a-815d-8b805a47fb51";
    public static final String SERVICE_NAME = "Thali_Bluetooth";
    public static final UUID SERVICE_UUID = UUID.fromString(SERVICE_UUID_AS_STRING);
}
