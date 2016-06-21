package com.kapp.youtube.view.activity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import com.kapp.youtube.Constants;
import com.kapp.youtube.Settings;
import com.kapp.youtube.Utils;
import com.kapp.youtube.model.YoutubeData;
import com.kapp.youtube.presenter.FetchRelatedVideo;
import com.kapp.youtube.presenter.GetVideoTitle;
import com.kapp.youtube.presenter.IPresenterCallback;
import com.kapp.youtube.service.PlaybackService;

import org.videolan.libvlc.util.JNILib;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by blackcat on 19/06/2016.
 */
public class ListenInBackground extends Activity {
    private static final String TAG = "ListenInBackground";
    private ServiceConnection serviceConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        if (!JNILib.checkJNILibs()) {
            Toast.makeText(ListenInBackground.this, "Please open app and download media library first!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        final Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_DO_NOTHING);
        startService(intent);

        String action = getIntent().getAction();
        if (action != null) {
            //setContentView(R.layout.waiting_layout);
            String youtubeId = null;
            String title = null;
            if (action.equals(Intent.ACTION_SEND)) {
                Uri uri = Uri.parse(getIntent().getStringExtra(Intent.EXTRA_TEXT));
                youtubeId = uri.getLastPathSegment();
                title = getIntent().getStringExtra(Intent.EXTRA_TEXT);
                Utils.logEvent("web_browser_intent");
            } else if (getIntent().getData() != null) { /* Intent.ACTION_VIEW */
                youtubeId = getIntent().getData().getQueryParameter("v");
                title = getIntent().getData().toString();
                Utils.logEvent("youtube_app_intent");
            }
            if (youtubeId != null && youtubeId.length() == Constants.YOUTUBE_ID_LENGTH) {
                Utils.logEvent("listen_in_background");
                final String finalYoutubeId = youtubeId;
                final String finalTitle = title;
                new GetVideoTitle(0, new IPresenterCallback() {
                    @Override
                    public void onFinish(int jobType, Object result) {
                        final YoutubeData youtubeData = new YoutubeData(
                                finalYoutubeId,
                                result == null ? finalTitle : result.toString(),
                                "YouTube",
                                null);
                        serviceConnection = new ServiceConnection() {
                            @Override
                            public void onServiceConnected(ComponentName name, IBinder service) {
                                final PlaybackService playbackService = ((PlaybackService.MBinder) service).getInstance();
                                List<YoutubeData> youtubeDatas = new ArrayList<>();
                                youtubeDatas.add(youtubeData);
                                playbackService.playYoutubeList(youtubeDatas, true);
                                if (Settings.isAutoPlay())
                                    new FetchRelatedVideo(0, new IPresenterCallback() {
                                        @Override
                                        public void onFinish(int jobType, Object result) {
                                            try {
                                                List<YoutubeData> datas = (List<YoutubeData>) result;
                                                if (datas.get(0).id.equals(youtubeData.id))
                                                    playbackService.playYoutubeList(datas, false);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }).execute(youtubeData);
                                finish();
                            }

                            @Override
                            public void onServiceDisconnected(ComponentName name) {
                                serviceConnection = null;
                            }
                        };
                        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
                    }
                }).execute(youtubeId);
                return;
            }
        }
        Toast.makeText(ListenInBackground.this, "Can not parse youtube url.", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceConnection != null)
            unbindService(serviceConnection);
    }

}
