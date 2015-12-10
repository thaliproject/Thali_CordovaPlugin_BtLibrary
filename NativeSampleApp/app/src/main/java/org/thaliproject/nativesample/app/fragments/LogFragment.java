package org.thaliproject.nativesample.app.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.thaliproject.nativesample.app.R;

/**
 *
 */
public class LogFragment extends Fragment {
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
        TextView textView = (TextView)view.findViewById(R.id.section_label);
        textView.setText("Log");
        return view;
    }
}
