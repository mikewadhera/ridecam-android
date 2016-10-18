package com.ridecam.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.ridecam.R;

public class ListFragment extends Fragment {

    View mRootView;

    public ListFragment() {
    }

    public static ListFragment factory() {
        ListFragment listFragment = new ListFragment();
        return listFragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_dashkam, container, false);

        render();

        return mRootView;
    }

    public void render() {

    }
}
