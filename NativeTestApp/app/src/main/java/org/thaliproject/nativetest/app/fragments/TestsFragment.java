/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativetest.app.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import org.thaliproject.nativetest.app.BatteryEngine;
import org.thaliproject.nativetest.app.R;
import org.thaliproject.nativetest.app.TestEngine;
import org.thaliproject.nativetest.app.test.AbstractTest;

import java.util.List;

/**
 * A fragment for selecting and running tests.
 */
public class TestsFragment extends Fragment {
    private static final String TAG = TestsFragment.class.getName();
    private Context mContext = null;
    private TestEngine mTestEngine = null;
    private Spinner mTestSelectorSpinner = null;
    private Button mRunTestButton = null;
    private int mSelectedTestIndex = 0;
    private BatteryEngine mBatteryEngine;
    private Button runBatteryTestButton = null;

    public TestsFragment() {
    }

    public void setTestEngine(TestEngine testEngine, BatteryEngine batteryEngine) {
        mTestEngine = testEngine;
        mBatteryEngine = batteryEngine;
        if (mTestEngine != null) {
            populateTestSelector();
            bindRunButton();
        }
        if (mBatteryEngine != null) {
            bindBatteryButton();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tests, container, false);
        mContext = view.getContext();

        mTestSelectorSpinner = (Spinner) view.findViewById(R.id.testSelectionSpinner);
        populateTestSelector();

        mRunTestButton = (Button) view.findViewById(R.id.runTestButton);
        bindRunButton();

        runBatteryTestButton = (Button) view.findViewById(R.id.runBatteryTest);
        bindBatteryButton();

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /**
     * Populates the test selector UI element.
     */
    private void populateTestSelector() {
        if (mTestSelectorSpinner != null && mTestSelectorSpinner.getAdapter() == null) {
            List<AbstractTest> tests = TestEngine.getTests();
            String[] testNames = new String[tests.size()];

            for (int i = 0; i < tests.size(); ++i) {
                testNames[i] = tests.get(i).getName();
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                    mContext, android.R.layout.simple_spinner_item, testNames);

            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mTestSelectorSpinner.setAdapter(adapter);

            mTestSelectorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int index, long l) {
                    mSelectedTestIndex = index;
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                }
            });
        }
    }

    private void bindRunButton() {
        if (mTestEngine != null && mRunTestButton != null) {
            mRunTestButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mTestEngine != null) {
                        if (mTestEngine.runTest(TestEngine.getTests().get(mSelectedTestIndex))) {
                            //mRunTestButton.setEnabled(false);
                        }
                    }
                }
            });
        }
    }

    private void bindBatteryButton() {
        if (mBatteryEngine != null && runBatteryTestButton != null) {
            runBatteryTestButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mBatteryEngine != null) {
                        if (!mBatteryEngine.isStarted()) {
                            runBatteryTestButton.setText(R.string.stop_battery_test);
                            mBatteryEngine.runTest();

                        } else {
                            runBatteryTestButton.setText(R.string.battery_test);
                            mBatteryEngine.onTestFinished();
                            mBatteryEngine.stop();

                        }
                    }
                }
            });
        }
    }
}
