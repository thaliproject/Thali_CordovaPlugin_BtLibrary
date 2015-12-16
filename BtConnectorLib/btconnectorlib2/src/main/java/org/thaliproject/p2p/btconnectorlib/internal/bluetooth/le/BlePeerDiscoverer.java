/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanResult;
import android.util.Log;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import java.util.UUID;

/**
 *
 */
@TargetApi(21)
public class BlePeerDiscoverer implements BleAdvertiser.Listener, BleScanner.Listener {
    /**
     * A listener for peer discovery events.
     */
    public interface BlePeerDiscoveryListener {
        /**
         * Called when the discovery is started or stopped.
         * @param isStarted If true, the discovery was started. If false, it was stopped.
         */
        void onIsDiscoveryStartedChanged(boolean isStarted);

        /**
         * Called when a peer was discovered.
         * @param peerProperties The properties of the discovered peer.
         */
        void onPeerDiscovered(PeerProperties peerProperties);
    }

    private static final String TAG = BlePeerDiscoverer.class.getName();
    private final BlePeerDiscoveryListener mListener;
    private final UUID mServiceUuid;
    private BleAdvertiser mBleAdvertiser = null;
    private BleScanner mBleScanner = null;

    /**
     * Constructor.
     * @param listener The listener.
     * @param bluetoothAdapter The Bluetooth adapter.
     * @param myPeerName Our peer name for advertisement.
     * @param serviceUuid The BLE service UUID.
     * @param myBluetoothAddress Our Bluetooth address for advertisement.
     */
    public BlePeerDiscoverer(
            BlePeerDiscoveryListener listener, BluetoothAdapter bluetoothAdapter,
            String myPeerName, UUID serviceUuid, String myBluetoothAddress) {
        mListener = listener;
        mServiceUuid = serviceUuid;

        mBleAdvertiser = new BleAdvertiser(this, bluetoothAdapter);

        mBleAdvertiser.setAdvertiseData(
                PeerAdvertisementFactory.createAdvertiseData(myPeerName, serviceUuid, myBluetoothAddress));

        mBleScanner = new BleScanner(this, bluetoothAdapter);

    }

    /**
     *
     * @return
     */
    public synchronized boolean start() {
        boolean isStarting = false;

        isStarting = (mBleAdvertiser.start() && mBleScanner.start());

        return isStarting;
    }

    /**
     *
     */
    public synchronized void stop() {
        mBleAdvertiser.stop();
        mBleScanner.stop();
    }

    @Override
    public void onAdvertiserFailedToStart(int errorCode) {

    }

    @Override
    public void onIsAdvertiserStartedChanged(boolean isStarted) {
        Log.i(TAG, "onIsAdvertiserStartedChanged: " + isStarted);
    }

    @Override
    public void onScannerFailed(int errorCode) {

    }

    @Override
    public void onIsScannerStartedChanged(boolean isStarted) {
        Log.i(TAG, "onIsScannerStartedChanged: " + isStarted);
    }

    @Override
    public void onScanResult(ScanResult result) {
        checkScanResult(result);
    }

    /**
     *
     * @param scanResult
     */
    private synchronized void checkScanResult(ScanResult scanResult) {
        byte[] manufacturerData = null;

        if (scanResult.getScanRecord() != null) {
            manufacturerData = scanResult.getScanRecord().getManufacturerSpecificData(
                    PeerAdvertisementFactory.MANUFACTURER_ID);
        }

        PeerProperties peerProperties = null;

        if (manufacturerData != null) {
            peerProperties = PeerAdvertisementFactory.manufacturerDataToPeerProperties(manufacturerData);
        }

        if (peerProperties != null && mListener != null) {
            mListener.onPeerDiscovered(peerProperties);
        }
    }
}
