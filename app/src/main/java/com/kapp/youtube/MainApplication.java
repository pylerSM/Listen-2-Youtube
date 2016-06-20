package com.kapp.youtube;

import android.app.Application;
import android.content.Context;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.videolan.libvlc.util.JNILib;

/**
 * Created by khang on 18/04/2016.
 * Email: khang.neon.1997@gmail.com
 */
public class MainApplication extends Application {
    private static final String TAG = "MainApplication";
    public static Context applicationContext;

    @Override
    public void onCreate() {
        super.onCreate();
        applicationContext = this;
        JNILib.init(this);
        Settings.init(this);
    }

    public static FirebaseAnalytics getAnalytics() {
        return FirebaseAnalytics.getInstance(applicationContext);
    }
}
