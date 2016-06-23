package com.kapp.youtube.view.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import com.kapp.youtube.MainApplication;
import com.kapp.youtube.R;
import com.kapp.youtube.Settings;
import com.kapp.youtube.service.PlaybackService;

/**
 * Created by khang on 30/04/2016.
 * Email: khang.neon.1997@gmail.com
 */
public class SettingsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "SettingsFragment";

    private void bindPreferenceSummaryToValue(Preference preference) {
        preference.setOnPreferenceChangeListener(this);
        onPreferenceChange(preference,
                PreferenceManager.getDefaultSharedPreferences(MainApplication.applicationContext)
                        .getString(preference.getKey(), ""));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_general);
        bindPreferenceSummaryToValue(findPreference(Settings.DOWNLOAD_FOLDER));
        bindPreferenceSummaryToValue(findPreference(Settings.DOWNLOAD_FOLDER_FOR_VIDEO));
        bindPreferenceSummaryToValue(findPreference("volume"));
        bindPreferenceSummaryToValue(findPreference("download_choice"));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.getKey().equals("volume")) {
            String v = (String) newValue;
            int volumeLevel = v.length() == 0 ? 0 : Integer.parseInt((String) newValue);
            if (volumeLevel > 100)
                volumeLevel = 100;
            Settings.set("volume", "" + volumeLevel);
            Settings.setVolume(volumeLevel);
            Intent intent = new Intent(getActivity(), PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_CHANGE_VOLUME);
            getActivity().startService(intent);
            preference.setSummary(volumeLevel + "");
        } else
            preference.setSummary((String) newValue);
        return true;
    }
}
