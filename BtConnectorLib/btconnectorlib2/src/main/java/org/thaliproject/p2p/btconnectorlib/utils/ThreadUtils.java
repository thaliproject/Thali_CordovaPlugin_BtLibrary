/* Copyright (c) 2015-2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */

package org.thaliproject.p2p.btconnectorlib.utils;

import java.util.Locale;

public class ThreadUtils {

    public static String currentThreadToString(){
        return String.format(Locale.getDefault(), "Current thread: %s, id: %d", Thread.currentThread().toString(), Thread.currentThread().getId());
    }
}
