package com.kapp.youtube;

import android.os.Bundle;

/**
 * Created by khang on 24/03/2016.
 * Email: khang.neon.1997@gmail.com
 */
public class Constants {
    public static final String API_KEY = "AIzaSyB2w2PdpqSrTCQXZ0tiz3Boj7SIooNsD3Y";

    public static final String FIREBASE_SERVER = "https://analyze-usage.firebaseio.com/";


    public static final String SERVER1 = "http://murmuring-brushlands-18762.herokuapp.com",
            SERVER2 = "https://young-taiga-59434.herokuapp.com",
            SERVER3 = "https://polar-ridge-85715.herokuapp.com";

    private static final long DAY = 24 * 60 * 60 * 1000L,
            DAY_DIV_3 = DAY / 3;

    public static String getServer() {
        long time = System.currentTimeMillis() % DAY;
        Bundle bundle = new Bundle();
        String server;
        if (time < DAY_DIV_3) {
            server = SERVER1;
        } else if (time < DAY_DIV_3 * 2) {
            server = SERVER2;
        } else {
            server = SERVER3;
        }
        bundle.putString("server", server);
        bundle.putString("time", (time / (60 * 60 * 1000)) + "h");
        MainApplication.getFirebaseAnalytics().logEvent("Select Server", bundle);
        return server;
    }
}
