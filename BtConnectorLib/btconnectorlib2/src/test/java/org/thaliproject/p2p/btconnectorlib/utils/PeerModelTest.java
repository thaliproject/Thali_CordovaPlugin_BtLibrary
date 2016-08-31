package org.thaliproject.p2p.btconnectorlib.utils;

import android.os.CountDownTimer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PeerModelTest {

    @Mock
    PeerModel.Listener mMockListener;

    @Mock
    PeerModel.Listener mMockListener2;

    @Mock
    DiscoveryManagerSettings mMockDiscoveryManagerSettings;

    @Mock
    CountDownTimer mMockCheckExpiredPeersTimer;

    @Mock
    HashMap mMockMap;

    @Mock
    PeerProperties mMockPeerProperties;

    @Mock
    PeerProperties mMockPeerProperties2;

    PeerModel mPeerModel;


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mPeerModel = new PeerModel(mMockListener, mMockDiscoveryManagerSettings);
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void peerPropertiesConstructor() throws Exception {
        PeerModel pm = new PeerModel(mMockListener, mMockDiscoveryManagerSettings);
        assertThat("PeerProperties is properly initiated", pm, is(notNullValue()));
        Field listenersField = pm.getClass().getDeclaredField("mListeners");
        listenersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        CopyOnWriteArrayList<PeerModel.Listener> listeners
                = (CopyOnWriteArrayList<PeerModel.Listener>) listenersField.get(pm);

        assertThat("Listener is properly added during the initialization", listeners.size(), is(1));

        Field settingsField = pm.getClass().getDeclaredField("mSettings");
        settingsField.setAccessible(true);
        DiscoveryManagerSettings settings
                = (DiscoveryManagerSettings) settingsField.get(pm);

        assertThat("Discovery manager settings is properly initialized", settings,
                is(mMockDiscoveryManagerSettings));
    }

    @Test
    public void testAddRemoveListener() throws Exception {

        Field listenersField = mPeerModel.getClass().getDeclaredField("mListeners");
        listenersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        CopyOnWriteArrayList<PeerModel.Listener> listeners
                = (CopyOnWriteArrayList<PeerModel.Listener>) listenersField.get(mPeerModel);

        // add null
        mPeerModel.addListener(null);
        assertThat("The null listener is not added", listeners.size(), is(1));

        // add existing listener
        mPeerModel.addListener(mMockListener);
        assertThat("The listener is not added twice", listeners.size(), is(1));

        // add listener
        mPeerModel.addListener(mMockListener2);
        assertThat("The listener is properly added", listeners.size(), is(2));

        // remove listener
        mPeerModel.removeListener(mMockListener2);
        assertThat("The listener is properly removed", listeners.size(), is(1));

        // remove not existing listener
        mPeerModel.removeListener(mMockListener2);
        assertThat("The listener is properly removed", listeners.size(), is(1));
    }

    @Test
    public void testClear() throws Exception {
        Field mCheckExpiredPeersTimerField = mPeerModel.getClass().getDeclaredField("mCheckExpiredPeersTimer");
        mCheckExpiredPeersTimerField.setAccessible(true);
        mCheckExpiredPeersTimerField.set(mPeerModel, mMockCheckExpiredPeersTimer);

        Field mDiscoveredPeersField = mPeerModel.getClass().getDeclaredField("mDiscoveredPeers");
        mDiscoveredPeersField.setAccessible(true);
        mDiscoveredPeersField.set(mPeerModel, mMockMap);

        mPeerModel.clear();

        // check if the peer container is cleared
        verify(mMockMap, times(1))
                .clear();

        // check if the timer for checking for expired peers is stopped
        verify(mMockCheckExpiredPeersTimer, times(1))
                .cancel();
        assertThat("The timer for checking for expired peers is stopped",
                mCheckExpiredPeersTimerField.get(mPeerModel), is(nullValue()));
    }

    @Test
    public void testOnPeerExpirationTimeChanged_expTimeSetToZero() throws Exception {
        Field mCheckExpiredPeersTimerField = mPeerModel.getClass().getDeclaredField("mCheckExpiredPeersTimer");
        mCheckExpiredPeersTimerField.setAccessible(true);
        mCheckExpiredPeersTimerField.set(mPeerModel, mMockCheckExpiredPeersTimer);

        mPeerModel.onPeerExpirationTimeChanged();

        // check if the timer for checking for expired peers is stopped
        verify(mMockCheckExpiredPeersTimer, times(1))
                .cancel();
        assertThat("The timer for checking for expired peers is stopped",
                mCheckExpiredPeersTimerField.get(mPeerModel), is(nullValue()));

    }

    @Test
    public void testOnPeerExpirationTimeChanged_expTimeSetToNonZero() throws Exception {
        Field mCheckExpiredPeersTimerField = mPeerModel.getClass().getDeclaredField("mCheckExpiredPeersTimer");
        mCheckExpiredPeersTimerField.setAccessible(true);
        mCheckExpiredPeersTimerField.set(mPeerModel, mMockCheckExpiredPeersTimer);
        when(mMockDiscoveryManagerSettings.getPeerExpiration()).thenReturn(500L);
        mPeerModel.onPeerExpirationTimeChanged();

        // check if the timer for checking for expired peers is stopped
        verify(mMockCheckExpiredPeersTimer, times(1))
                .cancel();
        CountDownTimer countDownTimer = (CountDownTimer) mCheckExpiredPeersTimerField.get(mPeerModel);
        assertThat("The timer for checking for expired peers is set",
                countDownTimer, is(notNullValue()));
    }

    @Test
    public void testGetDiscoveredPeerByBluetoothMacAddress_NoPeers() throws Exception {

        HashMap<PeerProperties, Timestamp> mDiscoveredPeers = new HashMap<>();

        Field mDiscoveredPeersField = mPeerModel.getClass().getDeclaredField("mDiscoveredPeers");
        mDiscoveredPeersField.setAccessible(true);
        mDiscoveredPeersField.set(mPeerModel, mDiscoveredPeers);
        PeerProperties pp = mPeerModel.getDiscoveredPeerByBluetoothMacAddress("testBTAddres");
        assertThat("Is null when no peers found",
                pp, is(nullValue()));
    }

    @Test
    public void testGetDiscoveredPeerByBluetoothMacAddress_PeersFound() throws Exception {
        String testBTAddress = "01:00:00:00:00:00:00:E0";

        HashMap<PeerProperties, Timestamp> mDiscoveredPeers = new HashMap<>();

        mDiscoveredPeers.put(mMockPeerProperties, new Timestamp(1000L));

        when(mMockPeerProperties.getBluetoothMacAddress()).thenReturn(testBTAddress);

        Field mDiscoveredPeersField = mPeerModel.getClass().getDeclaredField("mDiscoveredPeers");
        mDiscoveredPeersField.setAccessible(true);
        mDiscoveredPeersField.set(mPeerModel, mDiscoveredPeers);
        PeerProperties pp = mPeerModel.getDiscoveredPeerByBluetoothMacAddress(testBTAddress);
        assertThat("The proper peer properties instance is returned",
                pp, is(mMockPeerProperties));
    }

    @Test
    public void testGetDiscoveredPeerByDeviceAddress_NoPeers() throws Exception {
        HashMap<PeerProperties, Timestamp> mDiscoveredPeers = new HashMap<>();

        Field mDiscoveredPeersField = mPeerModel.getClass().getDeclaredField("mDiscoveredPeers");
        mDiscoveredPeersField.setAccessible(true);
        mDiscoveredPeersField.set(mPeerModel, mDiscoveredPeers);
        PeerProperties pp = mPeerModel.getDiscoveredPeerByDeviceAddress("testBTAddres");
        assertThat("Is null when no peers found",
                pp, is(nullValue()));

    }

    @Test
    public void testGetDiscoveredPeerByDeviceAddress_PeerFound() throws Exception {
        String testDeviceAddress = "testDeviceAddress";

        HashMap<PeerProperties, Timestamp> mDiscoveredPeers = new HashMap<>();

        mDiscoveredPeers.put(mMockPeerProperties, new Timestamp(1000L));

        when(mMockPeerProperties.getDeviceAddress()).thenReturn(testDeviceAddress);

        Field mDiscoveredPeersField = mPeerModel.getClass().getDeclaredField("mDiscoveredPeers");
        mDiscoveredPeersField.setAccessible(true);
        mDiscoveredPeersField.set(mPeerModel, mDiscoveredPeers);
        PeerProperties pp = mPeerModel.getDiscoveredPeerByDeviceAddress(testDeviceAddress);
        assertThat("The proper peer properties instance is returned",
                pp, is(mMockPeerProperties));

    }

    @Test
    public void testRemovePeer() throws Exception {

        HashMap<PeerProperties, Timestamp> mDiscoveredPeers = new HashMap<>();

        mDiscoveredPeers.put(mMockPeerProperties, new Timestamp(1000L));

        Field mDiscoveredPeersField = mPeerModel.getClass().getDeclaredField("mDiscoveredPeers");
        mDiscoveredPeersField.setAccessible(true);
        mDiscoveredPeersField.set(mPeerModel, mDiscoveredPeers);

        PeerProperties pp = mPeerModel.removePeer(mMockPeerProperties);
        assertThat("The proper peer properties instance is returned",
                pp, is(mMockPeerProperties));

        assertThat("The given peer properties id removedfrom the collection",
                mDiscoveredPeers.size(), is(0));

        pp = mPeerModel.removePeer(mMockPeerProperties);
        assertThat("The null value is returned if the given peer properties not found",
                pp, is(nullValue()));

        pp = mPeerModel.removePeer(null);

        assertThat("Is null when the given peer properties is null",
                pp, is(nullValue()));
    }

    @Test
    public void testAddOrUpdateDiscoveredPeer_Add() throws Exception {
        doNothing().when(mMockListener).onPeerAdded(isA(PeerProperties.class));
        when(mMockDiscoveryManagerSettings.getPeerExpiration()).thenReturn(500L);

        mPeerModel.addOrUpdateDiscoveredPeer(mMockPeerProperties);

        verify(mMockListener, times(1)).onPeerAdded(isA(PeerProperties.class));
        verify(mMockListener, never()).onPeerUpdated(isA(PeerProperties.class));
        verify(mMockDiscoveryManagerSettings, times(1)).getPeerExpiration();

        Field discoveredPeersField = mPeerModel.getClass().getDeclaredField("mDiscoveredPeers");
        discoveredPeersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        HashMap<PeerProperties, Timestamp> peers
                = (HashMap<PeerProperties, Timestamp>) discoveredPeersField.get(mPeerModel);

        assertThat("The peer is added", peers.size(), is(1));
    }

    @Test
    public void testAddOrUpdateDiscoveredPeer_Update() throws Exception {
        // prepare a peer's list
        HashMap<PeerProperties, Timestamp> mDiscoveredPeers = new HashMap<>();
        //should use real time (not 1000L) because in method used value of System.currentTimeMillis()
        long initialTime = System.currentTimeMillis();
        mDiscoveredPeers.put(mMockPeerProperties, new Timestamp(initialTime));

        Field mDiscoveredPeersField = mPeerModel.getClass().getDeclaredField("mDiscoveredPeers");
        mDiscoveredPeersField.setAccessible(true);
        mDiscoveredPeersField.set(mPeerModel, mDiscoveredPeers);

        doNothing().when(mMockListener).onPeerUpdated(isA(PeerProperties.class));
        when(mMockDiscoveryManagerSettings.getPeerExpiration()).thenReturn(500L);
        when(mMockPeerProperties.hasMoreInformation(isA(PeerProperties.class))).thenReturn(true);

        mPeerModel.addOrUpdateDiscoveredPeer(mMockPeerProperties);

        verify(mMockListener, times(1)).onPeerUpdated(isA(PeerProperties.class));
        verify(mMockListener, never()).onPeerAdded(isA(PeerProperties.class));
        verify(mMockDiscoveryManagerSettings, times(1)).getPeerExpiration();

        Field discoveredPeersField = mPeerModel.getClass().getDeclaredField("mDiscoveredPeers");
        discoveredPeersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        HashMap<PeerProperties, Timestamp> peers
                = (HashMap<PeerProperties, Timestamp>) discoveredPeersField.get(mPeerModel);

        assertThat("The peer is added", peers.size(), is(1));
    }

    @Test
    public void testAddOrUpdateDiscoveredPeer_Update2() throws Exception {
        // prepare a peer's list
        HashMap<PeerProperties, Timestamp> mDiscoveredPeers = new HashMap<>();
        long initialTime = System.currentTimeMillis();
        mDiscoveredPeers.put(mMockPeerProperties, new Timestamp(initialTime));

        Field mDiscoveredPeersField = mPeerModel.getClass().getDeclaredField("mDiscoveredPeers");
        mDiscoveredPeersField.setAccessible(true);
        mDiscoveredPeersField.set(mPeerModel, mDiscoveredPeers);

        doNothing().when(mMockListener).onPeerUpdated(isA(PeerProperties.class));
        when(mMockDiscoveryManagerSettings.getPeerExpiration()).thenReturn(500L);
        when(mMockPeerProperties.hasMoreInformation(isA(PeerProperties.class))).thenReturn(false);

        mPeerModel.addOrUpdateDiscoveredPeer(mMockPeerProperties);

        verify(mMockListener, never()).onPeerUpdated(isA(PeerProperties.class));
        verify(mMockListener, never()).onPeerAdded(isA(PeerProperties.class));
        verify(mMockDiscoveryManagerSettings, times(1)).getPeerExpiration();

        Field discoveredPeersField = mPeerModel.getClass().getDeclaredField("mDiscoveredPeers");
        discoveredPeersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        HashMap<PeerProperties, Timestamp> peers
                = (HashMap<PeerProperties, Timestamp>) discoveredPeersField.get(mPeerModel);

        assertThat("The peer is added", peers.size(), is(1));
    }

    @Test
    public void testAddOrUpdateDiscoveredPeer_UpdateTimeslot() throws Exception {
        HashMap<PeerProperties, Timestamp> mDiscoveredPeers = new HashMap<>();
        long initialTime = System.currentTimeMillis();
        mDiscoveredPeers.put(mMockPeerProperties, new Timestamp(initialTime - DiscoveryManagerSettings.DEFAULT_PEER_PROPERTIES_UPDATE_PERIOD_IN_MILLISECONDS-1));

        Field mDiscoveredPeersField = mPeerModel.getClass().getDeclaredField("mDiscoveredPeers");
        mDiscoveredPeersField.setAccessible(true);
        mDiscoveredPeersField.set(mPeerModel, mDiscoveredPeers);

        doNothing().when(mMockListener).onPeerUpdated(isA(PeerProperties.class));
        when(mMockDiscoveryManagerSettings.getPeerExpiration()).thenReturn(DiscoveryManagerSettings.DEFAULT_PEER_EXPIRATION_IN_MILLISECONDS);
        when(mMockPeerProperties.hasMoreInformation(isA(PeerProperties.class))).thenReturn(false);

        mPeerModel.addOrUpdateDiscoveredPeer(mMockPeerProperties);

        verify(mMockListener, never()).onPeerUpdated(isA(PeerProperties.class));
        verify(mMockListener, times(1)).onPeerAdded(isA(PeerProperties.class));
        verify(mMockDiscoveryManagerSettings, times(1)).getPeerExpiration();
        Field discoveredPeersField = mPeerModel.getClass().getDeclaredField("mDiscoveredPeers");
        discoveredPeersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        HashMap<PeerProperties, Timestamp> peers
                = (HashMap<PeerProperties, Timestamp>) discoveredPeersField.get(mPeerModel);

        assertThat("The peer is added", peers.size(), is(1));
    }

    @Test
    public void testCheckListForExpiredPeers() throws Exception {
        HashMap<PeerProperties, Timestamp> mDiscoveredPeers = new HashMap<>();
        long hourBefore = new Date().getTime() - 3600 * 1000;
        long dayBefore = new Date().getTime() - 24 * 3600 * 1000;
        long halfDay = 12 * 3600 * 1000;

        when(mMockDiscoveryManagerSettings.getPeerExpiration()).thenReturn(halfDay);

        mDiscoveredPeers.put(mMockPeerProperties, new Timestamp(hourBefore));
        mDiscoveredPeers.put(mMockPeerProperties2, new Timestamp(dayBefore));

        Field mDiscoveredPeersField = mPeerModel.getClass().getDeclaredField("mDiscoveredPeers");
        mDiscoveredPeersField.setAccessible(true);
        mDiscoveredPeersField.set(mPeerModel, mDiscoveredPeers);

        mPeerModel.checkListForExpiredPeers();

        @SuppressWarnings("unchecked")
        HashMap<PeerProperties, Timestamp> peers
                = (HashMap<PeerProperties, Timestamp>) mDiscoveredPeersField.get(mPeerModel);

        assertThat("The expired peer is removed", peers.size(), is(1));

        verify(mMockListener, times(1)).onPeerExpiredAndRemoved(isA(PeerProperties.class));
    }
}