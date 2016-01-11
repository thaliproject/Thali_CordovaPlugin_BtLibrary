/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativetest.app;

import android.content.Context;
import android.util.Log;
import org.thaliproject.nativetest.app.model.PeerAndConnectionModel;
import org.thaliproject.nativetest.app.model.Settings;
import org.thaliproject.nativetest.app.test.AbstractTest;
import org.thaliproject.nativetest.app.test.TestListener;
import org.thaliproject.p2p.btconnectorlib.ConnectionManager;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManager;

/**
 * A connection engine to runTest tests.
 */
public class TestEngine extends ConnectionEngine implements TestListener {
    private static final String TAG = TestEngine.class.getName();
    private TestListener mListener = null;
    private AbstractTest mCurrentTest = null;

    public TestEngine(Context context, TestListener listener) {
        super(context);
        mListener = listener;
    }

    /**
     * Runs the given test.
     * @param testToRun The test to runTest.
     * @return True, if the test was started successfully. False otherwise.
     */
    public boolean runTest(AbstractTest testToRun) {
        boolean wasStarted = false;

        if (mCurrentTest == null || !mCurrentTest.isRunning()) {
            if (testToRun != null) {
                mCurrentTest = testToRun;
                mCurrentTest.setListener(this);
                wasStarted = mCurrentTest.run();
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
            mCurrentTest.cancel();
        }
    }

    @Override
    public void onTestFinished(String testName, float successRate, String results) {
        if (mListener != null) {
            mListener.onTestFinished(testName, successRate, results);
        }
    }
}
