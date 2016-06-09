package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.support.test.InstrumentationRegistry;

import org.thaliproject.p2p.btconnectorlib.internal.wifi.WifiDirectManager;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class AbstractConnectivityManagerTest {

    private static long MAX_MEDIA_TIMEOUT = 60000;
    private static long CHECK_MEDIA_INTERVAL = 500;

    protected static void toggleBluetooth(boolean turnOn) throws Exception {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            // Device does not support Bluetooth
            fail("No Bluetooth support!");
        }
        if (turnOn && !btAdapter.isEnabled()) {
            btAdapter.enable();
        } else if (!turnOn && btAdapter.isEnabled()) {
            btAdapter.disable();
        }

        long currentTimeout = 0;
        while (currentTimeout < MAX_MEDIA_TIMEOUT) {
            Thread.sleep(CHECK_MEDIA_INTERVAL);
            currentTimeout += CHECK_MEDIA_INTERVAL;
            if (btAdapter.isEnabled() != turnOn && currentTimeout < MAX_MEDIA_TIMEOUT) {
                continue;
            }
            assertThat(btAdapter.isEnabled(), is(turnOn));
            break;
        }
    }

    protected static boolean getBluetoothStatus() {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        return btAdapter != null && btAdapter.isEnabled();
    }

    protected static void toggleWifi(boolean turnOn) throws Exception {

        WifiDirectManager wifiManager = WifiDirectManager.getInstance(InstrumentationRegistry.getContext());

        if (wifiManager == null) {
            // Device does not support WIFI
            fail("No Wifi support!");
        }
        if (turnOn && !wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        } else if (!turnOn && wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(false);
        }

        long currentTimeout = 0;
        while (currentTimeout < MAX_MEDIA_TIMEOUT) {
            Thread.sleep(CHECK_MEDIA_INTERVAL);
            currentTimeout += CHECK_MEDIA_INTERVAL;
            if (wifiManager.isWifiEnabled() != turnOn && currentTimeout < MAX_MEDIA_TIMEOUT) {
                continue;
            }
            assertThat(wifiManager.isWifiEnabled(), is(turnOn));
            break;
        }
    }

    protected static boolean getWifiStatus() {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        return btAdapter != null && btAdapter.isEnabled();
    }

    protected void waitForMainLooper() throws InterruptedException {
        // In API level 23 we could use MessageQueue.isIdle to check if all is ready
        // For now just use timeout
        Thread.sleep(100);
    }
}
