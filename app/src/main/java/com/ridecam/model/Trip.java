package com.ridecam.model;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ridecam.geo.Utils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
    private List<Coordinate> coordinates;
    private String videoUrl;

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

    public long getDistanceInMiles() {
        double total = 0;
        Coordinate lastCoordinate = null;
        for (Coordinate c : getCoordinates()) {
            double distance = 0;
            if (lastCoordinate != null) {
                distance = Utils.distance(c.latitude, c.longitude, lastCoordinate.latitude, lastCoordinate.longitude);
            }
            total += distance;
            lastCoordinate = c;
        }
        return Math.round(total);
    }
}
