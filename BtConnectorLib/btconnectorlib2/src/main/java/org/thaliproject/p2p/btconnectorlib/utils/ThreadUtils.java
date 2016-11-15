/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */

package org.thaliproject.p2p.btconnectorlib.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.Locale;

public class ThreadUtils {

    public static String currentThreadToString() {
        return String.format(Locale.getDefault(), "Current thread: %s, id: %d", Thread.currentThread().toString(), Thread.currentThread().getId());
    }

    public static boolean postToMainHandler(Runnable action) {
        Handler h = new Handler(Looper.getMainLooper());
        return h.post(action);
    }
}
