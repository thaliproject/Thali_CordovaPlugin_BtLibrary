package org.thaliproject.nativetest.app.test.connection;

import android.util.Log;

import org.thaliproject.nativetest.app.test.Timer;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

/**
 * Created by evabishchevich on 1/4/17.
 */

class PeerTestData {

    private final Timer timer;
    final PeerProperties peerProperties;
    final Result result;
    final Object monitor = new Object();
    private volatile int connectCount = 0;
    private volatile int retryCount;


    PeerTestData(PeerProperties peerProperties, Timer timer, Result result) {
        this.timer = timer;
        this.peerProperties = peerProperties;
        this.result = result;
    }

    void startAttempt() {
        resetRetries();
        timer.start();
    }

    void finishAttempt(boolean success) {
        long duration = timer.finish();
        Attempt attempt = new Attempt(success, duration, retryCount + 1);
        Log.d("PeerTestData", attempt.toString());
        result.addAttempt(attempt);
    }

    private void resetRetries() {
        retryCount = 0;
    }

    int getRetryCount() {
        return retryCount;
    }

    void increaseRetries() {
        retryCount++;
    }

    int getConnectCount() {
        return connectCount;
    }

    void increaseConnectCount() {
        connectCount++;
    }

}
