package org.thaliproject.nativetest.app.test.connection;

/**
 * Created by evabishchevich on 1/4/17.
 */

public class Attempt {
    final boolean isSuccessful;
    final long duration;
    final int connectCount;

    public Attempt(boolean isSuccessful, long duration, int connectCount) {
        this.isSuccessful = isSuccessful;
        this.duration = duration;
        this.connectCount = connectCount;
    }

    @Override
    public String toString() {
        return "Attempt[" + (isSuccessful ? "succeed" : "failed") + ", duration: " + duration +
                ", connects: " + connectCount + "]";
    }
}
