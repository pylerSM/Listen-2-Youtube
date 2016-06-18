package com.kapp.youtube.view.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.kapp.youtube.service.PlaybackService;

import org.videolan.libvlc.util.JNILib;

/**
 * Created by khang on 04/05/2016.
 * Email: khang.neon.1997@gmail.com
 */
public class AudioPreviewActivity extends AppCompatActivity {
    private static final String TAG = "AudioPreviewActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!JNILib.checkJNILibs()) {
            Toast.makeText(AudioPreviewActivity.this,
                    "Media codec file not found, open App to download it.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }
        if (intent.getData() == null) {
            finish();
            return;
        }
        intent.setClass(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_PREVIEW);
        startService(intent);
        finish();
    }
}
