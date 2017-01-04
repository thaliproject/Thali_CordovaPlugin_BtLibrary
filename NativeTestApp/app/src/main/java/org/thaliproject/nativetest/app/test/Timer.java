package org.thaliproject.nativetest.app.test;

import android.os.SystemClock;

/**
 * Created by evabishchevich on 1/4/17.
 */

public class Timer {

    private volatile long startTime;

    public void start() {
        startTime = SystemClock.elapsedRealtime();
    }

    public long finish() {
        if (startTime == 0) {
            throw new IllegalArgumentException("\"Finish\" called before \"start\" method");
        }
        long result = SystemClock.elapsedRealtime() - startTime;
        resetTime();
        return result;
    }

    private void resetTime() {
        startTime = 0;
    }
}
