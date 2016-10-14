package com.ridecam.geo;

public class Utils {

    // http://jonisalonen.com/2014/computing-distance-between-coordinates-can-be-simple-and-fast/
    // If the two points are near each other, for example in the same city, estimating the great
    // circle with a straight line in the latitude-longitude space will produce minimal error,
    // and be a lot faster to calculate. A minor complication is the fact that the length of a
    // degree of longitude depends on the latitude: a degree of longitude spans 111km on the
    // Equator, but half of that on 60° north. Adjusting for this is easy: multiply the
    // longitude by the cosine of the latitude. Then you can just take the Euclidean
    // distance between the two points, and multiply by the length of a degree:
    private static double DEGLEN = 110.25;
    public static double distance(double latA, double longA, double latB, double longB) {
        double x = latA - latB;
        double y = (longA - longB) * Math.cos(longA);
        return DEGLEN * Math.sqrt(x*x + y*y);
    }

}
