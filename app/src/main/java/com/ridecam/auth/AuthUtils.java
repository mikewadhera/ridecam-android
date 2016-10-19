package com.ridecam.auth;

import android.content.Context;

public class AuthUtils {

    public static String getUserId(Context context) {
        return Installation.id(context);
    }

}
