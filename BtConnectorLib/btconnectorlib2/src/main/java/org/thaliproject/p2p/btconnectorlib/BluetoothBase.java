// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.btconnectorlib;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

/**
 * Created by juksilve on 6.3.2015.
 */
class BluetoothBase {

    public interface BluetoothStatusChanged {
        void BluetoothStateChanged(int state);
    }

    private final BluetoothStatusChanged callBack;
    private final BluetoothAdapter bluetooth;

    private BtBroadCastReceiver receiver = null;
    private final Context context;

    public BluetoothBase(Context Context, BluetoothStatusChanged handler) {
        this.context = Context;
        this.callBack = handler;
        this.bluetooth = BluetoothAdapter.getDefaultAdapter();
    }

    //returning false will indicate missing HW support
    public boolean Start(){
        if (bluetooth == null) {
            return false;
        }

        Stop();

        Log.i("", "Start-My BT: " + getAddress());

        BtBroadCastReceiver tmpReceiver = new BtBroadCastReceiver();
        try {

            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
            this.context.registerReceiver(tmpReceiver, filter);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return false;
        }
        receiver = tmpReceiver;
        return true;
    }

    public void Stop() {

        BroadcastReceiver tmp = receiver;
        receiver = null;
        if (tmp != null) {
            try {
                this.context.unregisterReceiver(tmp);
            } catch(IllegalArgumentException e) {e.printStackTrace();}
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
        return bluetooth != null && bluetooth.isEnabled();
    }

    public BluetoothAdapter getAdapter(){
        return bluetooth;
    }

    public String getAddress() {
        return bluetooth == null ? null : bluetooth.getAddress();
    }

    public String getName() {
        return bluetooth == null ? null : bluetooth.getName();
    }

    public BluetoothDevice getRemoteDevice(String address) {
        return bluetooth == null ? null : bluetooth.getRemoteDevice(address);
    }

    private class BtBroadCastReceiver extends BroadcastReceiver {
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
