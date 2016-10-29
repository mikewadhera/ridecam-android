package com.ridecam.db;

import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ridecam.model.Trip;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static android.R.attr.id;

public abstract class DB {

    public static final String TAG = "DB";

    protected String mUserId;
    protected DatabaseReference mTripsRef;
    protected DatabaseReference mLocationsRef;

    public DB(String userId) {
        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();
        mUserId = userId;
        mTripsRef = rootRef.child("trips").child(mUserId);
        mLocationsRef = rootRef.child("locations").child(mUserId);
    }

    public Trip mapSimpleTrip(DataSnapshot dataSnapshot) {
        if (dataSnapshot == null || dataSnapshot.getKey() == null) {
            return null;
        }
        Trip trip = new Trip(dataSnapshot.getKey());
        Object ts = dataSnapshot.child("t_start").getValue();
        if (ts != null) trip.setStartTimestamp((long)ts);
        Object te = dataSnapshot.child("t_end").getValue();
        if (te != null) trip.setEndTimestamp((long)te);
        Object m = dataSnapshot.child("m").getValue();
        if (m != null) trip.setMiles((long)m);
        Object n = dataSnapshot.child("n").getValue();
        if (n != null) trip.setName((String)n);
        Object v = dataSnapshot.child("h264_video_url").getValue();
        if (v != null) trip.setVideoUrl((String)v);
        Object s = dataSnapshot.child("s").getValue();
        if (s != null) trip.setStarred((boolean)s);
        return trip;
    }

    public static class Save extends DB {

        private Trip mTrip;

        public Save(String userId, Trip trip) {
            super(userId);
            mTrip = trip;
        }

        public void run() {
            Map<String, Object> tripMap = new HashMap<>();
            tripMap.put("t_start", mTrip.getStartTimestamp());
            tripMap.put("t_end", mTrip.getEndTimestamp());
            tripMap.put("n", mTrip.getDefaultName());
            mTrip.calculateMiles();
            tripMap.put("m", mTrip.getMiles());
            if (mTrip.isStarred()) tripMap.put("s", true);

            mTripsRef.child(mTrip.getId()).setValue(tripMap);

            DatabaseReference locationRef = mLocationsRef.child(mTrip.getId());
            Map<String, Object> coordinateMap;
            for (Trip.Coordinate c : mTrip.getCoordinates()) {
                coordinateMap = new HashMap<>();
                coordinateMap.put("x", c.latitude);
                coordinateMap.put("y", c.longitude);
                coordinateMap.put("z", c.timestamp);
                coordinateMap.put("w", c.bearing);
                locationRef.push().setValue(coordinateMap);
            }
        }

    }

    public static class IsRecordingComplete extends DB {

        public interface ResultListener {
            public void onResult(boolean isRecordingComplete);
        }

        private String mId;

        public IsRecordingComplete(String userId, String id) {
            super(userId);
            mId = id;
        }

        public void runAsync(final ResultListener resultListener) {
            mTripsRef.child(mId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    boolean recordingComplete;
                    if (dataSnapshot == null) {
                        recordingComplete = false;
                    } else {
                        recordingComplete = dataSnapshot.hasChild("t_end");
                    }
                    resultListener.onResult(recordingComplete);
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {}
            });
        }

    }

    public static class SaveTripVideoDownloadURL extends DB {

        private String mId;
        private String mDownloadUrl;

        public SaveTripVideoDownloadURL(String userId, String id, String downloadUrl) {
            super(userId);
            mId = id;
            mDownloadUrl = downloadUrl;
        }

        public void run() {
            mTripsRef.child(mId).child("h264_video_url").setValue(mDownloadUrl);
        }

    }

    public static class LoadSimpleTrip extends DB {

        public interface ResultListener {
            public void onResult(Trip trip);
        }

        private String mId;

        public LoadSimpleTrip(String userId, String id) {
            super(userId);
            mId = id;
        }

        public void runAsync(final ResultListener resultListener) {
            mTripsRef.child(mId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    resultListener.onResult(mapSimpleTrip(dataSnapshot));
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {}
            });
        }

    }

    public static class UpdateTripName extends DB {

        private String mId;
        private String mName;

        public UpdateTripName(String userId, String id, String name) {
            super(userId);
            mId = id;
            mName = name;
        }

        public void run() {
            mTripsRef.child(mId).child("n").setValue(mName);
        }

    }

    public static class LoadWeeklyTrips extends DB {

        public class WeeklyTripSummary {
            public Date week;
            public long miles;
            public List<String> tripIds;

            public WeeklyTripSummary() {
                this.tripIds = new ArrayList<String>();
            }

        }

        public interface WeeklyTripsListener {
            void onResult(List<WeeklyTripSummary> result);
        }

        public LoadWeeklyTrips(String userId) {
            super(userId);
        }

        public void runAsync(final WeeklyTripsListener weeklyTripsListener) {

            DatabaseReference tripsRef = mTripsRef;

            tripsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    LinkedList<WeeklyTripSummary> result = new LinkedList<>();
                    WeeklyTripSummary lastSummary = null;
                    Date lastDate = null;
                    Date currentDate;
                    for (DataSnapshot tripDataSnapshot : dataSnapshot.getChildren()) {
                        if (tripDataSnapshot.child("t_end").getValue() == null) { // Skip in-progress
                            continue;
                        }
                        currentDate = new Date((long)tripDataSnapshot.child("t_start").getValue());
                        if (lastDate == null || !isSameWeek(lastDate, currentDate)) {
                            if (lastSummary != null) {
                                result.addFirst(lastSummary);
                            }
                            lastSummary = new WeeklyTripSummary();
                            lastSummary.week = firstDayOfWeek(currentDate);
                        }
                        Object miles = tripDataSnapshot.child("m").getValue();
                        if (miles != null) {
                            lastSummary.miles += (long)miles;
                        }
                        lastSummary.tripIds.add(tripDataSnapshot.getKey());
                        lastDate = currentDate;
                    }
                    if (lastSummary != null) {
                        result.addFirst(lastSummary);
                    }
                    weeklyTripsListener.onResult(result);
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {}
            });

        }

        private boolean isSameWeek(Date d1, Date d2) {
            Calendar calendar = Calendar.getInstance();
            calendar.setFirstDayOfWeek(Calendar.MONDAY);
            calendar.setTime(d1);
            int d1Week = calendar.get(Calendar.WEEK_OF_YEAR);
            calendar.setTime(d2);
            int d2Week = calendar.get(Calendar.WEEK_OF_YEAR);
            return d1Week == d2Week;
        }

        private Date firstDayOfWeek(Date d) {
            Calendar calendar = Calendar.getInstance();
            calendar.setFirstDayOfWeek(Calendar.MONDAY);
            calendar.setTime(d);
            int dWeek = calendar.get(Calendar.WEEK_OF_YEAR);;
            int dYear = calendar.get(Calendar.YEAR);
            calendar = Calendar.getInstance();
            calendar.clear();
            calendar.setFirstDayOfWeek(Calendar.MONDAY);
            calendar.set(Calendar.WEEK_OF_YEAR, dWeek);
            calendar.set(Calendar.YEAR, dYear);
            return calendar.getTime();
        }
    }

    public static class SimpleTripRangeQuery extends DB {

        public interface ResultListener {
            void onResult(List<Trip> trips);
        }

        private String mStartTripId;
        private String mEndTripId;

        public SimpleTripRangeQuery(String userId, String startTripId, String endTripId) {
            super(userId);
            mStartTripId = startTripId;
            mEndTripId = endTripId;
        }

        public void runAsync(final ResultListener resultListener) {
            DatabaseReference tripsRef = mTripsRef;

            tripsRef.orderByKey().startAt(mStartTripId).endAt(mEndTripId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    ArrayList<Trip> result = new ArrayList<>();
                    for (DataSnapshot tripDataSnapshot : dataSnapshot.getChildren()) {
                        result.add(mapSimpleTrip(tripDataSnapshot));
                    }
                    resultListener.onResult(result);
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {}
            });
        }
    }

}
