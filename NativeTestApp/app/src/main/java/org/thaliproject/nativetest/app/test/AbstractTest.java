/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativetest.app.test;

/**
 * An abstract base class for tests.
 */
public abstract class AbstractTest {
    public static final long DEFAULT_TEST_TIMEOUT_IN_MILLISECONDS = 30000;
    public static final int DEFAULT_NUMBER_OF_DESIRED_PEERS = 1;

    protected long mTestTimeoutInMilliseconds = DEFAULT_TEST_TIMEOUT_IN_MILLISECONDS;
    protected int mNumberOfDesiredPeers = DEFAULT_NUMBER_OF_DESIRED_PEERS;

    /**
     * @return The test timeout in milliseconds.
     */
    public long getTestTimeout() {
        return mTestTimeoutInMilliseconds;
    }

    /**
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

    /**
     * Starts the test.
     * @return True, if successfully started. False otherwise.
     */
    public abstract boolean run();

    /**
     * Cancels the test.
     */
    public void cancel() {
    }
}
