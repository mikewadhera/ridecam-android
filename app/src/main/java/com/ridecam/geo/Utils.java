package com.ridecam.geo;

import android.location.Location;

public class Utils {

    public static float distanceInMeters(double latA, double longA, double latB, double longB) {
        float[] results = new float[1];
        float distanceMeters = 0;
        Location.distanceBetween(latA, longA, latB, longB, results);
        distanceMeters = results[0];
        return distanceMeters;
    }

    public static long metersToMiles(float meters) {
        return Math.round(meters * 0.00062137);
    }

}
