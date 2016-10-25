package com.ridecam.model;

import android.content.Context;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.ridecam.fs.FSUtils;
import com.ridecam.geo.Utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class Trip {

    public static class Coordinate {
        public double latitude;
        public double longitude;
        public long timestamp;
        public float bearing;
        public String title;
    }

    private String id;
    private long startTimestamp;
    private long endTimestamp;
    private long miles;
    private List<Coordinate> coordinates;
    private String videoUrl;
    private String name;

    public static String allocateId() {
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        return db.child("trips").push().getKey();
    }

    public Trip(String id) {
        this.id = id;
        this.coordinates = new LinkedList<Coordinate>();
    }

    public String getId() {
        return id;
    }

    public void addCoordinate(Coordinate coordinate) {
        this.coordinates.add(coordinate);
    }

    public List<Coordinate> getCoordinates() { return this.coordinates; }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public long getEndTimestamp() {
        return endTimestamp;
    }

    public void setEndTimestamp(long endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public void setMiles(long miles) {
        this.miles = miles;
    }

    public long getMiles() {
        return this.miles;
    }

    public void calculateMiles() {
        float totalMeters = 0;
        Coordinate lastCoordinate = null;
        for (Coordinate c : getCoordinates()) {
            float distanceMeters = 0;
            if (lastCoordinate != null) {
                distanceMeters = Utils.distanceInMeters(c.latitude, c.longitude, lastCoordinate.latitude, lastCoordinate.longitude);
            }
            totalMeters += distanceMeters;
            lastCoordinate = c;
        }
        this.miles = Utils.metersToMiles(totalMeters);
    }

    public String getDefaultName() {
        DateFormat df = new SimpleDateFormat("MM/d EEEE");
        Date date = new Date(this.startTimestamp);
        String baseName = df.format(date);
        if ((this.endTimestamp - this.startTimestamp) < 5 * 3600 * 1000) { // 5 hrs
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            String suffix = "";
            if (hour < 12) {
                suffix = "morning";
            } else if (hour < 17) {
                suffix = "afternoon";
            } else {
                suffix = "evening";
            }
            baseName += " " + suffix;
        }
        return baseName;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHumanDuration() {
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a");
        return sdf.format(new Date(startTimestamp)) + " - " + sdf.format(new Date(endTimestamp));

    }

    public String getLocalOrRemoteVideoUrl(Context context) {
        if (this.videoUrl != null) {
            return this.videoUrl;
        } else {
            return FSUtils.getVideoFile(context, this.id).getAbsolutePath();
        }
    }
}
