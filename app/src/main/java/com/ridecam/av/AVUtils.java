package com.ridecam.av;

public class AVUtils {

    public static int estimateVideoDurationHours(int bitrate, long sizeInBytes) {
        float sizeInBits = sizeInBytes * 8;
        double fractionalHours = (sizeInBits/bitrate)/3600;
        return (int)Math.floor(fractionalHours);
    }

}
