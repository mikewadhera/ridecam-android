package com.ridecam.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.ridecam.R;
import com.ridecam.TripListActivity;
import com.ridecam.auth.AuthUtils;
import com.ridecam.db.DB;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class WeekViewFragment extends Fragment {

    View mRootView;
    ListAdapter mListAdapter;

    public WeekViewFragment() {
    }

    public static WeekViewFragment factory() {
        WeekViewFragment weekViewFragment = new WeekViewFragment();
        return weekViewFragment;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mListAdapter != null) {
            render();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_weekview, container, false);

        mListAdapter = new ListAdapter(getActivity());

        addListeners();

        render();

        return mRootView;
    }

    public void addListeners() {
        GridView gridview = (GridView) mRootView.findViewById(R.id.gridview);
        gridview.setAdapter(mListAdapter);

        gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v,
                                    int position, long id) {
                Intent intent = new Intent(getActivity(), TripListActivity.class);

                if (position == 0) { // Starred
                    intent.putExtra(TripListActivity.TRIPS_IS_STARRED_EXTRA, true);
                } else {
                    DB.LoadWeeklyTrips.WeeklyTripSummary weeklyTripSummary = mListAdapter.getItem(position-1);
                    intent.putExtra(TripListActivity.TRIPS_TITLE_EXTRA, mListAdapter.getWeekTitle(position-1));
                    intent.putExtra(TripListActivity.TRIP_START_ID_EXTRA, weeklyTripSummary.tripIds.get(0));
                    intent.putExtra(TripListActivity.TRIP_END_ID_EXTRA, weeklyTripSummary.tripIds.get(weeklyTripSummary.tripIds.size()-1));
                }

                startActivity(intent);
            }
        });
    }

    public void render() {
        DB.LoadWeeklyTrips command = new DB.LoadWeeklyTrips(AuthUtils.getUserId(getActivity()));
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
            return mWeeklyTripSummaries.size()+1;
        }

        public DB.LoadWeeklyTrips.WeeklyTripSummary getItem(int position) {
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
                view = inflater.inflate(R.layout.item_week_list, parent, false);
            } else {
                view = convertView;
            }

            TextView weekTitleTextView = (TextView)view.findViewById(R.id.week_title);
            TextView milesTextView = (TextView)view.findViewById(R.id.miles_circle);

            if (position == 0) { // Starred
                weekTitleTextView.setText("");
                milesTextView.setAlpha(0);
                Drawable bg = new IconicsDrawable(getActivity()).icon(GoogleMaterial.Icon.gmd_folder_special).color(0xFFc8c4d1).sizeDp(120);
                view.setBackground(bg);
            } else {
                weekTitleTextView.setText(getWeekTitle(position-1));
                milesTextView.setAlpha(1);
                milesTextView.setText(String.valueOf(mWeeklyTripSummaries.get(position-1).miles));
                Drawable bg = new IconicsDrawable(getActivity()).icon(GoogleMaterial.Icon.gmd_folder).color(0x4bc8c4d1).sizeDp(120);
                view.setBackground(bg);
            }

            return view;
        }

        public void setWeeklySummaries(List<DB.LoadWeeklyTrips.WeeklyTripSummary> weeklySummaries) {
            mWeeklyTripSummaries = weeklySummaries;
        }

        public String getWeekTitle(int position) {
            DB.LoadWeeklyTrips.WeeklyTripSummary tripSummary = mWeeklyTripSummaries.get(position);

            SimpleDateFormat sdf = new SimpleDateFormat("MM/d");
            return "Week of " + sdf.format(tripSummary.week);
        }
    }
}
