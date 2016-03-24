/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.p2p.btconnectorlib.internal;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * An abstract base class for settings of the discovery and the connection manager.
 */
public abstract class AbstractSettings {
    protected static Context mContext = null;
    protected SharedPreferences mSharedPreferences;
    protected SharedPreferences.Editor mSharedPreferencesEditor;
    protected boolean mLoaded = false;

    /**
     * Constructor.
     */
    protected AbstractSettings(Context context) {
        this(context, PreferenceManager.getDefaultSharedPreferences(context));
    }

    /**
     * Constructor.
     */
    protected AbstractSettings(Context context, SharedPreferences sharedPreferences) {
        if (context == null) {
            throw new NullPointerException("Context is null");
        }

        mContext = context;
        mSharedPreferences = sharedPreferences;
        mSharedPreferencesEditor = mSharedPreferences.edit();
    }

    public abstract void load();

    public abstract void resetDefaults();
}
