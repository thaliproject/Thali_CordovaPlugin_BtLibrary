/* Copyright (c) 2015 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativesample.app.fragments;

import android.bluetooth.le.AdvertiseSettings;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.thaliproject.nativesample.app.R;
import org.thaliproject.nativesample.app.model.Settings;

/**
 * A fragment for changing the application settings.
 */
public class SettingsFragment extends Fragment {
    private static final String TAG = SettingsFragment.class.getName();
    private Settings mSettings = null;
    private EditText mConnectionTimeoutEditText = null;
    private EditText mPortNumberEditText = null;
    private CheckBox mEnableWifiCheckBox = null;
    private CheckBox mEnableBleCheckBox = null;
    private EditText mBufferSizeEditText = null;
    private EditText mDataAmountEditText = null;
    private CheckBox mEnableAutoConnectCheckBox = null;
    private CheckBox mEnableAutoConnectEvenWhenIncomingConnectionEstablishedCheckBox = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        mSettings = Settings.getInstance(view.getContext());
        mSettings.load();

        mConnectionTimeoutEditText = (EditText) view.findViewById(R.id.connectionTimeoutEditText);
        mConnectionTimeoutEditText.setText(String.valueOf(mSettings.getConnectionTimeout()));
        mConnectionTimeoutEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.length() > 0) {
                    try {
                        mSettings.setConnectionTimeout(Long.parseLong(editable.toString()));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, e.getMessage(), e);
                        mConnectionTimeoutEditText.setText(String.valueOf(mSettings.getConnectionTimeout()));
                    }
                }
            }
        });

        mPortNumberEditText = (EditText) view.findViewById(R.id.portNumberEditText);
        mPortNumberEditText.setText(String.valueOf(mSettings.getPortNumber()));
        mPortNumberEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.length() > 0 && !editable.toString().equals("-")) {
                    try {
                        mSettings.setPortNumber(Integer.parseInt(editable.toString()));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, e.getMessage(), e);
                        mPortNumberEditText.setText(String.valueOf(mSettings.getPortNumber()));
                    }
                }
            }
        });

        mEnableWifiCheckBox = (CheckBox) view.findViewById(R.id.enableWifiCheckBox);
        mEnableWifiCheckBox.setChecked(mSettings.getEnableWifiDiscovery());
        mEnableWifiCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mSettings.setEnableWifiDiscovery(b);
            }
        });

        mEnableBleCheckBox = (CheckBox) view.findViewById(R.id.enableBleCheckBox);
        mEnableBleCheckBox.setChecked(mSettings.getEnableBleDiscovery());
        mEnableBleCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mSettings.setEnableBleDiscovery(b);
            }
        });

        Spinner spinner = (Spinner) view.findViewById(R.id.advertiseModeSpinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                view.getContext(), R.array.advertise_mode_string_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(mSettings.getAdvertiseMode());
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int index, long l) {
                mSettings.setAdvertiseMode(index);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        spinner = (Spinner) view.findViewById(R.id.advertiseTxPowerLevelSpinner);
        adapter = ArrayAdapter.createFromResource(
                view.getContext(), R.array.advertise_tx_power_level_string_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(mSettings.getAdvertiseTxPowerLevel());
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int index, long l) {
                mSettings.setAdvertiseTxPowerLevel(index);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        spinner = (Spinner) view.findViewById(R.id.scanModeSpinner);
        adapter = ArrayAdapter.createFromResource(
                view.getContext(), R.array.scan_mode_string_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(mSettings.getScanMode());
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int index, long l) {
                mSettings.setScanMode(index);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        mBufferSizeEditText = (EditText) view.findViewById(R.id.bufferSizeEditText);
        mBufferSizeEditText.setText(String.valueOf(mSettings.getBufferSize()));
        mBufferSizeEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.length() > 0) {
                    try {
                        mSettings.setBufferSize(Integer.parseInt(editable.toString()));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, e.getMessage(), e);
                        mBufferSizeEditText.setText(String.valueOf(mSettings.getBufferSize()));
                    }
                }
            }
        });

        mDataAmountEditText = (EditText) view.findViewById(R.id.dataAmountEditText);
        mDataAmountEditText.setText(String.valueOf(mSettings.getDataAmount()));
        mDataAmountEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.length() > 0) {
                    try {
                        mSettings.setDataAmount(Long.parseLong(editable.toString()));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, e.getMessage(), e);
                        mDataAmountEditText.setText(String.valueOf(mSettings.getDataAmount()));
                    }
                }
            }
        });

        mEnableAutoConnectCheckBox = (CheckBox) view.findViewById(R.id.autoConnectCheckBox);
        mEnableAutoConnectCheckBox.setChecked(mSettings.getAutoConnect());
        mEnableAutoConnectCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mSettings.setAutoConnect(b);
            }
        });

        mEnableAutoConnectEvenWhenIncomingConnectionEstablishedCheckBox =
                (CheckBox) view.findViewById(R.id.autoConnectEvenWhenIncomingCheckBox);
        mEnableAutoConnectEvenWhenIncomingConnectionEstablishedCheckBox.setChecked(
                mSettings.getAutoConnectEvenWhenIncomingConnectionEstablished());
        mEnableAutoConnectEvenWhenIncomingConnectionEstablishedCheckBox
                .setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mSettings.setAutoConnectEvenWhenIncomingConnectionEstablished(b);
            }
        });

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
