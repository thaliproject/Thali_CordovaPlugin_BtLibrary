/* Copyright (c) 2016 Microsoft Corporation. This software is licensed under the MIT License.
 * See the license file delivered with this project for further information.
 */
package org.thaliproject.nativetest.app.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.thaliproject.nativetest.app.R;
import org.thaliproject.nativetest.app.TestEngine;
import org.thaliproject.nativetest.app.test.AbstractTest;
import org.thaliproject.nativetest.app.test.FindMyBluetoothAddressTest;
import java.util.ArrayList;
import java.util.List;

/**
 * A fragment for selecting and running tests.
 */
public class TestsFragment extends Fragment {
    private static final String TAG = TestsFragment.class.getName();
    private TestEngine mTestEngine = null;
    private List<AbstractTest> mTests = new ArrayList<AbstractTest>();
    private Button mRunTestButton = null;
    private int mSelectedTestIndex = 0;

    public TestsFragment() {
        mTests.add(new FindMyBluetoothAddressTest());
    }

    public void setTestEngine(TestEngine testEngine) {
        mTestEngine = testEngine;

        if (mTestEngine != null && mRunTestButton != null) {
            mRunTestButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mTestEngine != null) {
                        if (mTestEngine.runTest(mTests.get(mSelectedTestIndex))) {
                            //mRunTestButton.setEnabled(false);
                        }
                    }
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
        View view = inflater.inflate(R.layout.fragment_tests, container, false);

        Spinner spinner = (Spinner) view.findViewById(R.id.testSelectionSpinner);
        String[] testNames = new String[mTests.size()];

        for (int i = 0; i < mTests.size(); ++i) {
            testNames[i] = mTests.get(i).getName();
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                view.getContext(), android.R.layout.simple_spinner_item, testNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int index, long l) {
                mSelectedTestIndex = index;
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        mRunTestButton = (Button) view.findViewById(R.id.runTestButton);
        mRunTestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTestEngine != null) {
                    if (mTestEngine.runTest(mTests.get(mSelectedTestIndex))) {
                        //mRunTestButton.setEnabled(false);
                    }
                }
            }
        });

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
