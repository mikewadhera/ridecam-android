package com.ridecam;

import com.ridecam.fs.FSUtils;

public class Knobs {

    public static int PREVIEW_FPS = 24;

    public static int PREVIEW_WIDTH = 1280;

    public static int PREVIEW_HEIGHT = 720;

    public static int REC_BITRATE = 690000; // 690 kbit/sec

    public static int REC_FPS = 24;

    public static int REC_WIDTH = 1280;

    public static int REC_HEIGHT = 720;

    public static int MAX_REC_LENGTH_MS = 3600 * 1000 * 12; // 12 hours

    public static int GPS_MIN_TIME_CHANGE_MS = 30 * 1000; // 30 seconds

    public static int GPS_MIN_DISTANCE_CHANGE_M = 20; // 20 meters

    public static int getMaximumRecordingFileSizeBytes() {
        double factor = 0.9; // 90%
        return (int)Math.floor(factor * FSUtils.freeBytesAvailable(FSUtils.getVideoDirectory().getPath()));
    }

}
