// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.util.UUID;

/**
 * Created by juksilve on 6.3.2015.
 */
public class BluetoothBase {

    public interface BluetoothStatusChanged {
        public void BluetoothStateChanged(int state);
    }

    private BluetoothStatusChanged callBack = null;
    private BluetoothAdapter bluetooth = null;

    private BtBrowdCastReceiver receiver = null;
    private IntentFilter filter = null;
    private Context context = null;
    String blueAddress = "";

    public BluetoothBase(Context Context, BluetoothStatusChanged handler) {
        this.context = Context;
        this.callBack = handler;

        //bluetooth = new BluetoothAdapter(this);
        bluetooth = BluetoothAdapter.getDefaultAdapter();
        if (bluetooth != null) {
            blueAddress = bluetooth.getAddress();
        }
    }

    public synchronized  boolean Start() {

        if (bluetooth == null) {
            return false;
        }

        Log.d("", "Start-My BT: " + blueAddress);

        if (receiver == null) {
            try {
                receiver = new BtBrowdCastReceiver();
                filter = new IntentFilter();
                filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
                this.context.registerReceiver(receiver, filter);
            } catch(Exception e) {e.printStackTrace();}
        }

        return true;
    }

    public void Stop() {

        BroadcastReceiver tmp = receiver;
        receiver = null;
        if (tmp != null) {
            try {
                this.context.unregisterReceiver(tmp);
            } catch(Exception e) {e.printStackTrace();}
        }
    }

    public void SetBluetoothEnabled(boolean seton) {
        if (bluetooth == null) {
            return;
        }

        if (seton) {
            bluetooth.enable();
        } else {
            bluetooth.disable();
        }
    }

    public boolean isBluetoothEnabled() {
        if (bluetooth == null) {
            return false;
        }

        return bluetooth.isEnabled();
    }

    public BluetoothAdapter getAdapter(){
        return bluetooth;
    }

    public String getAddress() {
        return blueAddress;
    }

    public String getName() {
        if (bluetooth == null) {
            return "";
        }

        return bluetooth.getName();
    }

    public BluetoothDevice getRemoteDevice(String address) {
        if (bluetooth == null) {
            return null;
        }

        return bluetooth.getRemoteDevice(address);
    }

    private class BtBrowdCastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
                int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);

                if (callBack != null) {
                    callBack.BluetoothStateChanged(mode);
                }
            }
        }
    }
}
