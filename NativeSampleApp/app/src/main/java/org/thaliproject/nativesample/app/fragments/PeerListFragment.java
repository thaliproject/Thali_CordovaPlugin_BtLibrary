package org.thaliproject.nativesample.app.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import org.thaliproject.nativesample.app.PeerAndConnectionModel;
import org.thaliproject.nativesample.app.R;
import org.thaliproject.p2p.btconnectorlib.PeerProperties;

/**
 *
 */
public class PeerListFragment extends Fragment implements PeerAndConnectionModel.Listener {
    public interface Listener {
        void onConnectRequest(PeerProperties peerProperties);
        void onSendDataRequest(PeerProperties peerProperties);
    }

    private static final String TAG = PeerListFragment.class.getName();
    private Context mContext = null;
    private ListView mListView = null;
    private ListAdapter mListAdapter = null;
    private PeerAndConnectionModel mModel = null;
    private Listener mListener = null;

    public PeerListFragment() {
    }

    public void setListener(Listener listener) {
        mListener = listener;
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
        mListAdapter = new ListAdapter(mContext);
        mListView = (ListView)view.findViewById(R.id.listView);
        mListView.setAdapter(mListAdapter);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                PeerProperties peerProperties = (PeerProperties)mListView.getItemAtPosition(position);

                if (mListener != null) {
                    mListener.onConnectRequest(peerProperties);
                } else {
                    Log.i(TAG, "onItemClick: " + peerProperties.toString() + " clicked, but I have no listener");
                }
            }
        });

        mModel.setListener(this);

        mListAdapter.notifyDataSetChanged();

        return view;
    }

    @Override
    public void onDestroy() {
        mModel.setListener(null);
        super.onDestroy();
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

            TextView text = (TextView)view.findViewById(R.id.peerName);
            text.setText(peerProperties.getName());

            text = (TextView)view.findViewById(R.id.peerId);
            text.setText(peerProperties.getId());

            boolean hasIncomingConnection = mModel.hasConnectionToPeer(peerProperties.getId(), true);
            boolean hasOutgoingConnection = mModel.hasConnectionToPeer(peerProperties.getId(), false);
            String connectionInformationText = "";

            if (hasIncomingConnection && hasOutgoingConnection) {
                connectionInformationText = "Connected (incoming and outgoing)";
            } else if (hasIncomingConnection) {
                connectionInformationText = "Connected (incoming)";
            } else if (hasOutgoingConnection) {
                connectionInformationText = "Connected (outgoing)";
            } else {
                connectionInformationText = "Not connected";
            }

            text = (TextView)view.findViewById(R.id.connectionsInformation);
            text.setText(connectionInformationText);

            return view;
        }
    }
}
