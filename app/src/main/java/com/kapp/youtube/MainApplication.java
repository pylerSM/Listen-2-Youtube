package com.kapp.youtube;

import android.app.Application;
import android.content.Context;

import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * Created by khang on 18/04/2016.
 * Email: khang.neon.1997@gmail.com
 */
public class MainApplication extends Application {
    private static final String TAG = "MainApplication";
    public static Context applicationContext;
    private static FirebaseAnalytics mFirebaseAnalytics;

    @Override
    public void onCreate() {
        super.onCreate();
        applicationContext = this;
        Settings.init(this);
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
    }

    public static FirebaseAnalytics getFirebaseAnalytics() {
        return mFirebaseAnalytics;
    }

}
