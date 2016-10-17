package com.ridecam.fs;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public class FSUtils {

    private static final String TAG = "FSUtils";

    public static File getVideoDirectory(Context context) {
        String subFolder = "videos";
        File folder = new File(context.getFilesDir(), subFolder);
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                // TODO add logging
            }
        }
        return folder;
    }

    public static long freeBytesAvailable(String path) {
        StatFs stat = new StatFs(path);
        long bytesAvailable;
        if(Build.VERSION.SDK_INT >= 18){
            bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();
        }
        else{
            bytesAvailable = stat.getBlockSize() * stat.getAvailableBlocks();
        }
        return bytesAvailable;
    }

    public static File[] getVideoFilesOldestFirst(Context context) {
        File[] files = FSUtils.getVideoDirectory(context).listFiles();
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                if (f1.lastModified() > f2.lastModified()) {
                    return -1;
                } else if (f1.lastModified() < f2.lastModified()) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });
        return files;
    }

    public static String getBasename(File file) {
        return FilenameUtils.getBaseName(file.getAbsolutePath());
    }

    public static void deleteFile(File file) {
        file.delete();
    }

}
