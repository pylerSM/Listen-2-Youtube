package com.kapp.youtube.view.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.view.ContextThemeWrapper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.kapp.youtube.R;
import com.kapp.youtube.Settings;
import com.kapp.youtube.service.PlaybackService;

/**
 * Created by blackcat on 21/06/2016.
 */
public class ActivityControlPlayback extends Activity implements SeekBar.OnSeekBarChangeListener {
    public static final String IS_ONLINE = "isOnline";
    public static final String IS_PLAYING = "isPlaying";
    public static final String YOUTUBE_ID = "youtube_id";
    public static final String TITLE = "title";
    public static final String CURRENT_SECS = "current_secs";
    public static final String DURATION = "duration";
    public static final String UPDATE_CONTROL_ACTION = "com.kapp.youtube.update_playback_control";
    View repeat, stop, next;
    ImageView schuffleAndDown;
    FloatingActionButton togglePlayback;
    TextView titlePlayback, timing;
    SeekBar seekBar;
    private MaterialDialog dialog;
    private String youtubeId;
    private boolean isOnline, changePlayback = true;
    private boolean isPlaying;
    private String title;
    private long currentSecs;
    private long duration;

    boolean initialized = false;

    UpdateUIReceiver updateUIReceiver = new UpdateUIReceiver();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);

        dialog = new MaterialDialog.Builder(new ContextThemeWrapper(this, R.style.DialogControlPlaybackStyle))
                .customView(R.layout.controller, false)
                .cancelable(true)
                .cancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        finish();
                    }
                }).build();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                repeat = dialog.findViewById(R.id.ivRepeat);
                stop = dialog.findViewById(R.id.ivStop);
                togglePlayback = (FloatingActionButton) dialog.findViewById(R.id.fbToggle);
                next = dialog.findViewById(R.id.ivNext);
                schuffleAndDown = (ImageView) dialog.findViewById(R.id.ivSchuffleAndDownload);
                titlePlayback = (TextView) dialog.findViewById(R.id.tvTitle);
                timing = (TextView) dialog.findViewById(R.id.tvTime);
                seekBar = (SeekBar) dialog.findViewById(R.id.sbTime);

                seekBar.setOnSeekBarChangeListener(ActivityControlPlayback.this);

                repeat.setSelected(Settings.isRepeat());
                schuffleAndDown.setSelected(Settings.isShuffle());

                repeat.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        repeat.setSelected(Settings.toggleRepeat());
                    }
                });

                stop.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(getApplicationContext(), PlaybackService.class);
                        intent.setAction(PlaybackService.ACTION_STOP);
                        startService(intent);
                    }
                });

                next.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(getApplicationContext(), PlaybackService.class);
                        intent.setAction(PlaybackService.ACTION_SKIP);
                        startService(intent);
                    }
                });

                togglePlayback.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(getApplicationContext(), PlaybackService.class);
                        intent.setAction(PlaybackService.ACTION_TOGGLE_PLAYBACK);
                        startService(intent);
                        if (!isPlaying)
                            togglePlayback.setImageResource(R.drawable.ic_action_playback_pause);
                        else
                            togglePlayback.setImageResource(R.drawable.ic_action_playback_play);
                        togglePlayback.setEnabled(false);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                togglePlayback.setEnabled(true);
                            }
                        }, 1000);
                    }
                });

                schuffleAndDown.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (isOnline) {
                            Intent intent = new Intent(ActivityControlPlayback.this, DownloadOffline.class);
                            intent.setAction(DownloadOffline.DOWNLOAD_ACTION);
                            intent.putExtra(DownloadOffline.YOUTUBE_ID, youtubeId);
                            intent.putExtra(DownloadOffline.TITLE, title);
                            startActivity(intent);
                        } else {
                            schuffleAndDown.setSelected(Settings.toggleShuffle());
                        }
                    }
                });

                initialized = true;
                parseIntent(getIntent());
                updateUI();
            }
        });

        dialog.show();

        registerReceiver(updateUIReceiver, new IntentFilter(UPDATE_CONTROL_ACTION));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(updateUIReceiver);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (dialog != null && dialog.isShowing())
            try {
                dialog.dismiss();
            } catch (Exception ignore) {
            }
        finish();
    }

    private void parseIntent(Intent intent) {
        Bundle bundle = intent.getExtras();
        isOnline = bundle.getBoolean(IS_ONLINE);
        if (isOnline)
            youtubeId = bundle.getString(YOUTUBE_ID);
        isPlaying = bundle.getBoolean(IS_PLAYING);
        changePlayback = title == null || !title.equals(bundle.getString(TITLE));
        title = bundle.getString(TITLE);
        currentSecs = bundle.getLong(CURRENT_SECS, 0);
        duration = bundle.getLong(DURATION, 0);
    }

    boolean cacheIsPlaying = false;
    boolean enabledUpdateTiming = true;

    private void updateUI() {
        if (initialized) {
            if (cacheIsPlaying != isPlaying) {
                cacheIsPlaying = isPlaying;
                if (isPlaying)
                    togglePlayback.setImageResource(R.drawable.ic_action_playback_pause);
                else
                    togglePlayback.setImageResource(R.drawable.ic_action_playback_play);
            }
            if (changePlayback) {
                if (isOnline) {
                    schuffleAndDown.setImageResource(R.drawable.ic_file_download);
                } else {
                    schuffleAndDown.setImageResource(R.drawable.schuffle_iconb);
                    schuffleAndDown.setSelected(Settings.isShuffle());
                }
                titlePlayback.setText(title);
                seekBar.setMax((int) duration);
            }

            if (seekBar.getMax() != duration)
                seekBar.setMax((int) duration);

            if (enabledUpdateTiming) {
                timing.setText(String.format("%s / %s",
                        timeToText(currentSecs),
                        timeToText(duration)));
                seekBar.setProgress((int) currentSecs);
            }
        }
    }

    String timeToText(long timeInSecs) {
        int minutes = (int) (timeInSecs / 60);
        int secs = (int) (timeInSecs % 60);
        return (minutes < 10 ? "0" + minutes : minutes) + ":"
                + (secs < 10 ? "0" + secs : secs);
    }

    boolean hasChangeFromUser = false;

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            timing.setText(String.format("%s / %s",
                    timeToText(progress),
                    timeToText(duration)));
            hasChangeFromUser = true;
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        enabledUpdateTiming = false;
        hasChangeFromUser = false;
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (hasChangeFromUser) {
            Intent intent = new Intent(this, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_SEEK);
            intent.putExtra("position", (long) seekBar.getProgress());
            startService(intent);
        }
        enabledUpdateTiming = true;
    }

    class UpdateUIReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("TAG", "onReceive: ");
            parseIntent(intent);
            updateUI();
        }
    }
}
