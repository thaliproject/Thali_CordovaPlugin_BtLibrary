package org.thaliproject.nativetest.app.test.connection;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.thaliproject.nativetest.app.ConnectionSettings;
import org.thaliproject.nativetest.app.test.Timer;
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
        void onFinished(PeerProperties peerProperties, Result result);
    }

    static final int ATTEMPTS_COUNT = 20;
    private static final int MAX_RETRY_COUNT = 5;
    private static final String TAG = "ConnectDurationTest";

    private final ConnectionManager connectionManager;
    private final ConnectDurationTestListener listener;
    private List<PeerTestData> testPeers;

    public ConnectDurationTest(Context ctx, ConnectDurationTestListener listener, List<PeerProperties> peerProperties) {
        this.connectionManager = new ConnectionManager(ctx, this, ConnectionSettings.SERVICE_UUID,
                ConnectionSettings.SERVICE_NAME);
        this.listener = listener;
        initTestPeers(peerProperties);
    }

    private void initTestPeers(List<PeerProperties> peerProperties) {
        testPeers = new ArrayList<>(peerProperties.size());
        for (PeerProperties p : peerProperties) {
            testPeers.add(new PeerTestData(p, new Timer(), new Result()));
        }
    }

    public void start() {
        Log.d(TAG, "start");
        for (PeerTestData testPeer : testPeers) {
            startTest(testPeer);
        }
    }

    private void startTest(final PeerTestData testPeer) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int currentAttempt = 0;
                while (currentAttempt < ATTEMPTS_COUNT) {
                    synchronized (testPeer.monitor) {
                        currentAttempt++;
                        connect(testPeer);
                        try {
                            testPeer.monitor.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                }
                listener.onFinished(testPeer.peerProperties, testPeer.result);
            }
        }).start();
    }

    private void connect(final PeerTestData testPeer) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                testPeer.startAttempt();
                connectionManager.connect(testPeer.peerProperties);
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
        PeerTestData testPeer = findTestPeer(peerProperties);
        if (testPeer != null) {
            processConnectionResult(testPeer, true);
        } else {
            throw new RuntimeException("Communicate with a wrong peer " + peerProperties.toString());
        }
    }

    private PeerTestData findTestPeer(PeerProperties peerProperties) {
        for (PeerTestData ptd : testPeers) {
            if (ptd.peerProperties.equals(peerProperties)) {
                return ptd;
            }
        }
        return null;
    }

    @Override
    public void onConnectionTimeout(PeerProperties peerProperties) {
        Log.w(TAG, "onConnectionTimeout");
        if (!tryToReconnect(peerProperties)) {
            throw new RuntimeException("Communicate with a wrong peer " + peerProperties.toString());
        }
    }

    @Override
    public void onConnectionFailed(PeerProperties peerProperties, String errorMessage) {
        Log.w(TAG, "onConnectionFailed");
        if (!tryToReconnect(peerProperties)) {
            throw new RuntimeException("Communicate with a wrong peer " + peerProperties.toString());
        }
    }

    private boolean tryToReconnect(PeerProperties peerProperties) {
        PeerTestData testPeer = findTestPeer(peerProperties);
        if (testPeer != null) {
            reconnect(testPeer);
            return true;
        }
        return false;
    }

    private void reconnect(final PeerTestData testPeer) {
        if (testPeer.getRetryCount() < MAX_RETRY_COUNT) {
            testPeer.increaseRetries();
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    connectionManager.connect(testPeer.peerProperties);
                }
            });
        } else {
            processConnectionResult(testPeer, false);
        }
    }

    private void processConnectionResult(PeerTestData testPeer, boolean successful) {
        Log.d(TAG, "processConnectionResult, " + (successful ? "succeed" : "failed"));
        testPeer.finishAttempt(successful);
        synchronized (testPeer.monitor) {
            testPeer.monitor.notify();
        }
    }

}
