package com.kapp.youtube.mediaplayer;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.kapp.youtube.presenter.GetLink;
import com.kapp.youtube.presenter.IPresenterCallback;

import org.json.JSONException;
import org.json.JSONObject;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;


/**
 * Created by khang on 29/04/2016.
 * Email: khang.neon.1997@gmail.com
 */
public class MyMediaPlayer implements MediaPlayer.EventListener, IPresenterCallback {
    private static final String TAG = "MyMediaPlayer";
    private static MediaPlayer sMediaPlayer;

    public static LibVLC sLibVLC;
    private Context mContext;

    static LibVLC getLibVLC() {
        if (sLibVLC == null) {
            ArrayList<String> options = new ArrayList<>();
            options.add("--http-reconnect");
            options.add("--network-caching=6000");
            options.add("--no-video");
            sLibVLC = new LibVLC(options);
        }
        return sLibVLC;
    }

    private PlaybackStatus status = PlaybackStatus.STOPPED;
    private PlaybackListener listener;
    private int flag = 0;


    public MyMediaPlayer(Context mContext) {
        sMediaPlayer = new MediaPlayer(getLibVLC());
        sMediaPlayer.setEventListener(this);
        this.mContext = mContext;
    }

    public void prepareWithUri(Uri uri) {
        Log.d(TAG, "prepareWithUri - line 40: " + uri);
        flag++;
        if (status != PlaybackStatus.PREPARING)
            setStatus(PlaybackStatus.PREPARING);
        sMediaPlayer.stop();
        if (sMediaPlayer.getMedia() == null || !uri.equals(sMediaPlayer.getMedia().getUri())) {
            Media media = new Media(getLibVLC(), uri);
            sMediaPlayer.setMedia(media);
        } else
            Log.d(TAG, "prepareWithUri - line 57: REUSE current ");
        sMediaPlayer.play();
        setStatus(PlaybackStatus.PLAYING);
    }

    private void prepareNoPlay(Uri uri) {
        sMediaPlayer.stop();
        if (sMediaPlayer.getMedia() == null || !uri.equals(sMediaPlayer.getMedia().getUri())) {
            Media media = new Media(getLibVLC(), uri);
            sMediaPlayer.setMedia(media);
            sMediaPlayer.pause();
        } else
            Log.d(TAG, "prepareWithUri - line 57: REUSE current ");
    }

    public void prepareWithYoutubeId(String youtubeId) {
        flag++;
        setStatus(PlaybackStatus.PREPARING);
        sMediaPlayer.stop();
        new GetLink(flag, this).execute(youtubeId);
    }

    public void play() {
        Log.d(TAG, "play - line 54: PLAY");
        if (!isPlaying() && sMediaPlayer.getMedia() != null) {
            setStatus(PlaybackStatus.PLAYING);
            sMediaPlayer.play();
        }
    }

    public void pause() {
        if (isPlaying()) {
            setStatus(PlaybackStatus.PAUSED);
            sMediaPlayer.pause();
        }
    }

    public void stop() {
        flag++;
        if (!isStopped()) {
            setStatus(PlaybackStatus.STOPPED);
            sMediaPlayer.stop();
        }
    }

    public void release() {
        flag++;
        sMediaPlayer.release();
        sMediaPlayer = null;
    }

    public void seek(long toPos) {
        sMediaPlayer.setPosition(toPos);
        sMediaPlayer.setTime(toPos);
    }

    public boolean canSeek() {
        return sMediaPlayer.isSeekable();
    }

    public long getDuration() {
        return sMediaPlayer.getLength();
    }

    public long getPosition() {
        return sMediaPlayer.getTime();
    }

    public void setListener(PlaybackListener listener) {
        this.listener = listener;
    }

    public void setStatus(PlaybackStatus status) {
        this.status = status;
        if (listener != null)
            switch (status) {
                case PREPARING:
                    Log.d(TAG, "setStatus - line 50: PREPARING");
                    listener.onPrepare();
                    break;
                case PLAYING:
                    Log.d(TAG, "setStatus - line 54: PLAYING");
                    listener.onPlayed();
                    break;
                case PAUSED:
                    listener.onPaused();
                    Log.d(TAG, "setStatus - line 59: PAUSED");
                    break;
                case STOPPED:
                    Log.d(TAG, "setStatus - line 62: STOPPED");
                    listener.onStopped();
                    break;
                case ERROR:
                    Log.d(TAG, "setStatus - line 123: ERROR");
                    listener.onError();
                    break;
                case FINISHED:
                    Log.d(TAG, "setStatus - line 127: FINISHED");
                    listener.onFinished();
                    break;
            }
    }

    public boolean isPlaying() {
        return status == PlaybackStatus.PLAYING || status == PlaybackStatus.PREPARING;
    }

    public boolean isPaused() {
        return status == PlaybackStatus.PAUSED;
    }

    public boolean isStopped() {
        return status == PlaybackStatus.STOPPED;
    }

    public boolean isError() {
        return status == PlaybackStatus.ERROR;
    }

    public boolean isFinished() {
        return status == PlaybackStatus.FINISHED;
    }

    public void setVolume(int volume) {
        sMediaPlayer.setVolume(volume);
    }

    @Override
    public void onEvent(MediaPlayer.Event event) {
        switch (event.type) {
            case MediaPlayer.Event.EncounteredError:
                Log.d(TAG, "On Error, Stop media player" + event);
                setStatus(PlaybackStatus.ERROR);
                break;
            case MediaPlayer.Event.EndReached:
                Log.d(TAG, "onEvent - line 153: EndReached");
                Log.d(TAG, getPosition() + "/" + getDuration());
                //final long currentPos = getPosition();
                setStatus(PlaybackStatus.FINISHED);
                break;
            case MediaPlayer.Event.TimeChanged:
                if (listener != null)
                    listener.onPositionChanged(getDuration(), getPosition());
                break;
        }
    }

    @Override
    public void onFinish(int jobId, Object result) {
        if (jobId == this.flag && result != null) {
            JSONObject jsonObject = (JSONObject) result;
            String url = null;
            try {
                url = jsonObject.getString("url");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (url != null) {
                if (status == PlaybackStatus.PREPARING)
                    prepareWithUri(Uri.parse(url));
                else if (status == PlaybackStatus.PAUSED) {
                    prepareNoPlay(Uri.parse(url));
                    Log.d(TAG, "onFinish - line 208: prepare No play");
                }

            } else {
                Toast.makeText(mContext, "Get sound stream error by some reasons:\n" +
                        "- Your network connectivity\n" +
                        "- Video not available for US", Toast.LENGTH_LONG).show();
                stop();
            }
        }
    }

    public enum PlaybackStatus {
        PREPARING,
        PLAYING,
        PAUSED,
        ERROR,
        FINISHED,
        STOPPED
    }

    public interface PlaybackListener {
        void onPrepare();

        void onPlayed();

        void onPaused();

        void onStopped();

        void onError();

        void onFinished();

        void onPositionChanged(long duration, long current);
    }


}
