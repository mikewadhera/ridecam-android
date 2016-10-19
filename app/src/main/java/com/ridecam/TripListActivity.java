package com.ridecam;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.ridecam.auth.AuthUtils;
import com.ridecam.auth.Installation;
import com.ridecam.db.DB;
import com.ridecam.model.Trip;

import java.util.List;

public class TripListActivity extends AppCompatActivity {

    public static final String TRIP_START_ID_EXTRA = "com.ridecam.TripListActivity.TRIP_START_ID_EXTRA";
    public static final String TRIP_END_ID_EXTRA = "com.ridecam.TripListActivity.TRIP_END_ID_EXTRA";
    public static final String TRIPS_TITLE_EXTRA = "com.ridecam.TripListActivity.TRIP_TITLE_EXTRA";

    public Trip[] mTrips;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();
        setTitle(intent.getStringExtra(TRIPS_TITLE_EXTRA));

        mTrips = new Trip[]{};

        final ListView listView = (ListView)findViewById(R.id.trip_list);

        DB.SimpleTripRangeQuery command = new DB.SimpleTripRangeQuery(
                AuthUtils.getUserId(this),
                intent.getStringExtra(TRIP_START_ID_EXTRA),
                intent.getStringExtra(TRIP_END_ID_EXTRA));
        command.runAsync(new DB.SimpleTripRangeQuery.ResultListener() {
            @Override
            public void onResult(List<Trip> trips) {
                mTrips = trips.toArray(new Trip[trips.size()]);
                final SimpleArrayAdapter adapter = new SimpleArrayAdapter(TripListActivity.this, mTrips);
                listView.setAdapter(adapter);
            }
        });
    }

    public class SimpleArrayAdapter extends ArrayAdapter<Trip> {
        private final Context context;
        private final Trip[] values;

        public SimpleArrayAdapter(Context context, Trip[] values) {
            super(context, -1, values);
            this.context = context;
            this.values = values;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View rowView;
            if (convertView == null) {
                rowView = inflater.inflate(R.layout.item_trip_list, parent, false);
            } else {
                rowView = convertView;
            }

            final TextView textView = (TextView) rowView.findViewById(R.id.firstLine);
            final TextView durationTextView = (TextView) rowView.findViewById(R.id.secondLine);
            Trip trip = values[position];
            textView.setText(trip.getName());
            durationTextView.setText(trip.getHumanDuration());

            return rowView;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int position) {
            return true;
        }
    }

}
