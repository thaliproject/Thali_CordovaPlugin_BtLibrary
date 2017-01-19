package org.thaliproject.nativetest.app.test.connection;

import java.util.ArrayList;
import java.util.List;

import static org.thaliproject.nativetest.app.test.connection.ConnectDurationTest.ATTEMPTS_COUNT;

/**
 * Created by evabishchevich on 1/4/17.
 */

public class Result {
    private List<Attempt> attempts;

    Result() {
        attempts = new ArrayList<>(ATTEMPTS_COUNT);
    }

    void addAttempt(Attempt attempt) {
        attempts.add(attempt);
    }

    public boolean isSuccessful() {
        for (Attempt a : attempts) {
            if (!a.isSuccessful) {
                return false;
            }
        }
        return true;
    }

    public long averageDuration() {
        if (attempts.size() == 0) {
            return 0;
        }
        long sum = 0;
        for (Attempt a : attempts) {
            sum += a.duration;
        }
//            return Math.round(((double) sum) / (NANOS_IN_MILI * attempts.size()));
        return Math.round(((double) sum) / attempts.size());
    }

    public long maxDuration() {
        if (attempts.size() == 0) {
            return 0;
        }
        long max = 0;
        for (Attempt a : attempts) {
            if (a.duration > max) {
                max = a.duration;
            }
        }
        return max;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Result: ").append(isSuccessful() ? "succeed" : "failed").append(", average duration: ")
                .append(averageDuration()).append(", max duration: ").append(maxDuration()).append("\n");
        sb.append("Attempts:\n");
        for (Attempt a : attempts) {
            sb.append(a.toString()).append("\n");
        }
        return sb.toString();
    }
}
