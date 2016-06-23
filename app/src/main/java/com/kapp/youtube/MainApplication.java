package com.kapp.youtube;

import android.app.Application;
import android.content.Context;

import net.hockeyapp.android.metrics.MetricsManager;

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
        MetricsManager.register(this, this, Constants.HOCKEY_APP_ID);
    }
}
