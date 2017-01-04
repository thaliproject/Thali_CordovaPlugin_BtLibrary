package org.thaliproject.nativetest.app.test.connection;

/**
 * Created by evabishchevich on 1/4/17.
 */

public class Attempt {
    final boolean isSuccessful;
    final long duration;

    Attempt(boolean isSuccessful, long duration) {
        this.isSuccessful = isSuccessful;
        this.duration = duration;
    }

    @Override
    public String toString() {
        return "Attempt[" + (isSuccessful ? "succeed" : "failed") + ", duration: " + duration + "]";
    }
}
