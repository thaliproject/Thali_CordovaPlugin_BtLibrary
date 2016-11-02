/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativetest.app.test;

/**
 * An interface for test listeners.
 */
public interface TestListener {
    /**
     * Called when a test is about to start.
     * @param testName The name of the test.
     */
    void onTestStarting(String testName);

    /**
     * Called when a test is runTest or aborted.
     * @param testName The name of the test.
     * @param successRate The success rate (1.0 is 100 %).
     * @param results The test results.
     */
    void onTestFinished(String testName, float successRate, String results);

    /**
     * Called when a test is about to start.
     */
    void onTestStarting();

    /**
     * Called when a test is runTest or aborted.
     */
    void onTestFinished();
}
