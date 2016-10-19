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
import android.widget.TextView;

import com.ridecam.R;
import com.ridecam.TripActivity;
import com.ridecam.db.DB;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class ListFragment extends Fragment {

    View mRootView;
    ListAdapter mListAdapter;

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

        mListAdapter = new ListAdapter(getActivity());

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
        gridview.setAdapter(mListAdapter);
    }

    public void render() {
        DB.LoadWeeklyTrips command = new DB.LoadWeeklyTrips();
        command.runAsync(new DB.LoadWeeklyTrips.WeeklyTripsListener() {
            @Override
            public void onResult(List<DB.LoadWeeklyTrips.WeeklyTripSummary> result) {
                mListAdapter.setWeeklySummaries(result);
                mListAdapter.notifyDataSetChanged();
            }
        });
    }

    public class ListAdapter extends BaseAdapter {
        private Context mContext;
        private List<DB.LoadWeeklyTrips.WeeklyTripSummary> mWeeklyTripSummaries;

        public ListAdapter(Context c) {
            mContext = c;
            mWeeklyTripSummaries = new ArrayList<>();
        }

        public int getCount() {
            return mWeeklyTripSummaries.size();
        }

        public Object getItem(int position) {
            return mWeeklyTripSummaries.get(position);
        }

        public long getItemId(int position) {
            return 0;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (convertView == null) {
                // if it's not recycled, initialize some attributes
                view = inflater.inflate(R.layout.view_list_week, parent, false);
            } else {
                view = convertView;
            }

            TextView weekTitleTextView = (TextView)view.findViewById(R.id.week_title);
            DB.LoadWeeklyTrips.WeeklyTripSummary tripSummary = mWeeklyTripSummaries.get(position);
            SimpleDateFormat sdf = new SimpleDateFormat("MM/d");
            String weekTitle = "Week of " + sdf.format(tripSummary.week);
            weekTitleTextView.setText(weekTitle);

            TextView milesTextView = (TextView)view.findViewById(R.id.miles_circle);
            milesTextView.setText(String.valueOf(tripSummary.miles));

            return view;
        }

        public void setWeeklySummaries(List<DB.LoadWeeklyTrips.WeeklyTripSummary> weeklySummaries) {
            mWeeklyTripSummaries = weeklySummaries;
        }
    }
}
