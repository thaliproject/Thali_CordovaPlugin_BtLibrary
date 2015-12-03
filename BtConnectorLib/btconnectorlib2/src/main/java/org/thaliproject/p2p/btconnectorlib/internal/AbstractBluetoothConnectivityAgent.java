package org.thaliproject.p2p.btconnectorlib.internal;

import android.content.Context;
import android.util.Log;
import org.json.JSONException;
import org.thaliproject.p2p.btconnectorlib.internal.bluetooth.BluetoothManager;

/**
 *
 */
public abstract class AbstractBluetoothConnectivityAgent implements BluetoothManager.BluetoothManagerListener {
    protected static String TAG = AbstractBluetoothConnectivityAgent.class.getName();
    protected final BluetoothManager mBluetoothManager;
    protected String mMyPeerId = null;
    protected String mMyPeerName = null;
    protected String mMyIdentityString = "";

    /**
     * Constructor.
     * @param context The application context.
     */
    public AbstractBluetoothConnectivityAgent(Context context) {
        mBluetoothManager = BluetoothManager.getInstance(context);
    }

    /**
     * Verifies the validity of our identity string. If the not yet created, will try to create it.
     * If the identity string already exists, it won't be recreated.
     * @return True, if the identity string is OK (i.e. not empty). False otherwise.
     */
    protected boolean verifyIdentityString() {
        if ((mMyIdentityString == null || mMyIdentityString.length() == 0)
                && mMyPeerId != null && mMyPeerName != null
                && mBluetoothManager.isBluetoothEnabled()) {
            try {
                mMyIdentityString = CommonUtils.createIdentityString(
                        mMyPeerId, mMyPeerName, mBluetoothManager.getBluetoothAddress());
                Log.i(TAG, "verifyIdentityString: Identity string created: " + mMyIdentityString);
            } catch (JSONException e) {
                Log.e(TAG, "verifyIdentityString: Failed create an identity string: " + e.getMessage(), e);
            }
        }

        return (mMyIdentityString != null && mMyIdentityString.length() > 0);
    }

    @Override
    abstract public void onBluetoothAdapterScanModeChanged(int mode);
}
