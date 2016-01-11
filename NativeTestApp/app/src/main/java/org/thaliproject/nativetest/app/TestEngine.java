/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativesample.app;

import android.content.Context;

/**
 * A connection engine to run tests.
 */
public class TestEngine extends ConnectionEngine {
    public interface Listener {
        void onTestFinished();
    }

    public TestEngine(Context context) {
        super(context);
    }
}
