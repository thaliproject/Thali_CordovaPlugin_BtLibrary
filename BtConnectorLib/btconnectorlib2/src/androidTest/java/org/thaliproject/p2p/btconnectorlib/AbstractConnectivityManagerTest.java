package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.net.wifi.WifiManager;
import android.support.test.InstrumentationRegistry;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class AbstractConnectivityManagerTest {

    private static long MAX_MEDIA_TIMEOUT = 20000;
    private static long CHECK_MEDIA_INTERVAL = 500;

    protected static void toggleBluetooth(boolean turnOn) throws Exception {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            // Device does not support Bluetooth
            fail("No Bluetooth support!");
        }

        // first make sure bluetooth is not changing its state right now
        long currentTimeout = 0;
        while (currentTimeout < MAX_MEDIA_TIMEOUT) {
            if (btAdapter.getState() == BluetoothAdapter.STATE_ON ||
                btAdapter.getState() == BluetoothAdapter.STATE_OFF) {
                break;
            }
            Thread.sleep(CHECK_MEDIA_INTERVAL);
            currentTimeout += CHECK_MEDIA_INTERVAL;
        }
        assertThat(btAdapter.getState(), anyOf(is(BluetoothAdapter.STATE_ON),
                                               is(BluetoothAdapter.STATE_OFF)));

        if (turnOn && btAdapter.getState() == BluetoothAdapter.STATE_OFF) {
            btAdapter.enable();
        } else if (!turnOn && btAdapter.getState() == BluetoothAdapter.STATE_ON) {
            btAdapter.disable();
        }

        int expectedState = turnOn ? BluetoothAdapter.STATE_ON : BluetoothAdapter.STATE_OFF;
        currentTimeout = 0;
        while (currentTimeout < MAX_MEDIA_TIMEOUT) {
            Thread.sleep(CHECK_MEDIA_INTERVAL);
            currentTimeout += CHECK_MEDIA_INTERVAL;
            if (btAdapter.getState() != expectedState && currentTimeout < MAX_MEDIA_TIMEOUT) {
                continue;
            }
            assertThat(btAdapter.getState(), is(expectedState));
            break;
        }
    }

    protected static boolean getBluetoothStatus() {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        return btAdapter != null && btAdapter.isEnabled();
    }

    protected static void toggleWifi(boolean turnOn) throws Exception {

        WifiManager wifiManager = (WifiManager) InstrumentationRegistry.getContext().getSystemService(
                InstrumentationRegistry.getContext().WIFI_SERVICE);

        if (wifiManager == null) {
            // Device does not support WIFI
            fail("No Wifi support!");
        }

        // first make sure wifi is not changing its state right now
        long currentTimeout = 0;
        while (currentTimeout < MAX_MEDIA_TIMEOUT) {
            if (wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED ||
                wifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED) {
                break;
            }
            Thread.sleep(CHECK_MEDIA_INTERVAL);
            currentTimeout += CHECK_MEDIA_INTERVAL;
        }
        assertThat(wifiManager.getWifiState(), anyOf(is(WifiManager.WIFI_STATE_ENABLED),
                                                     is(WifiManager.WIFI_STATE_DISABLED)));

        if (turnOn && wifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED) {
            wifiManager.setWifiEnabled(true);
        } else if (!turnOn && wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
            wifiManager.setWifiEnabled(false);
        }

        int expectedState = turnOn ? WifiManager.WIFI_STATE_ENABLED : WifiManager.WIFI_STATE_DISABLED;
        currentTimeout = 0;
        while (currentTimeout < MAX_MEDIA_TIMEOUT) {
            Thread.sleep(CHECK_MEDIA_INTERVAL);
            currentTimeout += CHECK_MEDIA_INTERVAL;
            if (wifiManager.getWifiState() != expectedState && currentTimeout < MAX_MEDIA_TIMEOUT) {
                continue;
            }
            assertThat(wifiManager.getWifiState(), is(expectedState));
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
