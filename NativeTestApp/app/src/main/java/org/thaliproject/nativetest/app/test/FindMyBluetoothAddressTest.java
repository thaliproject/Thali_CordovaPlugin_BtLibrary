/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativetest.app.test;

import android.util.Log;
import org.thaliproject.nativetest.app.TestEngine;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManager;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

/**
 * A test for requesting a peer to provide us our Bluetooth MAC address.
 */
public class FindMyBluetoothAddressTest
        extends AbstractTest
        implements DiscoveryManager.DiscoveryManagerListener {
    private static final String TAG = FindMyBluetoothAddressTest.class.getName();

    public FindMyBluetoothAddressTest(TestEngine testEngine, TestListener listener) {
        super(testEngine, listener);
    }

    @Override
    public String getName() {
        return "Find my Bluetooth MAC address";
    }

    @Override
    public boolean run() {
        Log.i(TAG, "run");
        super.run();

        return true;
    }

    @Override
    public void finalize() {
        super.finalize();

        if (mListener != null) {
            mListener.onTestFinished(getName(), 0f, "no results");
        }
    }

    @Override
    public void onDiscoveryManagerStateChanged(DiscoveryManager.DiscoveryManagerState discoveryManagerState) {

    }

    @Override
    public void onPeerDiscovered(PeerProperties peerProperties) {

    }

    @Override
    public void onPeerUpdated(PeerProperties peerProperties) {

    }

    @Override
    public void onPeerLost(PeerProperties peerProperties) {

    }
}
