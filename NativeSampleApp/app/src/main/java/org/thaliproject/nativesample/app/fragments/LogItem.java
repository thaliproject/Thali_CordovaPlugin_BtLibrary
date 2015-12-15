/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativesample.app.fragments;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;

/**
 * Contains data for a single log item.
 */
class LogItem {
    public Timestamp timestamp;
    public String timestampString = "";
    public String message = "";
    public boolean isError = false;

    public LogItem(Timestamp timestamp, String message, boolean isError) {
        this.timestamp = timestamp;
        this.timestampString = new SimpleDateFormat("hh:mm:ss").format(timestamp);
        this.message = message;
        this.isError = isError;
    }
}
