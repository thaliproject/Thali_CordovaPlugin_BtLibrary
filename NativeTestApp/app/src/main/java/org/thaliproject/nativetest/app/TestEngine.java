/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativetest.app;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import org.thaliproject.nativetest.app.fragments.LogFragment;
import org.thaliproject.nativetest.app.test.AbstractTest;
import org.thaliproject.nativetest.app.test.FindMyBluetoothAddressTest;
import org.thaliproject.nativetest.app.test.FindPeersTest;
import org.thaliproject.nativetest.app.test.TestListener;
import org.thaliproject.p2p.btconnectorlib.ConnectionManager;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManager;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * A connection engine to run tests.
 */
public class TestEngine extends ConnectionEngine implements TestListener {
    private static final String TAG = TestEngine.class.getName();
    private static List<AbstractTest> mTests = new ArrayList<AbstractTest>();
    private TestListener mListener = null;
    private AbstractTest mCurrentTest = null;

    /**
     * Constructor.
     *
     * @param context  The application context.
     * @param activity The activity.
     * @param listener The test listener.
     */
    public TestEngine(Context context, Activity activity, TestListener listener) {
        super(context, activity);
        mListener = listener;

        mTests.add(new FindMyBluetoothAddressTest(this, this));
        mTests.add(new FindPeersTest(this, this));
    }

    /**
     * @return The tests.
     */
    public static List<AbstractTest> getTests() {
        return mTests;
    }

    /**
     * @return The discovery manager instance.
     */
    public DiscoveryManager getDiscoveryManager() {
        return mDiscoveryManager;
    }

    /**
     * Runs the given test.
     *
     * @param testToRun The test to runTest.
     * @return True, if the test was started successfully. False otherwise.
     */
    public boolean runTest(AbstractTest testToRun) {
        boolean wasStarted = false;

        if (mCurrentTest == null || !mCurrentTest.isRunning()) {
            if (testToRun != null) {
                Log.i(TAG, "runTest: " + testToRun.getName());
                onTestStarting(testToRun.getName());
                mCurrentTest = testToRun;
                mCurrentTest.setListener(this);
                wasStarted = mCurrentTest.run();

                if (!wasStarted) {
                    Log.e(TAG, "runTest: Failed to run test \"" + mCurrentTest.getName() + "\", cancelling...");
                    mCurrentTest.cancel();
                    mCurrentTest = null;
                }
            } else {
                Log.e(TAG, "runTest: The given test is null");
            }
        } else {
            Log.e(TAG, "runTest: Cannot runTest since a previous test is still running");
        }

        return wasStarted;
    }

    /**
     * Cancels the current test.
     */
    public void cancel() {
        if (mCurrentTest != null && mCurrentTest.isRunning()) {
            Log.i(TAG, "cancel: Cancelling test \"" + mCurrentTest.getName() + "\"...");
            mCurrentTest.cancel();
        }
    }

    /**
     * @return The connection manager instance.
     */
    public ConnectionManager getConnectionManager() {
        return mConnectionManager;
    }

    @Override
    public void onTestStarting(String testName) {
        if (mListener != null) {
            mListener.onTestStarting(testName);
        }
    }

    /**
     * Dumps the test results to logcat, stops this engine (the connection and discovery manager)
     * and notifies the listener.
     *
     * @param testName    The name of the test.
     * @param successRate The success rate (1.0 is 100 %).
     * @param results     The test results.
     */
    @Override
    public void onTestFinished(String testName, float successRate, String results) {
        Log.i(TAG, "Test \"" + testName + "\" finished with success rate "
                + Math.round(successRate * 100) + " % - Results:");
        Log.i(TAG, results);

        stop();

        if (mListener != null) {
            mListener.onTestFinished(testName, successRate, results);
        }

        mCurrentTest = null;
    }

    @Override
    public void onTestStarting() {

    }

    @Override
    public void onTestFinished() {

    }

    /**
     * @param state         The new state.
     * @param isDiscovering True, if peer discovery is active. False otherwise.
     * @param isAdvertising True, if advertising is active. False otherwise.
     */
    @Override
    public void onDiscoveryManagerStateChanged(
            DiscoveryManager.DiscoveryManagerState state,
            boolean isDiscovering, boolean isAdvertising) {
        if (mCurrentTest != null) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("TEST ENGINE: Discovery manager state changed: ");
            stringBuilder.append(state);
            stringBuilder.append(", ");
            stringBuilder.append(isDiscovering ? "discovering/scanning" : "not discovering/scanning");
            stringBuilder.append(", ");
            stringBuilder.append(isAdvertising ? "advertising" : "not advertising");
            String message = stringBuilder.toString();
            LogFragment.logTestEngineMessage(message);
            MainActivity.showToast(message);
        }
    }

    @Override
    public void onPeerDiscovered(PeerProperties peerProperties) {
        Log.d(TAG, "onPeerDiscovered: " + peerProperties.toString());

        if (mCurrentTest instanceof DiscoveryManager.DiscoveryManagerListener) {
            ((DiscoveryManager.DiscoveryManagerListener) mCurrentTest).onPeerDiscovered(peerProperties);
        }
    }

    @Override
    public void onPeerUpdated(PeerProperties peerProperties) {
        Log.d(TAG, "onPeerUpdated: " + peerProperties.toString());

        if (mCurrentTest instanceof DiscoveryManager.DiscoveryManagerListener) {
            ((DiscoveryManager.DiscoveryManagerListener) mCurrentTest).onPeerUpdated(peerProperties);
        }
    }

    @Override
    public void onPeerLost(PeerProperties peerProperties) {
        Log.d(TAG, "onPeerLost: " + peerProperties.toString());

        if (mCurrentTest instanceof DiscoveryManager.DiscoveryManagerListener) {
            ((DiscoveryManager.DiscoveryManagerListener) mCurrentTest).onPeerLost(peerProperties);
        }
    }

    @Override
    public void onBluetoothMacAddressResolved(String bluetoothMacAddress) {
        Log.i(TAG, "onBluetoothMacAddressResolved: " + bluetoothMacAddress);

        if (mCurrentTest instanceof DiscoveryManager.DiscoveryManagerListener) {
            ((DiscoveryManager.DiscoveryManagerListener) mCurrentTest).onBluetoothMacAddressResolved(bluetoothMacAddress);
        }
    }


}
