package org.thaliproject.p2p.btconnectorlib;

/**
 * Created by juksilve on 27.8.2015.
 */
interface AdvertiserCallback{
    void onAdvertisingStarted(String error);
    void onAdvertisingStopped(String error);
}
