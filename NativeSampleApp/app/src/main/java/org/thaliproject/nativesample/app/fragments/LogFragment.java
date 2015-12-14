/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativesample.app.fragments;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import org.thaliproject.nativesample.app.R;
import java.sql.Timestamp;
import java.util.Date;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A fragment managing and displaying the log items.
 */
public class LogFragment extends Fragment {
    private static final String TAG = LogFragment.class.getName();
    private static final int MAX_NUMBER_OF_LOG_ITEMS = 50;
    private Context mContext = null;
    private ListView mListView = null;
    private ListAdapter mListAdapter = null;
    private CopyOnWriteArrayList<LogItem> mLog = new CopyOnWriteArrayList<LogItem>();
    private ColorStateList mDefaultTextViewColors = null;

    public LogFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log, container, false);

        mContext = view.getContext();
        mListAdapter = new ListAdapter(mContext);

        mListView = (ListView)view.findViewById(R.id.listView);
        mListView.setAdapter(mListAdapter);

        return view;
    }

    /**
     * Adds a new log item with the given message.
     * @param message The message for the log item.
     */
    public void logMessage(String message) {
        addLogItem(message, false);
    }

    /**
     * Adds a new log item with the given error message.
     * @param errorMessage The error message for the log item.
     */
    public void logError(String errorMessage) {
        addLogItem(errorMessage, true);
    }

    /**
     * Adds a new log item with the given message.
     * @param message The message for the log item.
     * @param isError If true, will mark this message as an error.
     */
    private synchronized void addLogItem(String message, boolean isError) {
        Timestamp timestamp = new Timestamp(new Date().getTime());
        LogItem logItem = new LogItem(timestamp, message, isError);
        mLog.add(0, logItem);

        if (mLog.size() > MAX_NUMBER_OF_LOG_ITEMS) {
            mLog.remove(mLog.size() - 1); // Remove the last item
        }

        if (mContext != null) {
            Handler handler = new Handler(mContext.getMainLooper());

            handler.post(new Runnable() {
                @Override
                public void run() {
                    mListAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    class ListAdapter extends BaseAdapter {
        private LayoutInflater mInflater = null;
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
            mInflater = (LayoutInflater) mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return mLog.size();
        }

        @Override
        public Object getItem(int position) {
            return mLog.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;

            if (view == null) {
                view = mInflater.inflate(R.layout.log_item, null);
            }

            LogItem logItem = mLog.get(position);

            TextView textView = (TextView)view.findViewById(R.id.timestamp);
            textView.setText(logItem.timestampString);

            if (mDefaultTextViewColors == null) {
                // Store the original colors
                mDefaultTextViewColors = textView.getTextColors();
            }

            if (logItem.isError) {
                textView.setTextColor(Color.RED);
            } else {
                textView.setTextColor(mDefaultTextViewColors);
            }

            textView = (TextView)view.findViewById(R.id.message);
            textView.setText(logItem.message);

            if (logItem.isError) {
                textView.setTextColor(Color.RED);
            } else {
                textView.setTextColor(mDefaultTextViewColors);
            }

            return view;
        }
    }
}
