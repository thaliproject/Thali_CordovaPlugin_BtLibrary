package org.thaliproject.p2p.btconnectorlib.internal.bluetooth.le;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.thaliproject.p2p.btconnectorlib.DiscoveryManagerSettings;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BleScannerTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    @Mock
    BluetoothAdapter mMockBluetoothAdapter;
    @Mock
    AdvertiseData mMockAdvertiseData;
    @Mock
    AdvertiseData mTmpMockAdvertiseData;
    @Mock
    BleScanner.Listener mMockListener;
    @Mock
    ScanSettings.Builder mMockBuilder;
    @Mock
    DiscoveryManagerSettings mMockDiscoveryManagerSettings;
    @Mock
    ScanSettings mMockScanSettings;
    @Mock
    BluetoothLeScanner mMockBluetoothLeScanner;
    @Mock
    List<ScanFilter> mMockScanFilters;
    @Mock
    ScanFilter mMockScanFilter;
    @Mock
    ScanResult mMockScanResult1;
    @Mock
    ScanResult mMockScanResult2;
    BleScanner mBleScanner;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockBuilder.build()).thenReturn(mMockScanSettings);

        when(mMockDiscoveryManagerSettings.getScanMode()).thenReturn(
                DiscoveryManagerSettings.DEFAULT_SCAN_MODE);

        when(mMockDiscoveryManagerSettings.getScanReportDelay()).thenReturn(
                DiscoveryManagerSettings.DEFAULT_SCAN_REPORT_DELAY_IN_FOREGROUND_IN_MILLISECONDS);

        when(mMockBluetoothAdapter.getBluetoothLeScanner()).thenReturn(
                mMockBluetoothLeScanner);

        mBleScanner = new BleScanner(mMockListener, mMockBluetoothAdapter, mMockBuilder,
                mMockDiscoveryManagerSettings);

        Field scanFiltersField = mBleScanner.getClass().getDeclaredField("mScanFilters");
        scanFiltersField.setAccessible(true);
        scanFiltersField.set(mBleScanner, mMockScanFilters);
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void bleScannerConstructor_noSettings() throws Exception {
        reset(mMockBuilder);
        when(mMockBuilder.build()).thenReturn(mMockScanSettings);

        BleScanner bleScanner
                = new BleScanner(mMockListener, mMockBluetoothAdapter, mMockBuilder,
                null);

        assertThat("The BleScanner is properly created",
                bleScanner, is(notNullValue()));

        verify(mMockBuilder, times(1)).setScanMode(
                DiscoveryManagerSettings.DEFAULT_SCAN_MODE);

        verify(mMockBuilder, times(1)).setReportDelay(
                DiscoveryManagerSettings.DEFAULT_SCAN_REPORT_DELAY_IN_FOREGROUND_IN_MILLISECONDS);

        Field scanSettingsField = bleScanner.getClass().getDeclaredField("mScanSettings");
        scanSettingsField.setAccessible(true);

        assertThat("The scan settings is set",
                scanSettingsField.get(bleScanner), is(notNullValue()));
    }

    @Test
    public void bleScannerConstructor_withSettings() throws Exception {
        reset(mMockBuilder);
        when(mMockBuilder.build()).thenReturn(mMockScanSettings);

        when(mMockDiscoveryManagerSettings.getScanMode()).thenReturn(ScanSettings.SCAN_MODE_LOW_POWER);
        when(mMockDiscoveryManagerSettings.getScanReportDelay()).thenReturn(600L);

        BleScanner bleScanner
                = new BleScanner(mMockListener, mMockBluetoothAdapter, mMockBuilder,
                mMockDiscoveryManagerSettings);

        assertThat("The BleScanner is properly created",
                bleScanner, is(notNullValue()));

        verify(mMockBuilder, times(1)).setScanMode(
                ScanSettings.SCAN_MODE_LOW_POWER);

        verify(mMockBuilder, times(1)).setReportDelay(600L);

        Field scanSettingsField = bleScanner.getClass().getDeclaredField("mScanSettings");
        scanSettingsField.setAccessible(true);

        assertThat("The scan settings is set",
                scanSettingsField.get(bleScanner), is(notNullValue()));
    }

    @Ignore("https://github.com/thaliproject/Thali_CordovaPlugin_BtLibrary/issues/92")
    @Test
    public void testStart() throws Exception {

        assertThat("It returns true when the BLE scanning is started",
                mBleScanner.start(), is(true));
        //noinspection unchecked
        verify(mMockBluetoothLeScanner, times(1)).startScan(mMockScanFilters,
                mMockScanSettings, mBleScanner);
        verify(mMockListener, times(1)).onIsScannerStartedChanged(true);
        assertThat("It should return true as the scanner is either starting or running",
                mBleScanner.isStarted(), is(true));

        reset(mMockBluetoothLeScanner);
        // repeat start
        assertThat("It should return true as scanning is already started",
                mBleScanner.start(), is(true));

        //noinspection unchecked
        verify(mMockBluetoothLeScanner, never()).startScan(any(List.class),
                any(ScanSettings.class), any(BleScanner.class));
    }

    @Test
    public void testStart_exception() throws Exception {

        //noinspection unchecked
        doThrow(IllegalArgumentException.class).when(mMockBluetoothLeScanner)
                .startScan(any(List.class),
                        any(ScanSettings.class), any(BleScanner.class));

        assertThat("It should return false as the scanning is not started",
                mBleScanner.start(), is(false));

        assertThat("It should remain false as the scanner is neither starting nor running",
                mBleScanner.isStarted(), is(false));
    }

    @Test
    public void testStart_noScannerInstance() throws Exception {
        when(mMockBluetoothAdapter.getBluetoothLeScanner()).thenReturn(null);
        BleScanner bleScanner
                = new BleScanner(mMockListener, mMockBluetoothAdapter, mMockBuilder,
                mMockDiscoveryManagerSettings);

        assertThat("It should return false as the scanning is not started",
                bleScanner.start(), is(false));

        assertThat("It should remain false as the scanning is neither starting nor running",
                bleScanner.isStarted(), is(false));
    }

    @Ignore("https://github.com/thaliproject/Thali_CordovaPlugin_BtLibrary/issues/92")
    @Test
    public void testStop_noBTLEScanner() throws Exception {

        Field scannerField = mBleScanner.getClass().getDeclaredField("mBluetoothLeScanner");
        scannerField.setAccessible(true);
        mBleScanner.start();

        // remove Scanner
        scannerField.set(mBleScanner, null);

        mBleScanner.stop(true);

        verify(mMockListener, times(1)).onIsScannerStartedChanged(false);

        assertThat("It should be set to false as the scanning is stopped",
                mBleScanner.isStarted(), is(false));
    }

    @Ignore("https://github.com/thaliproject/Thali_CordovaPlugin_BtLibrary/issues/92")
    @Test
    public void testStop_notify() throws Exception {
        mBleScanner.start();
        reset(mMockListener);
        mBleScanner.stop(true);

        verify(mMockBluetoothLeScanner, times(1)).stopScan(mBleScanner);
        verify(mMockListener, times(1)).onIsScannerStartedChanged(false);

        assertThat("It should be set to false as the scanner is stopped",
                mBleScanner.isStarted(), is(false));
    }

    @Test
    public void testStop_dontNotify() throws Exception {
        mBleScanner.start();
        reset(mMockListener);
        mBleScanner.stop(false);

        verify(mMockBluetoothLeScanner, times(1)).stopScan(mBleScanner);
        verify(mMockListener, never()).onIsScannerStartedChanged(anyBoolean());

        assertThat("It should be set to false as the scanner is stopped",
                mBleScanner.isStarted(), is(false));
    }

    @Test
    public void testClearScanFilters_restart() throws Exception {
        mBleScanner.start();
        mBleScanner.clearScanFilters();

        verify(mMockScanFilters, times(1)).clear();
        assertThat("It should be true as the scanner was started",
                mBleScanner.isStarted(), is(true));
    }

    @Test
    public void testClearScanFilters_noRestart() throws Exception {
        mBleScanner.stop(false);
        mBleScanner.clearScanFilters();

        verify(mMockScanFilters, times(1)).clear();
        assertThat("It should remain false as the scanner was not started",
                mBleScanner.isStarted(), is(false));
    }

    @Test
    public void testAddScanFilter_restart() throws Exception {
        mBleScanner.start();
        mBleScanner.addScanFilter(mMockScanFilter);

        verify(mMockScanFilters, times(1)).add(mMockScanFilter);
        assertThat("It should be true as the scanner was started",
                mBleScanner.isStarted(), is(true));

    }

    @Test
    public void testAddScanFilter_noRestart() throws Exception {
        mBleScanner.stop(false);
        mBleScanner.addScanFilter(mMockScanFilter);

        verify(mMockScanFilters, times(1)).add(mMockScanFilter);
        assertThat("It should be false as the scanner was not started",
                mBleScanner.isStarted(), is(false));
    }

    @Test
    public void testSetScanSettings() throws Exception {
        Field scanSettingsField = mBleScanner.getClass().getDeclaredField("mScanSettings");
        scanSettingsField.setAccessible(true);

        mBleScanner.setScanSettings(mMockScanSettings);

        assertThat("The scan settings is properly set",
                (ScanSettings) scanSettingsField.get(mBleScanner), is(mMockScanSettings));
    }

    @Test
    public void testSetScanSettings_exception() throws Exception {
        thrown.expect(NullPointerException.class);
        mBleScanner.setScanSettings(null);
    }

    @Test
    public void testOnBatchScanResults() throws Exception {
        List<ScanResult> scanResults = new ArrayList<>();
        scanResults.add(mMockScanResult1);
        scanResults.add(mMockScanResult2);
        scanResults.add(null);

        mBleScanner.onBatchScanResults(scanResults);
        verify(mMockListener, times(1)).onScanResult(mMockScanResult1);
        verify(mMockListener, times(1)).onScanResult(mMockScanResult2);
        verify(mMockListener, times(2)).onScanResult(any(ScanResult.class));
    }

    @Ignore("https://github.com/thaliproject/Thali_CordovaPlugin_BtLibrary/issues/92")
    @Test
    public void testOnScanFailed() throws Exception {
        mBleScanner.start();

        // test
        mBleScanner.onScanFailed(ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED);
        verify(mMockBluetoothLeScanner, never()).stopScan(mBleScanner);
        verify(mMockListener, times(1)).onIsScannerStartedChanged(false);
        verify(mMockListener, times(1)).onScannerFailed(
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED);
        assertThat("It should be false as the scanner is stopped",
                mBleScanner.isStarted(), is(false));
    }

    @Ignore
    @Test
    public void testOnScanFailed_alreadyStarted() throws Exception {
        mBleScanner.start();

        // test
        mBleScanner.onScanFailed(ScanCallback.SCAN_FAILED_ALREADY_STARTED);
        verify(mMockBluetoothLeScanner, times(1)).stopScan(mBleScanner);
        verify(mMockListener, times(1)).onIsScannerStartedChanged(false);
        verify(mMockListener, times(1)).onScannerFailed(
                ScanCallback.SCAN_FAILED_ALREADY_STARTED);
        assertThat("It should be false as the scanner is stopped",
                mBleScanner.isStarted(), is(false));
    }

    @Test
    public void testOnScanResult() throws Exception {
        mBleScanner.onScanResult(1, null);
        verify(mMockListener, never()).onScanResult(any(ScanResult.class));

        mBleScanner.onScanResult(1, mMockScanResult1);
        verify(mMockListener, times(1)).onScanResult(mMockScanResult1);
    }
}