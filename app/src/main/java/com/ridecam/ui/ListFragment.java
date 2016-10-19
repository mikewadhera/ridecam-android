package com.ridecam.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;

import com.ridecam.R;
import com.ridecam.TripActivity;

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

        loadLayout();

        render();

        return mRootView;
    }

    public void loadLayout() {
        Button cameraButton = (Button)mRootView.findViewById(R.id.camera_button);
        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TripActivity tripActivity = (TripActivity)getActivity();
                tripActivity.focusCameraFragment();
            }
        });

        GridView gridview = (GridView) mRootView.findViewById(R.id.gridview);
        gridview.setAdapter(new ListAdapter(getActivity()));
    }

    public void render() {

    }

    public class ListAdapter extends BaseAdapter {
        private Context mContext;

        public ListAdapter(Context c) {
            mContext = c;
        }

        public int getCount() {
            return mThumbIds.length;
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            return 0;
        }

        // create a new ImageView for each item referenced by the Adapter
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (convertView == null) {
                // if it's not recycled, initialize some attributes
                view = inflater.inflate(R.layout.view_list_week, parent, false);
            } else {
                view = convertView;
            }

            //imageView.setImageResource(mThumbIds[position]);
            return view;
        }

        // references to our images
        private Integer[] mThumbIds = {
                R.drawable.common_google_signin_btn_icon_dark,
                R.drawable.common_google_signin_btn_icon_dark,
                R.drawable.common_google_signin_btn_icon_dark,
                R.drawable.common_google_signin_btn_icon_dark,
                R.drawable.common_google_signin_btn_icon_dark,
                R.drawable.common_google_signin_btn_icon_dark,
        };
    }
}
