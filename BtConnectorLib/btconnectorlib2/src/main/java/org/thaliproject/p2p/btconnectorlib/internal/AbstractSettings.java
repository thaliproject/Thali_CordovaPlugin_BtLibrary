/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * An abstract base class for settings of the discovery and the connection manager.
 */
public abstract class AbstractSettings {
    protected static Context mContext = null;
    protected SharedPreferences mSharedPreferences;
    protected SharedPreferences.Editor mSharedPreferencesEditor;

    public abstract void load();

    public abstract void resetToDefaults();
}
