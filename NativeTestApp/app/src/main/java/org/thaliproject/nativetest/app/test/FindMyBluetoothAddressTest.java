/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativetest.app.test;

import android.util.Log;

/**
 * A test for requesting a peer to provide us our Bluetooth MAC address.
 */
public class FindMyBluetoothAddressTest extends AbstractTest {
    private static final String TAG = FindMyBluetoothAddressTest.class.getName();

    @Override
    public String getName() {
        return "Find my Bluetooth address";
    }

    @Override
    public boolean run() {
        Log.i(TAG, "runTest");
        super.run();

        if (mListener != null) {
            mListener.onTestFinished(getName(), 0f, "no results");
        }

        return false;
    }
}
