package org.thaliproject.p2p.btlib_testapp;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.JsonReader;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.thaliproject.p2p.btconnectorlib.BTConnector;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity implements BtConnectorHelper.jxCallBack {


    MainActivity that = this;
    BtConnectorHelper mBtConnectorHelper = null;
    String peerIdentifier = "";
    String peerName = "";

    String LastpeerIdentifier = "";
    boolean isRunning = false;

    ConnectivityMonitor mConnectivityMonitor = null;
    LifeCycleMonitor mLifeCycleMonitor = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mConnectivityMonitor = new ConnectivityMonitor(that, that);
        mConnectivityMonitor.Start();

        mLifeCycleMonitor = new LifeCycleMonitor(that, that);
        mLifeCycleMonitor.Start();

        mBtConnectorHelper = new BtConnectorHelper(that, that);

        peerName = mBtConnectorHelper.GetDeviceName();
        peerIdentifier = mBtConnectorHelper.MakeGUID();

        Button clButton = (Button) findViewById(R.id.button2);
        clButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((TextView)findViewById(R.id.debugdataBox)).setText("cleared.\n");
            }
        });

        Button cuntButton = (Button) findViewById(R.id.toglebut);
        cuntButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mBtConnectorHelper != null){
                    if(isRunning) {
                        mBtConnectorHelper.Stop();
                        print_text("Stopped");
                        isRunning = false;
                    }else{
                        mBtConnectorHelper.Start(that.peerIdentifier, that.peerName);
                        print_text("Started");
                        isRunning = true;
                    }
                }else {
                    ((TextView) findViewById(R.id.debugdataBox)).append("mBtConnectorHelper is null\n");
                }
            }
        });

        Button toggleButton = (Button) findViewById(R.id.conBut);
        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mBtConnectorHelper != null){
                    mBtConnectorHelper.BeginConnectPeer(LastpeerIdentifier);

                }else {
                    ((TextView) findViewById(R.id.debugdataBox)).append("mBtConnectorHelper is null\n");
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(mLifeCycleMonitor != null) {
            mLifeCycleMonitor.Stop();
            mLifeCycleMonitor = null;
        }

        if(mConnectivityMonitor != null) {
            mConnectivityMonitor.Stop();
            mConnectivityMonitor = null;
        }

        if(mBtConnectorHelper != null) {
            mBtConnectorHelper.Stop();
            mBtConnectorHelper = null;
        }
    }

    private void print_text(String message){
        String orgText = ((TextView)findViewById(R.id.debugdataBox)).getText().toString();
        ((TextView)findViewById(R.id.debugdataBox)).setText(message + "\n" + orgText);
        Log.i("Bt_TestApp", message);
    }

    @Override
    public void CallJSMethod(String method, String jsonData) {

        if(method.equalsIgnoreCase("peerChanged")) {
            print_text("Peers changed: " + " : " + jsonData);
            try {

                JSONArray json = new JSONArray(jsonData);

                for (int i = 0; i < json.length(); i++) {
                    JSONObject e = json.getJSONObject(i);
                    String state = e.getString("state");
                    String peerId = e.getString("peerIdentifier");

                    if(state.equalsIgnoreCase("Available")) {
                        LastpeerIdentifier = peerId;
                    }

                    String peerName = e.getString("peerName");
                    print_text(peerName + " is " + state + " with Id: " + peerId);

                }
            } catch (Exception e) {
                print_text("JSON parsing error: " + e.toString());
            }
        }else{
            print_text(method + " : " + jsonData);
        }
    }
}
