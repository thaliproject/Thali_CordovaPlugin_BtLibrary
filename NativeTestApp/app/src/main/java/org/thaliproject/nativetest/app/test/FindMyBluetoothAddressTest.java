/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativetest.app.test;

//import android.os.Build;
import android.bluetooth.BluetoothAdapter;
import android.util.Log;
import org.thaliproject.nativetest.app.TestEngine;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManager;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;
import org.thaliproject.p2p.btconnectorlib.internal.BluetoothMacAddressResolutionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

/**
 * A test for requesting a peer to provide us our Bluetooth MAC address.
 */
public class FindMyBluetoothAddressTest
        extends AbstractTest
        implements DiscoveryManager.DiscoveryManagerListener {
    private static final String TAG = FindMyBluetoothAddressTest.class.getName();
    private static long DEFAULT_TEST_TIMEOUT = 80000;
    private static int DURATION_OF_DEVICE_DISCOVERABLE_IN_SECONDS = 35;
    private DiscoveryManager mDiscoveryManager = null;
    private String mStoredBluetoothMacAddress = null;
    private String mBluetoothMacAddress = null;

    public FindMyBluetoothAddressTest(TestEngine testEngine, TestListener listener) {
        super(testEngine, listener);
        setTestTimeout(DEFAULT_TEST_TIMEOUT);
    }

    @Override
    public String getName() {
        return "Find my Bluetooth MAC address";
    }

    @Override
    public boolean run() {
        Log.i(TAG, "run");
        super.run();

        mDiscoveryManager = mTestEngine.getDiscoveryManager();
        mDiscoveryManager.stop();

        //if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
        mDiscoveryManager.setEmulateMarshmallow(true);
        //}

        try {
            Field mBluetoothMacAddressResolutionHelperField = mDiscoveryManager.getClass().getDeclaredField("mBluetoothMacAddressResolutionHelper");
            mBluetoothMacAddressResolutionHelperField.setAccessible(true);
            BluetoothMacAddressResolutionHelper mBluetoothMacAddressResolutionHelper =
                    (BluetoothMacAddressResolutionHelper) mBluetoothMacAddressResolutionHelperField.get(mDiscoveryManager);

            Field mBluetoothAdapterField = mBluetoothMacAddressResolutionHelper.getClass().getDeclaredField("mBluetoothAdapter");
            mBluetoothAdapterField.setAccessible(true);
            BluetoothAdapter mBluetoothAdapter = (BluetoothAdapter) mBluetoothAdapterField.get(mBluetoothMacAddressResolutionHelper);

            Field mServiceField = mBluetoothAdapter.getClass().getDeclaredField("mService");
            mServiceField.setAccessible(true);

            Object btManagerService = mServiceField.get(mBluetoothAdapter);
            mStoredBluetoothMacAddress = (String) btManagerService.getClass().getMethod("getAddress").invoke(btManagerService);
        } catch (NoSuchFieldException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }

        mDiscoveryManager.clearIdentityString();
        return mDiscoveryManager.start(true, true);
    }

    @Override
    public void finalize() {
        super.finalize();
        mDiscoveryManager.stop();
        mDiscoveryManager.setEmulateMarshmallow(false);

        if (mListener != null) {
            if (mBluetoothMacAddress != null) {
                mListener.onTestFinished(getName(), 1f, "Bluetooth MAC address resolved: " + mBluetoothMacAddress);
            } else {
                if (mStoredBluetoothMacAddress != null) {
                    // Restore the Bluetooth MAC address
                    DiscoveryManagerSettings.getInstance(null).setBluetoothMacAddress(mStoredBluetoothMacAddress);
                }

                String missingPermission = mDiscoveryManager.getMissingPermission();

                if (missingPermission != null) {
                    mListener.onTestFinished(getName(), 0f,
                            "Cannot resolve the Bluetooth MAC address due to denied permission: " + missingPermission);
                } else {
                    mListener.onTestFinished(getName(), 0f, "Failed to receive the Bluetooth MAC address");
                }
            }
        }
    }

    @Override
    public boolean onPermissionCheckRequired(String permission) {
        // Not used
        return false;
    }

    @Override
    public void onDiscoveryManagerStateChanged(
            DiscoveryManager.DiscoveryManagerState state,
            boolean isDiscovering, boolean isAdvertising) {
        // Not used
    }

    @Override
    public void onProvideBluetoothMacAddressRequest(String requestId) {
        // TODO
    }

    @Override
    public void onPeerReadyToProvideBluetoothMacAddress() {
        if (!DiscoveryManagerSettings.getInstance(null).getAutomateBluetoothMacAddressResolution()) {
            mDiscoveryManager.makeDeviceDiscoverable(DURATION_OF_DEVICE_DISCOVERABLE_IN_SECONDS);
        }
    }

    @Override
    public void onBluetoothMacAddressResolved(String bluetoothMacAddress) {
        if (bluetoothMacAddress != null) {
            mBluetoothMacAddress = bluetoothMacAddress;
            finalize();
        }
    }

    @Override
    public void onPeerDiscovered(PeerProperties peerProperties) {
        // Not used
    }

    @Override
    public void onPeerUpdated(PeerProperties peerProperties) {
        // Not used
    }

    @Override
    public void onPeerLost(PeerProperties peerProperties) {
        // Not used
    }
}
