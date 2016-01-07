/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativesample.app.fragments;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.*;
import android.widget.*;
import org.thaliproject.nativesample.app.model.Connection;
import org.thaliproject.nativesample.app.model.PeerAndConnectionModel;
import org.thaliproject.nativesample.app.R;
import org.thaliproject.nativesample.app.utils.MenuUtils;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

/**
 * A fragment containing the list of discovered peers.
 */
@TargetApi(22)
public class PeerListFragment extends Fragment implements PeerAndConnectionModel.Listener {
    public interface Listener {
        void onPeerSelected(PeerProperties peerProperties);
        void onConnectRequest(PeerProperties peerProperties);
        void onSendDataRequest(PeerProperties peerProperties);
    }

    private static final String TAG = PeerListFragment.class.getName();
    private Context mContext = null;
    private Drawable mIncomingConnectionIconNotConnected = null;
    private Drawable mIncomingConnectionIconConnected = null;
    private Drawable mIncomingConnectionIconDataFlowing = null;
    private Drawable mOutgoingConnectionIconNotConnected = null;
    private Drawable mOutgoingConnectionIconConnected = null;
    private Drawable mOutgoingConnectionIconDataFlowing = null;
    private ListView mListView = null;
    private ListAdapter mListAdapter = null;
    private PeerAndConnectionModel mModel = null;
    private Listener mListener = null;
    private PeerProperties mSelectedPeerProperties = null;

    public PeerListFragment() {
    }

    public synchronized void setListener(Listener listener) {
        mListener = listener;

        if (mListener != null && mListView != null) {
            mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int index, long l) {
                    mSelectedPeerProperties = (PeerProperties) mListView.getItemAtPosition(index);
                    Log.i(TAG, "onItemClick: " + mSelectedPeerProperties.toString());

                    if (mListener != null) {
                        mListener.onPeerSelected(mSelectedPeerProperties);
                    }
                }
            });

            mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int index, long l) {
                    PeerProperties peerProperties = (PeerProperties)mListView.getItemAtPosition(index);
                    Log.i(TAG, "onItemLongClick: " + peerProperties.toString());
                    mSelectedPeerProperties = peerProperties;

                    if (mListener != null) {
                        mListener.onPeerSelected(mSelectedPeerProperties);
                    }

                    return false; // Let the event propagate
                }
            });

            mListView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int index, long l) {
                    mSelectedPeerProperties = (PeerProperties) mListView.getItemAtPosition(index);
                    Log.i(TAG, "onItemSelected: " + mSelectedPeerProperties.toString());

                    if (mListener != null) {
                        mListener.onPeerSelected(mSelectedPeerProperties);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                }
            });
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_peers, container, false);

        mModel = PeerAndConnectionModel.getInstance();
        mContext = view.getContext();

        mIncomingConnectionIconNotConnected = getResources().getDrawable(R.drawable.ic_arrow_downward_gray_24dp, mContext.getTheme());
        mIncomingConnectionIconConnected = getResources().getDrawable(R.drawable.ic_arrow_downward_blue_24dp, mContext.getTheme());
        mIncomingConnectionIconDataFlowing = getResources().getDrawable(R.drawable.ic_arrow_downward_green_24dp, mContext.getTheme());
        mOutgoingConnectionIconNotConnected = getResources().getDrawable(R.drawable.ic_arrow_upward_gray_24dp, mContext.getTheme());
        mOutgoingConnectionIconConnected = getResources().getDrawable(R.drawable.ic_arrow_upward_blue_24dp, mContext.getTheme());
        mOutgoingConnectionIconDataFlowing = getResources().getDrawable(R.drawable.ic_arrow_upward_green_24dp, mContext.getTheme());

        mListAdapter = new ListAdapter(mContext);

        mListView = (ListView)view.findViewById(R.id.listView);
        mListView.setAdapter(mListAdapter);
        mListView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        registerForContextMenu(mListView);
        setListener(mListener);

        mModel.setListener(this);

        return view;
    }

    @Override
    public void onDestroy() {
        mModel.setListener(null);
        super.onDestroy();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.menu_peers, menu);

        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
        int position = info.position;
        PeerProperties peerProperties = (PeerProperties) mListView.getItemAtPosition(position);

        MenuUtils.PeerMenuItemsAvailability availability =
                MenuUtils.resolvePeerMenuItemsAvailability(peerProperties, mModel);

        MenuItem connectMenuItem = menu.getItem(0);
        MenuItem disconnectMenuItem = menu.getItem(1);
        MenuItem sendDataMenuItem = menu.getItem(2);

        connectMenuItem.setEnabled(availability.connectMenuItemAvailable);
        sendDataMenuItem.setEnabled(availability.sendDataMenuItemAvailable);
        disconnectMenuItem.setEnabled(availability.disconnectMenuItemAvailable);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        boolean wasConsumed = false;

        if (mListener != null) {
            int id = item.getItemId();

            switch (id) {
                case R.id.action_connect:
                    mListener.onConnectRequest(mSelectedPeerProperties); // Has a null check
                    wasConsumed = true;
                    break;
                case R.id.action_disconnect:
                    if (mSelectedPeerProperties != null) {
                        if (mModel.closeConnection(mSelectedPeerProperties)) {
                            wasConsumed = true;
                        }
                    }

                    break;
                case R.id.action_send_data:
                    mListener.onSendDataRequest(mSelectedPeerProperties); // Has a null check
                    wasConsumed = true;
                    break;
            }
        }

        return wasConsumed || super.onContextItemSelected(item);
    }

    @Override
    public void onDataChanged() {
        Log.i(TAG, "onDataChanged");
        Handler handler = new Handler(mContext.getMainLooper());

        handler.post(new Runnable() {
            @Override
            public void run() {
                mListAdapter.notifyDataSetChanged();
            }
        });
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
            return mModel.getPeers().size();
        }

        @Override
        public Object getItem(int position) {
            return mModel.getPeers().get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;

            if (view == null) {
                view = mInflater.inflate(R.layout.list_item_peer, null);
            }

            PeerProperties peerProperties = mModel.getPeers().get(position);

            TextView textView = (TextView) view.findViewById(R.id.peerName);
            textView.setText(peerProperties.getName());

            textView = (TextView) view.findViewById(R.id.peerId);
            textView.setText(peerProperties.getId());

            boolean hasIncomingConnection = mModel.hasConnectionToPeer(peerProperties, true);
            boolean hasOutgoingConnection = mModel.hasConnectionToPeer(peerProperties, false);
            ImageView outgoingConnectionIconImageView = (ImageView) view.findViewById(R.id.outgoingConnectionIconImageView);
            ImageView incomingConnectionIconImageView = (ImageView) view.findViewById(R.id.incomingConnectionIconImageView);
            String connectionInformationText = "";

            if (hasIncomingConnection && hasOutgoingConnection) {
                incomingConnectionIconImageView.setImageDrawable(mIncomingConnectionIconConnected);
                outgoingConnectionIconImageView.setImageDrawable(mOutgoingConnectionIconConnected);
                connectionInformationText = "Connected (incoming and outgoing)";
            } else if (hasIncomingConnection) {
                incomingConnectionIconImageView.setImageDrawable(mIncomingConnectionIconConnected);
                outgoingConnectionIconImageView.setImageDrawable(mOutgoingConnectionIconNotConnected);
                connectionInformationText = "Connected (incoming)";
            } else if (hasOutgoingConnection) {
                incomingConnectionIconImageView.setImageDrawable(mIncomingConnectionIconNotConnected);
                outgoingConnectionIconImageView.setImageDrawable(mOutgoingConnectionIconConnected);
                connectionInformationText = "Connected (outgoing)";
            } else {
                incomingConnectionIconImageView.setImageDrawable(mIncomingConnectionIconNotConnected);
                outgoingConnectionIconImageView.setImageDrawable(mOutgoingConnectionIconNotConnected);
                connectionInformationText = "Not connected";
            }

            Connection connectionResponsibleForSendingData = null;

            if (hasOutgoingConnection) {
                connectionResponsibleForSendingData = mModel.getConnectionToPeer(peerProperties, false);
            } else if (hasIncomingConnection) {
                connectionResponsibleForSendingData = mModel.getConnectionToPeer(peerProperties, true);
            }

            ProgressBar progressBar = (ProgressBar) view.findViewById(R.id.sendDataProgressBar);
            progressBar.setIndeterminate(false);
            progressBar.setMax(100);

            if (connectionResponsibleForSendingData != null
                    && connectionResponsibleForSendingData.isSendingData()) {
                if (connectionResponsibleForSendingData.getIsIncoming()) {
                    incomingConnectionIconImageView.setImageDrawable(mIncomingConnectionIconDataFlowing);
                } else {
                    outgoingConnectionIconImageView.setImageDrawable(mOutgoingConnectionIconDataFlowing);
                }

                progressBar.setProgress((int)(connectionResponsibleForSendingData.getSendDataProgress() * 100));

                connectionInformationText = "Sending "
                        + String.format("%.2f", connectionResponsibleForSendingData.getTotalDataAmountCurrentlySendingInMegaBytes())
                        + " MB (" + String.format("%.3f", connectionResponsibleForSendingData.getCurrentDataTransferSpeedInMegaBytesPerSecond())
                        + " MB/s)";
            } else {
                progressBar.setProgress(0);
            }

            textView = (TextView) view.findViewById(R.id.connectionsInformation);
            textView.setText(connectionInformationText);

            return view;
        }
    }
}
