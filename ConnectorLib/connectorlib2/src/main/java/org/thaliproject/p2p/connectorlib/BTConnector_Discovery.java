// Copyright (c) Microsoft. All Rights Reserved. Licensed under the MIT License. See license.txt in the project root for further information.
package org.thaliproject.p2p.connectorlib;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.AdvertiseSettings;
import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.CountDownTimer;
import android.util.Log;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Created by juksilve on 22.06.2015.
 */
public class BTConnector_Discovery implements BLEAdvertiserLollipop.BLEAdvertiserCallback, BLEScannerKitKat.BLEScannerCallback {

    BTConnector_Discovery that = this;

    static String JSON_ID_PEERID   = "pi";
    static String JSON_ID_PEERNAME = "pn";
    static String JSON_ID_BTADRRES = "ra";

    public interface  DiscoveryCallback{
        public void CurrentPeersList(List<ServiceItem> available);
        public void PeerDiscovered(ServiceItem service);
        public void DiscoveryStateChanged(State newState);
    }
    private DiscoveryCallback mDiscoveryCallback = null;


    BLEScannerKitKat mSearchKitKat = null;

    BLEAdvertiserLollipop mBLEAdvertiserLollipop = null;
    BluetoothGattService mFirstService = null;

    public enum State{
        DiscoveryIdle,
        DiscoveryNotInitialized,
        DiscoveryFindingPeers,
        DiscoveryFindingServices
    }

    private State myState = State.DiscoveryNotInitialized;
    private String mEncryptedInstance = "";

    private Context context = null;
    String mSERVICE_TYPE = "";


    CountDownTimer ServiceFoundTimeOutTimer = new CountDownTimer(600000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {
            if(that.mDiscoveryCallback != null) {
                //to clear any peers available status
                that.mDiscoveryCallback.CurrentPeersList(null);
            }
            Start();
        }
    };

    public BTConnector_Discovery(Context Context, DiscoveryCallback selector, String ServiceType,String instanceLine){
        this.context = Context;
        this.mSERVICE_TYPE = ServiceType;
        this.mDiscoveryCallback = selector;
        this.mEncryptedInstance = instanceLine;
    }

     public void Start() {
        Stop();

        StartAdvertiser();
        StartScanner();

        setState(State.DiscoveryFindingPeers);

    }

    private synchronized void StartAdvertiser(){
        mFirstService = new BluetoothGattService(UUID.fromString(BLEBase.SERVICE_UUID_1),BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic firstServiceChar = new BluetoothGattCharacteristic(UUID.fromString(BLEBase.CharacteristicsUID1),BluetoothGattCharacteristic.PROPERTY_READ,BluetoothGattCharacteristic.PERMISSION_READ );
        firstServiceChar.setValue(mEncryptedInstance.getBytes());

        mFirstService.addCharacteristic(firstServiceChar);

        mBLEAdvertiserLollipop = new BLEAdvertiserLollipop(that.context,that);
        mBLEAdvertiserLollipop.addService(mFirstService);
        mBLEAdvertiserLollipop.Start();
    }

    private synchronized void StartScanner() {
        print_line("SCAN", "starting");
        mSearchKitKat = new BLEScannerKitKat(this.context, this);
        mSearchKitKat.Start();
    }
    public void Stop() {
        print_line("", "Stoppingservices");
        ServiceFoundTimeOutTimer.cancel();

        BLEAdvertiserLollipop tmpadv= mBLEAdvertiserLollipop;
        mBLEAdvertiserLollipop = null;
        if(tmpadv != null){
            tmpadv.Stop();
        }

        BLEScannerKitKat tmpDisc = mSearchKitKat;
        mSearchKitKat = null;
        if(tmpDisc != null){
            tmpDisc.Stop();
        }
        setState(State.DiscoveryIdle);
    }

    @Override
    public void onAdvertisingStarted(AdvertiseSettings settingsInEffec, String error) {
        print_line("Advert", "Started err : " + error);
    }

    @Override
    public void onAdvertisingStopped(String error) {
        print_line("Advert", "Stopped err : " + error);
    }


    @Override
    public void gotServicesList(List<ServiceItem> list) {
        mDiscoveryCallback.CurrentPeersList(list);
    }

    @Override
    public void foundService(ServiceItem item) {
        mDiscoveryCallback.PeerDiscovered(item);
    }


    private void setState(State newState) {
       mDiscoveryCallback.DiscoveryStateChanged(newState);

    }

    public void print_line(String who, String line) {
        Log.i("BTConnector_Discovery" + who, line);
    }
}
