/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativetest.app.test;

import android.util.Log;
import org.thaliproject.nativetest.app.TestEngine;
import org.thaliproject.nativetest.app.model.PeerAndConnectionModel;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManager;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

/**
 * Test for discovering peers.
 */
public class FindPeersTest extends AbstractTest implements DiscoveryManager.DiscoveryManagerListener {
    private static final String TAG = FindPeersTest.class.getName();
    private DiscoveryManager mDiscoveryManager = null;
    private PeerAndConnectionModel mModel = null;
    private int mNumberOfPeersDiscovered = 0;

    public FindPeersTest(TestEngine testEngine, TestListener listener) {
        super(testEngine, listener);
    }

    @Override
    public String getName() {
        return "Find peers";
    }

    @Override
    public boolean run() {
        Log.i(TAG, "run");
        super.run();
        boolean wasStarted = false;

        mDiscoveryManager = mTestEngine.getDiscoveryManager();
        mModel = PeerAndConnectionModel.getInstance();
        return mDiscoveryManager.start(TestEngine.PEER_NAME);
    }

    @Override
    public void finalize() {
        Log.i(TAG, "finalize");
        super.finalize();

        if (mListener != null) {
            float successRate = 0f;

            if (mNumberOfDesiredPeers != 0) {
                successRate = (float)mNumberOfPeersDiscovered / (float)mNumberOfDesiredPeers;
            }

            String results = mNumberOfPeersDiscovered + " peer(s) discovered";
            mListener.onTestFinished(getName(), successRate, results);
        }
    }

    @Override
    public void onDiscoveryManagerStateChanged(DiscoveryManager.DiscoveryManagerState discoveryManagerState) {
        // Not used
    }

    @Override
    public void onPeerDiscovered(PeerProperties peerProperties) {
        if (mModel.addOrUpdatePeer(peerProperties)) {
            mNumberOfPeersDiscovered++;
            Log.i(TAG, "onPeerDiscovered: Number of discovered peers is now " + mNumberOfPeersDiscovered);
        }

        if (mNumberOfPeersDiscovered >= mNumberOfDesiredPeers) {
            Log.i(TAG, "onPeerDiscovered: The target number of discovered peers reached ("
                    + mNumberOfDesiredPeers + "), test done!");
            finalize();
        }
    }

    @Override
    public void onPeerUpdated(PeerProperties peerProperties) {
        // Not used
    }

    @Override
    public void onPeerLost(PeerProperties peerProperties) {
        // Not used
    }
}
