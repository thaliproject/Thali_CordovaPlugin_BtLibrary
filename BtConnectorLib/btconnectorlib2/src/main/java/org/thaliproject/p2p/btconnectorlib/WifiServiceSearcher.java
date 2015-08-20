package org.thaliproject.p2p.btconnectorlib;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import org.json.JSONObject;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION;


/**
 * Created by juksilve on 28.2.2015.
 */

public class WifiServiceSearcher {

    public interface  DiscoveryInternalCallBack{
        void gotPeersList(Collection<WifiP2pDevice> list);
        void gotServicesList(List<ServiceItem> list);
        void foundService(ServiceItem item);
    }

    private Context context = null;
    private BroadcastReceiver receiver = null;
    private final String SERVICE_TYPE;

    private final DiscoveryInternalCallBack callback;
    private WifiP2pManager p2p = null;
    private WifiP2pManager.Channel channel = null;
    private WifiP2pManager.PeerListListener peerListListener = null;

    enum ServiceState{
        NONE,
        DiscoverPeer,
        DiscoverService
    }
    private ServiceState myServiceState = ServiceState.NONE;

    private final CopyOnWriteArrayList<ServiceItem> myServiceList = new CopyOnWriteArrayList<ServiceItem>();

    private final CountDownTimer ServiceDiscoveryTimeOutTimer = new CountDownTimer(60000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {
            stopDiscovery();
            startPeerDiscovery();
        }
    };

    private CountDownTimer peerDiscoveryTimer = null;

    public WifiServiceSearcher(Context Context, WifiP2pManager Manager, WifiP2pManager.Channel Channel, DiscoveryInternalCallBack handler,String serviceType) {
        this.context = Context;
        this.p2p = Manager;
        this.channel = Channel;
        this.callback = handler;
        this.SERVICE_TYPE = serviceType;

        Random ran = new Random(System.currentTimeMillis());

        // if this 4 seconds minimum, then we see this
        // triggering before we got all services
        long millisInFuture = 5000 + (ran.nextInt(5000));

        debug_print("peerDiscoveryTimer timeout value:" + millisInFuture);

        peerDiscoveryTimer = new CountDownTimer(millisInFuture, 1000) {
            public void onTick(long millisUntilFinished) {
                // not using
            }
            public void onFinish() {
                myServiceState = ServiceState.NONE;
                if (callback == null) {
                    startPeerDiscovery();
                    return;
                }

                callback.gotServicesList(myServiceList);
                //cancel all other counters, and start our wait cycle
                ServiceDiscoveryTimeOutTimer.cancel();
                peerDiscoveryTimer.cancel();
                stopDiscovery();
                startPeerDiscovery();
            }
        };
    }

    public void Start() {

        if(receiver == null) {
            try {
                receiver = new ServiceSearcherReceiver();
                IntentFilter filter = new IntentFilter();
                filter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
                filter.addAction(WIFI_P2P_PEERS_CHANGED_ACTION);
                this.context.registerReceiver(receiver, filter);
            } catch(Exception e) {e.printStackTrace();}
        }

        peerListListener = new WifiP2pManager.PeerListListener() {

            public void onPeersAvailable(WifiP2pDeviceList peers) {

                // this is called still multiple time time-to-time
                // so need to make sure we only make one service discovery call
                if (myServiceState == ServiceState.DiscoverService) {
                    return;
                }

                if (callback != null) {
                    // we do want to inform also when we get zero peer list
                    // this would inform the plugin that all peers are now unavailable
                    callback.gotPeersList(peers.getDeviceList());
                }
                if (peers.getDeviceList().size() > 0) {
                    //tests have shown that if we have multiple peers with services advertising
                    // who disappear same time when we do this, there is a chance that we get stuck
                    // thus, if this happens, in 60 seconds we'll cancel this query and start peer discovery again
                    ServiceDiscoveryTimeOutTimer.start();
                    startServiceDiscovery();
                } //else //we'll just wait
            }

        };

        WifiP2pManager.DnsSdServiceResponseListener serviceListener = new WifiP2pManager.DnsSdServiceResponseListener() {

            public void onDnsSdServiceAvailable(String instanceName, String serviceType, WifiP2pDevice device) {

                debug_print("Found Service, :" + instanceName + ", type" + serviceType + ":");

                if (serviceType.startsWith(SERVICE_TYPE)) {
                    boolean addService = true;

                    Iterator<ServiceItem> iterator = myServiceList.iterator();
                    while (iterator.hasNext()) {
                        ServiceItem item = iterator.next();
                        if(item != null && item.deviceAddress.equals(device.deviceAddress)){
                            addService = false;
                        }
                    }
                    if(addService) {
                        try {
                            JSONObject jObject = new JSONObject(instanceName);

                            String peerIdentifier = jObject.getString(BTConnector.JSON_ID_PEERID);
                            String peerName = jObject.getString(BTConnector.JSON_ID_PEERNAME);
                            String peerAddress = jObject.getString(BTConnector.JSON_ID_BTADRRES);

                            debug_print("JsonLine: " + instanceName + " -- peerIdentifier:" + peerIdentifier + ", peerName: " + peerName + ", peerAddress: " + peerAddress);

                            ServiceItem tmpSrv = new ServiceItem(peerIdentifier,peerName,peerAddress, serviceType, device.deviceAddress,device.deviceName);
                            if(callback != null) {
                                //this is to inform right away that we have found a peer, so we don't need to wait for the whole list before connecting
                                callback.foundService(tmpSrv);
                            }
                            myServiceList.add(tmpSrv);

                        }catch (Exception e){
                            debug_print("checking instance failed , :" + e.toString());
                        }
                    }

                } else {
                    debug_print("Not our Service, :" + SERVICE_TYPE + "!=" + serviceType + ":");
                }

                ServiceDiscoveryTimeOutTimer.cancel();
                peerDiscoveryTimer.cancel();
                peerDiscoveryTimer.start();
            }
        };

        p2p.setDnsSdResponseListeners(channel, serviceListener, null);
        startPeerDiscovery();
    }

    public void Stop() {
        BroadcastReceiver tmprec = receiver;
        receiver = null;
        if(tmprec != null) {
            try {
                this.context.unregisterReceiver(tmprec);
            } catch (Exception e) {e.printStackTrace();}
        }
        ServiceDiscoveryTimeOutTimer.cancel();
        peerDiscoveryTimer.cancel();
        stopDiscovery();
        stopPeerDiscovery();
    }

    private void startPeerDiscovery() {
        p2p.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                myServiceState = ServiceState.DiscoverPeer;
                debug_print("Started peer discovery");
            }

            public void onFailure(int reason) {
                myServiceState = ServiceState.NONE;
                debug_print("Starting peer discovery failed, error code " + reason);
                //lets try again after 1 minute time-out !
                ServiceDiscoveryTimeOutTimer.start();
            }
        });
    }

    private void stopPeerDiscovery() {
        p2p.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                debug_print("Stopped peer discovery");
            }

            public void onFailure(int reason) {
                debug_print("Stopping peer discovery failed, error code " + reason);
            }
        });
    }

    private void startServiceDiscovery() {

        myServiceState = ServiceState.DiscoverService;

        WifiP2pDnsSdServiceRequest request = WifiP2pDnsSdServiceRequest.newInstance(SERVICE_TYPE);
        final Handler handler = new Handler();
        p2p.addServiceRequest(channel, request, new WifiP2pManager.ActionListener() {

            public void onSuccess() {
                debug_print("Added service request");
                handler.postDelayed(new Runnable() {
                    //There are supposedly a possible race-condition bug with the service discovery
                    // thus to avoid it, we are delaying the service discovery start here
                    public void run() {
                        p2p.discoverServices(channel, new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                                myServiceList.clear();
                                debug_print("Started service discovery");
                                myServiceState = ServiceState.DiscoverService;
                            }
                            public void onFailure(int reason) {
                                stopDiscovery();
                                myServiceState = ServiceState.NONE;
                                debug_print("Starting service discovery failed, error code " + reason);
                                //lets try again after 1 minute time-out !
                                ServiceDiscoveryTimeOutTimer.start();
                            }
                        });
                    }
                }, 1000);
            }

            public void onFailure(int reason) {
                myServiceState = ServiceState.NONE;
                debug_print("Adding service request failed, error code " + reason);
                //lets try again after 1 minute time-out !
                ServiceDiscoveryTimeOutTimer.start();
            }
        });

    }

    private void stopDiscovery() {
        p2p.clearServiceRequests(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                debug_print("Cleared service requests");
            }

            public void onFailure(int reason) {
                debug_print("Clearing service requests failed, error code " + reason);
            }
        });
    }
    private void debug_print(String buffer) {
        Log.i("Service searcher", buffer);
    }
    private class ServiceSearcherReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                if(myServiceState != ServiceState.DiscoverService) {
                    p2p.requestPeers(channel, peerListListener);
                }
            } else if(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {

                int state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);
                String persTatu = "Discovery state changed to ";

                if(state == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED){
                    persTatu = persTatu + "Stopped.";
                    startPeerDiscovery();
                }else if(state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED){
                    persTatu = persTatu + "Started.";
                }else{
                    persTatu = persTatu + "unknown  " + state;
                }
                debug_print(persTatu);
            }
        }
    }
}
