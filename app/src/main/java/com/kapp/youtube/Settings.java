package com.kapp.youtube;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

import java.io.File;

/**
 * Created by khang on 28/03/2016.
 * Email: khang.neon.1997@gmail.com
 */
public class Settings {
    private static final String TAG = "Settings";
    public static final String IS_REPEAT = "IS_REPEAT";
    public static final String IS_SHUFFLE = "IS_SHUFFLE",
            INIT_DEFAULT_VALUE = "INIT_DEFAULT_VALUE",
            DOWNLOAD_FOLDER = "DOWNLOAD_FOLDER", DOWNLOAD_FOLDER_FOR_VIDEO = "DOWNLOAD_FOLDER_FOR_VIDEO",
            SEARCH_ONLY_MUSIC_VIDEO = "SEARCH_ONLY_MUSIC_VIDEO";
    public static final String IS_AUTO_PLAY = "IS_AUTO_PLAY";
    public static final String DEVICE_KEY = "DEVICE_KEY";
    public static final String FIRST_OPEN = "FIRST_OPEN_12";
    public static final String VOLUME = "volume_int";
    public static final String DOWNLOAD_CHOICE = "download_choice";

    private static Context context;

    public static void init(Context ctx) {
        context = ctx;
        boolean initDefValue = getSharedPreferences().getBoolean(INIT_DEFAULT_VALUE, true);
        if (initDefValue) {
            SharedPreferences.Editor editor = getSharedPreferences().edit();
            editor.putBoolean(INIT_DEFAULT_VALUE, false);
            editor.putString(DOWNLOAD_FOLDER,
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).toString());
            editor.putString(DOWNLOAD_FOLDER_FOR_VIDEO,
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).toString());
            editor.apply();
        }
    }

    private static SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }


    public static boolean isRepeat() {
        return getSharedPreferences().getBoolean(IS_REPEAT, true);
    }

    public static boolean isShuffle() {
        return getSharedPreferences().getBoolean(IS_SHUFFLE, false);
    }

    public static boolean toggleRepeat() {
        boolean isRepeat = !isRepeat();
        getSharedPreferences().edit().putBoolean(IS_REPEAT, isRepeat).apply();
        return isRepeat;
    }

    public static boolean toggleShuffle() {
        boolean isShuffle = !isShuffle();
        getSharedPreferences().edit().putBoolean(IS_SHUFFLE, isShuffle).apply();
        return isShuffle;
    }

    public static boolean isOnlyMusicCategory() {
        return getSharedPreferences().getBoolean(SEARCH_ONLY_MUSIC_VIDEO, true);
    }

    public static File getDownloadFolder() {
        String path = getSharedPreferences().getString(DOWNLOAD_FOLDER, null);
        File file = new File(path);
        if (!file.exists())
            file.mkdirs();
        return file;
    }

    public static File getDownloadFolderForVideo() {
        String path = getSharedPreferences().getString(DOWNLOAD_FOLDER_FOR_VIDEO, null);
        File file = new File(path);
        if (!file.exists())
            file.mkdirs();
        return file;
    }

    public static boolean isAutoPlay(){
        return getSharedPreferences().getBoolean(IS_AUTO_PLAY, true);
    }

    public static boolean isFirstOpen() {
        return getSharedPreferences().getBoolean(FIRST_OPEN, true);
    }

    public static void setFirstOpen(boolean v) {
        getSharedPreferences().edit().putBoolean(FIRST_OPEN, v)
                .apply();
    }

    public static void setVolume(int volume) {
        getSharedPreferences().edit()
                .putInt(VOLUME, volume)
                .apply();
    }

    public static int getVolume() {
        return getSharedPreferences().getInt(VOLUME, 80);
    }

    public static void set(String key, String value) {
        getSharedPreferences().edit()
                .putString(key, value)
                .apply();
    }

    public static String getDownloadChoice() {
        return getSharedPreferences().getString(DOWNLOAD_CHOICE, context.getString(R.string.default_download_choice));
    }
}
