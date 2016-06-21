package com.kapp.youtube.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.kapp.youtube.R;

import java.util.Map;

/**
 * Created by blackcat on 21/06/2016.
 */
public class MessageReceiver extends FirebaseMessagingService {
    public static final String TYPE = "type";
    public static final String URL = "url";
    private static int sMessageNotificationId = 9999;
    private static final String TITLE = "title", MESSAGE = "message", TYPE_UPDATE = "update";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Map<String, String> data = remoteMessage.getData();
        if (TYPE_UPDATE.equalsIgnoreCase(data.get(TYPE))) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            Notification notification = new NotificationCompat.Builder(this)
                    .setAutoCancel(false)
                    .setSmallIcon(R.drawable.ic_update)
                    .setOngoing(true)
                    .setAutoCancel(true)
                    .setContentText(data.get(MESSAGE))
                    .setContentTitle(data.get(TITLE))
                    .build();
            Intent notificationIntent = new Intent(Intent.ACTION_VIEW);
            notificationIntent.setData(Uri.parse(data.get(URL)));
            notificationIntent.setFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
            );
            notification.contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            notificationManager.notify(++sMessageNotificationId, notification);
        }
    }
}
