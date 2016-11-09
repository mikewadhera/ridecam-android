package com.ridecam;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.LayoutInflaterCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.mikepenz.iconics.context.IconicsLayoutInflater;
import com.ridecam.auth.AuthUtils;
import com.ridecam.db.DB;
import com.ridecam.model.Trip;

import java.util.List;

public class TripListActivity extends AppCompatActivity {

    public static final String TRIP_START_ID_EXTRA = "com.ridecam.TripListActivity.TRIP_START_ID_EXTRA";
    public static final String TRIP_END_ID_EXTRA = "com.ridecam.TripListActivity.TRIP_END_ID_EXTRA";
    public static final String TRIPS_TITLE_EXTRA = "com.ridecam.TripListActivity.TRIP_TITLE_EXTRA";
    public static final String TRIPS_IS_STARRED_EXTRA = "com.ridecam.TripListActivity.TRIPS_IS_STARRED_EXTRA";

    public Trip[] mTrips;
    private ListView mListView;
    private boolean mStarred;
    private String mStartId;
    private String mEndId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LayoutInflaterCompat.setFactory(getLayoutInflater(), new IconicsLayoutInflater(getDelegate()));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mTrips = new Trip[]{};

        mListView = (ListView)findViewById(R.id.trip_list);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Trip trip = mTrips[mTrips.length-i-1];
                Intent intent = new Intent(TripListActivity.this, PlayerActivity.class);
                intent.putExtra(PlayerActivity.TRIP_ID_EXTRA, trip.getId());
                intent.putExtra(PlayerActivity.TRIP_VIDEO_URL_EXTRA, trip.getLocalOrRemoteVideoUrl(TripListActivity.this));
                startActivity(intent);
            }
        });

        Intent intent = getIntent();
        mStarred = intent.getBooleanExtra(TRIPS_IS_STARRED_EXTRA, false);
        mStartId = intent.getStringExtra(TRIP_START_ID_EXTRA);
        mEndId = intent.getStringExtra(TRIP_END_ID_EXTRA);

        if (mStarred) {
            setTitle("Starred");
        } else {
            setTitle(intent.getStringExtra(TRIPS_TITLE_EXTRA));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mStarred) {
            DB.SimpleTripRangeQuery command = new DB.SimpleTripRangeQuery(
                    AuthUtils.getUserId(this),
                    mStartId,
                    mEndId);
            command.runAsync(new DB.SimpleTripRangeQuery.ResultListener() {
                @Override
                public void onResult(List<Trip> trips) {
                    mTrips = trips.toArray(new Trip[trips.size()]);
                    SimpleArrayAdapter adapter = new SimpleArrayAdapter(TripListActivity.this, mTrips);
                    mListView.setAdapter(adapter);
                    }
            });
        }
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
            final TextView milesView = (TextView) rowView.findViewById(R.id.miles_circle);
            Trip trip = values[values.length-position-1];
            textView.setText(trip.getName());
            durationTextView.setText(trip.getHumanDuration());
            milesView.setText(String.valueOf(trip.getMiles()));

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
