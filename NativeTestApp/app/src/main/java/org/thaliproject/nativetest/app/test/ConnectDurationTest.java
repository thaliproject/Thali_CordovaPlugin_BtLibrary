package org.thaliproject.nativetest.app.test;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.thaliproject.nativetest.app.ConnectionSettings;
import org.thaliproject.p2p.btconnectorlib.ConnectionManager;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by evabishchevich on 1/4/17.
 */

public class ConnectDurationTest implements ConnectionManager.ConnectionManagerListener {

    public interface ConnectDurationTestListener {
        void onFinished(Result result);
    }

    private static final int ATTEMPTS_COUNT = 20;
    private static final int MAX_RETRY_COUNT = 5;
    private static final long NANOS_IN_MILI = 1_000_000L;
    private static final String TAG = "ConnectDurationTest";

    private final ConnectionManager connectionManager;
    private final ConnectDurationTestListener listener;
    private final PeerProperties peerPropertiesToConnect;
    private final Timer timer;
    private final Result result;
    private final Object monitor = new Object();
    private volatile boolean sync = true;
    private volatile int retryCount = 0;

    public ConnectDurationTest(Context ctx, ConnectDurationTestListener listener, PeerProperties peerProperties) {
        this.connectionManager = new ConnectionManager(ctx, this, ConnectionSettings.SERVICE_UUID,
                ConnectionSettings.SERVICE_NAME);
        this.listener = listener;
        peerPropertiesToConnect = peerProperties;
        timer = new Timer();
        result = new Result();
    }

    public void start() {
        Log.d(TAG, "start");

        new Thread(new Runnable() {
            @Override
            public void run() {
                int currentAttempt = 0;
                while (currentAttempt < ATTEMPTS_COUNT) {
//                    if (!sync) {
//                        try {
//                            Thread.sleep(100L);
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//                        continue;
//                    }
                    synchronized (monitor) {
                        currentAttempt++;
                        connect(peerPropertiesToConnect);
                        sync = false;
                        try {
                            monitor.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                }
                listener.onFinished(result);
            }
        }).start();

    }

    private void connect(final PeerProperties peerProperties) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                timer.start();
                connectionManager.connect(peerProperties);
            }
        });
    }

    @Override
    public void onConnectionManagerStateChanged(ConnectionManager.ConnectionManagerState state) {

    }

    @Override
    public void onConnected(BluetoothSocket bluetoothSocket, boolean isIncoming, PeerProperties peerProperties) {
        Log.d(TAG, "onConnected");
        try {
            bluetoothSocket.close();
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
        retryCount = 0;
        processConnectionResult(peerProperties, true);
    }

    @Override
    public void onConnectionTimeout(PeerProperties peerProperties) {
        Log.w(TAG, "onConnectionTimeout");
        reconnect(peerProperties);
    }

    @Override
    public void onConnectionFailed(PeerProperties peerProperties, String errorMessage) {
        Log.w(TAG, "onConnectionFailed");
        reconnect(peerProperties);
    }

    private void reconnect(final PeerProperties peerProperties) {
        if (retryCount < MAX_RETRY_COUNT) {
            retryCount++;
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    connectionManager.connect(peerProperties);
                }
            });
        } else {
            retryCount = 0;
            processConnectionResult(peerProperties, false);
        }
    }

    private void processConnectionResult(PeerProperties peerProperties, boolean successful) {
        Log.d(TAG, "processConnectionResult, " + (successful ? "succeed" : "failed"));
        if (peerProperties.equals(peerPropertiesToConnect)) {
            addAttempt(successful);
            synchronized (monitor) {
                monitor.notify();
            }
            sync = true;
            return;
        }
        throw new RuntimeException("Communicate with a wrong peer");
    }

    private void addAttempt(boolean successful) {
        long duration = timer.finish();
        result.addAttempt(new Attempt(successful, duration));
    }

    public static class Result {
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

    public static class Attempt {
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
}
