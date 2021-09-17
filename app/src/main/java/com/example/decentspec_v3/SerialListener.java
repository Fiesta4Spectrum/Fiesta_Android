package com.example.decentspec_v3;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class SerialListener extends Service {

    // const
    private String STOP_ACTION = "STOP";
    private String START_ACTION = "START";
    private String TAG = "SerialListener";

    // notification related
    private Integer NOTI_ID = 1;
    private NotificationManager mNotificationMgr = null;
    private String CHANNEL_ID = "Serial_Channel";
    private String CHANNEL_NAME = "DecentSpec Serial Notification";
    private String CHANNEL_DESC = "no description";
    private String NOTI_TITLE = "DecentSpec Serial Listener";
    private String NOTI_TEXT = "Serial Listener is running in background ...";
    private String NOTI_TICKER = "Serial Listener is initiating ...";

    public SerialListener() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(STOP_ACTION)) {
            Log.d(TAG, "stop the service");
            stopForeground(true);
            stopSelf();
        } else {
            Log.d(TAG, "start the service");
            startForeground(NOTI_ID, genForegroundNotification());
            doTheWork();
        }
        return START_NOT_STICKY;
    }

    private void doTheWork() {

    }

    // utility methods
    private Notification genForegroundNotification() {
        // each foreground service need a notification
        // only support notification above android O;
        Intent tapIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, tapIntent, 0);

        if (mNotificationMgr == null) {
            mNotificationMgr = getSystemService(NotificationManager.class);
        }
        // create channel first
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(CHANNEL_DESC);
        mNotificationMgr.createNotificationChannel(channel);
        // create a notification in this channel
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.serial)
                .setContentTitle(NOTI_TITLE)
                .setContentText(NOTI_TEXT)
                .setTicker(NOTI_TICKER)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        return builder.build();
    }
}