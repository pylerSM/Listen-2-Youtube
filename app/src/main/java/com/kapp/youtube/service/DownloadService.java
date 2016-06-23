package com.kapp.youtube.service;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.kapp.youtube.R;
import com.kapp.youtube.Settings;
import com.kapp.youtube.Utils;
import com.kapp.youtube.view.activity.DownloadManagerActivity;
import com.thin.downloadmanager.DefaultRetryPolicy;
import com.thin.downloadmanager.DownloadRequest;
import com.thin.downloadmanager.DownloadStatusListenerV1;
import com.thin.downloadmanager.ThinDownloadManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by khang on 25/04/2016.
 * Email: khang.neon.1997@gmail.com
 */
public class DownloadService extends Service {
    private static final String TAG = "DownloadService";

    public static final int TYPE_VIDEO = 0, TYPE_MUSIC = 1;

    public static final String ACTION_NEW_DOWNLOAD = "ACTION_NEW_DOWNLOAD";
    public static final String ACTION_REMOVE_DOWNLOAD = "ACTION_REMOVE_DOWNLOAD",
            ACTION_DO_NOTHING = "ACTION_DO_NOTHING";
    public static final int FOREGROUND_ID = Integer.MIN_VALUE;
    public static final String URL = "URL";
    public static final String FILE_NAME = "FILE_NAME";
    public static final String TITLE = "TITLE";
    public static final String ALBUM = "ALBUM";
    public static final String TYPE = "TYPE";

    NotificationManager notificationManager;
    ThinDownloadManager downloadManager;
    public List<DownloadInfo> queue;
    public int downloadingTaskId = -1;
    public DownloadInfo downloadingTaskInfo = null;
    private boolean startForeground = false;
    private NotificationCompat.Builder mBuilder;
    public int progress = 0;
    public long totalBytes = 0, downloadedBytes = 0;
    public DownloadListener listener;
    private WifiManager.WifiLock mWifiLock;
    private boolean grantedPermission = true;

    private Thread looper = new Thread(new Runnable() {
        private int progress = -1;

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(500);
                    if (this.progress != DownloadService.this.progress) {
                        if (listener != null)
                            listener.onCurrentDownloadChange();
                        updateNotificationProgress();
                        this.progress = DownloadService.this.progress;
                    }
                } catch (Exception e) {
                    return;
                }
            }
        }
    });
    private static int notificationId = 0;
    boolean binding = false;


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        binding = true;
        return new DownloadServiceBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        listener = null;
        binding = false;
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate - line 53: SERVICE");
        downloadManager = new ThinDownloadManager();
        queue = new ArrayList<>();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        looper.start();
        mWifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, "mylock");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy - line 121: ");
        looper.interrupt();
        downloadManager.release();
        if (mWifiLock.isHeld())
            mWifiLock.release();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null)
            processIntent(intent);
        return START_NOT_STICKY;
    }


    private void processIntent(Intent intent) {
        Log.d(TAG, "processIntent - line 69: SERVICE");
        if (intent.getAction().equals(ACTION_NEW_DOWNLOAD)) {
            String url = intent.getStringExtra(URL);
            Log.d(TAG, "processIntent - line 125: URL " + url);
            if (url == null)
                return;
            if (downloadingTaskInfo != null && url.equals(downloadingTaskInfo.url))
                return;  // duplicate
            for (DownloadInfo request : queue)
                if (url.equals(request.url))
                    return; // duplicate
            queue.add(new DownloadInfo(
                    url,
                    intent.getStringExtra(FILE_NAME),
                    intent.getStringExtra(TITLE),
                    intent.getStringExtra(ALBUM),
                    intent.getIntExtra(TYPE, TYPE_MUSIC)
            ));
            if (listener != null)
                listener.onQueueChange();
            processDownload();
        } else if (intent.getAction().equals(ACTION_REMOVE_DOWNLOAD)) {
            removeDownloadTask(intent.getStringExtra(URL));
        }

    }

    public void removeDownloadTask(String url) {
        if (downloadingTaskInfo != null && url.equals(downloadingTaskInfo.url)) {
            downloadManager.cancel(downloadingTaskId);
            downloadingTaskId = -1;
            downloadingTaskInfo = null;
        } else {
            for (int i = 0; i < queue.size(); i++)
                if (url.equals(queue.get(i).url)) {
                    queue.remove(i);
                    break;
                }
        }
        if (listener != null)
            listener.onQueueChange();
        processDownload();
    }

    private void processDownload() {
        Log.d(TAG, "processDownload - line 180: .");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            grantedPermission = Utils.checkPermissions(this);
        if (downloadingTaskId == -1 && queue.size() > 0) {
            Log.d(TAG, "processDownload - line 184: downloadingTaskId == -1 && queue.size() > 0");
            if (!startForeground)
                startForeground();
            mWifiLock.acquire();
            DownloadInfo downloadInfo = queue.get(0);
            queue.remove(0);
            if (listener != null)
                listener.onQueueChange();
            File downloadFolder = downloadInfo.type == TYPE_MUSIC ?
                    Settings.getDownloadFolder() : Settings.getDownloadFolderForVideo();
            File destinationFile = new File(downloadFolder, downloadInfo.fileName);
            DownloadProgressListener listener = new DownloadProgressListener();
            DownloadRequest request = new DownloadRequest(Uri.parse(downloadInfo.url))
                    .setDestinationURI(Uri.fromFile(destinationFile))
                    .setRetryPolicy(new DefaultRetryPolicy())
                    .setDeleteDestinationFileOnFailure(false)
                    .setPriority(DownloadRequest.Priority.HIGH)
                    .setDownloadContext(this)
                    .setStatusListener(listener);

            downloadingTaskInfo = downloadInfo;
            if (grantedPermission) {
                downloadingTaskId = downloadManager.add(request);
                listener.setDownloadId(downloadingTaskId);
                this.totalBytes = this.downloadedBytes = this.progress = 0;
                updateNotificationProgress();
            } else {
                createCompleteNotification(true, "Can't access storage, require granting permission.");
                processDownload();
            }
        } else if (queue.size() == 0) {
            if (startForeground)
                stopForeground(true);
            startForeground = false;
            notificationManager.cancel(FOREGROUND_ID);
            if (mWifiLock.isHeld())
                mWifiLock.release();
            Log.d(TAG, "run - line 219: stop download service");
            if (!binding)
                stopSelf();
        } else if (mBuilder != null) {
            mBuilder.setSubText(queue.size() + " task in queue");
            notificationManager.notify(FOREGROUND_ID, mBuilder.build());
        }
    }

    public void setListener(DownloadListener listener) {
        this.listener = listener;
    }

    public void startForeground() {
        startForeground = true;
        Intent intent = new Intent(getApplicationContext(), DownloadManagerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_action_download)
                .setOngoing(true)
                .setContentIntent(pi)
                .setContentTitle("Download server initiate...");
        startForeground(FOREGROUND_ID, mBuilder.build());
    }

    private void updateNotificationProgress() {
        if (downloadingTaskInfo == null || mBuilder == null)
            return;
        if (totalBytes == 0) {
            mBuilder.setProgress(100, 0, true);
        } else {
            mBuilder.setProgress(100, progress, false);
        }

        mBuilder.setContentTitle(downloadingTaskInfo.title)
                .setContentText("Received " + byteCountToString(downloadedBytes) + "/"
                        + byteCountToString(totalBytes) + ". Progress: " + progress + "%");
        mBuilder.setOngoing(true);
        if (queue.size() > 0)
            mBuilder.setSubText(queue.size() + " in queue");
        else
            mBuilder.setSubText(null);
        notificationManager.notify(FOREGROUND_ID, mBuilder.build());
    }

    public String byteCountToString(long bytes) {
        if (bytes / 1024 == 0)
            return bytes + " B";
        bytes /= 1024;
        if (bytes / 1024 == 0)
            return bytes + " KB";
        bytes /= 1024;
        return bytes + " MB";
    }

    private void createCompleteNotification(boolean error, String message) {
        if (downloadingTaskInfo == null)
            return;
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.ic_action_download)
                .setContentTitle(downloadingTaskInfo.title);
        if (error) {
            mBuilder.setContentText("Error: " + message);
            Utils.logEvent("download_error");
        }
        else {
            mBuilder.setContentText("Download completed");
            Utils.logEvent("download_success");
        }
        notificationManager.notify(++notificationId, mBuilder.build());
        if (!error) {
            File downloadFolder = downloadingTaskInfo.type == TYPE_MUSIC ?
                    Settings.getDownloadFolder() : Settings.getDownloadFolderForVideo();
            File destinationFile = new File(downloadFolder, downloadingTaskInfo.fileName);
            Uri uri = Uri.fromFile(destinationFile);
            long duration = Utils.getMediaDuration(uri);
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.Audio.AudioColumns.DATA, destinationFile.getAbsolutePath());
            contentValues.put(MediaStore.Audio.AudioColumns.TITLE, downloadingTaskInfo.title);
            contentValues.put(MediaStore.Audio.AudioColumns.DISPLAY_NAME, downloadingTaskInfo.title);
            contentValues.put(MediaStore.Audio.AudioColumns.DURATION, duration);
            contentValues.put(MediaStore.Audio.AudioColumns.ALBUM, downloadingTaskInfo.album);
            contentValues.put(MediaStore.Audio.AudioColumns.IS_MUSIC, 1);
            uri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues);
            Log.d(TAG, "createCompleteNotification - line 247: " + uri);
        }
    }

    public class DownloadServiceBinder extends Binder {
        public DownloadService getServiceInstance() {
            return DownloadService.this;
        }
    }

    public class DownloadInfo {
        public String url, fileName, title, album;
        public int type;

        public DownloadInfo(String url, String fileName, String title, String album, int type) {
            this.url = url;
            this.fileName = fileName;
            this.title = title;
            this.album = album;
            this.type = type;
        }
    }

    public class DownloadProgressListener implements DownloadStatusListenerV1 {
        int downloadId = Integer.MIN_VALUE;

        public void setDownloadId(int downloadId) {
            this.downloadId = downloadId;
        }

        @Override
        public void onDownloadComplete(DownloadRequest downloadRequest) {
            Log.d(TAG, "onDownloadComplete - line 343: ");
            if (downloadingTaskId == downloadId) {
                createCompleteNotification(false, null);
                downloadingTaskInfo = null;
                downloadingTaskId = -1;
                if (listener != null)
                    listener.onCurrentDownloadChange();
                processDownload();
            }
        }

        @Override
        public void onDownloadFailed(DownloadRequest downloadRequest, int errorCode, String errorMessage) {
            if (downloadingTaskId == downloadId) {
                createCompleteNotification(true, errorMessage);
                downloadingTaskInfo = null;
                downloadingTaskId = -1;
                if (listener != null)
                    listener.onCurrentDownloadChange();
                processDownload();
            }
        }

        @Override
        public void onProgress(DownloadRequest downloadRequest, long totalBytes, long downloadedBytes, int progress) {
            if (downloadingTaskId == downloadId) {
                if (downloadingTaskId == -1) return;
                DownloadService.this.totalBytes = totalBytes;
                DownloadService.this.downloadedBytes = downloadedBytes;
                DownloadService.this.progress = progress;
            }
        }
    }

    public interface DownloadListener {
        void onQueueChange();

        void onCurrentDownloadChange();
    }
}
