/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativetest.app.test;

import android.os.CountDownTimer;
import android.util.Log;

/**
 * An abstract base class for tests.
 */
public abstract class AbstractTest {
    public static final long DEFAULT_TEST_TIMEOUT_IN_MILLISECONDS = 30000;
    public static final int DEFAULT_NUMBER_OF_DESIRED_PEERS = 1;
    protected static final String TAG = AbstractTest.class.getName();

    protected long mTestTimeoutInMilliseconds = DEFAULT_TEST_TIMEOUT_IN_MILLISECONDS;
    protected int mNumberOfDesiredPeers = DEFAULT_NUMBER_OF_DESIRED_PEERS;
    protected TestListener mListener = null;
    protected CountDownTimer mTestTimeoutTimer = null;
    protected boolean mIsRunning = false;

    public abstract String getName();

    /**
     * @return The test timeout in milliseconds.
     */
    public long getTestTimeout() {
        return mTestTimeoutInMilliseconds;
    }

    /**
     * Sets the test timeout. Note that you should do this before starting the test (calling runTest())
     * in order for the given value to have an effect.
     * @param testTimeoutInMilliseconds The test timeout in milliseconds.
     */
    public void setTestTimeout(long testTimeoutInMilliseconds) {
        mTestTimeoutInMilliseconds = testTimeoutInMilliseconds;
    }

    public int getNumberOfDesiredPeers() {
        return mNumberOfDesiredPeers;
    }

    public void setNumberOfDesiredPeers(int numberOfDesiredPeers) {
        mNumberOfDesiredPeers = numberOfDesiredPeers;
    }

    public void setListener(TestListener listener) {
        mListener = listener;
    }

    public boolean isRunning() {
        return mIsRunning;
    }

    /**
     * Starts the test.
     * @return True, if successfully started. False otherwise.
     */
    public boolean run() {
        mIsRunning = true;

        mTestTimeoutTimer = new CountDownTimer(mTestTimeoutInMilliseconds, mTestTimeoutInMilliseconds) {
            @Override
            public void onTick(long l) {
                // Not used
            }

            @Override
            public void onFinish() {
                Log.d(TAG, "Test timeout");
                onTimeout();
            }
        };

        mTestTimeoutTimer.start();
        return false;
    }


    /**
     * Cancels the test.
     */
    public void cancel() {
        finalize();
    }

    protected void onTimeout() {
        cancel();
    }

    protected void finalize() {
        mIsRunning = false;
    }
}
