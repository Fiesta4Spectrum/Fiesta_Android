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
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import static com.example.decentspec_v3.IntentDirectory.*;


public class FLManager extends Service {

    // const
    private String TAG = "FLManager";
    // service states
    public static final int FL_TRAIN = 0;
    public static final int FL_COMM = 1;
    public static final int FL_IDLE = 2;

    private static int myState = FL_IDLE;

    // notification related
    private Integer NOTI_ID = 2;
    private NotificationManager mNotificationMgr = null;
    private String CHANNEL_ID = "FL_Channel";
    private String CHANNEL_NAME = "DecentSpec FL Notification";
    private String CHANNEL_DESC = "no description";
    private String NOTI_TITLE = "DecentSpec FL Manager";
    private String NOTI_TEXT = "Federated Learning Manager is running in background ...";
    private String NOTI_TICKER = "Federated Learning Manager is initiating ...";

    public FLManager() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(STOP_ACTION)) {
            Log.d(TAG, "stop the service");
            changeState(FL_IDLE);
            stopForeground(true);
            stopTheWork();
            stopSelf();
        } else {
            Log.d(TAG, "start the service");
            changeState(FL_IDLE);
            startForeground(NOTI_ID, genForegroundNotification());
            doTheWork();
        }
        return START_NOT_STICKY;
    }

    private void doTheWork() {
        /* find the cached data in local storage*/
    }
    private void stopTheWork() {
        /* clean up here */
    }

    private void changeState(int state) {
        /*
        change the state of the service
        0 - training
        1 - communication
        2 - idle
         */
        myState = state;
        Intent intent = new Intent(FL_STATE_FILTER)
                .putExtra(STATE_FIELD, state);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
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
                .setSmallIcon(R.drawable.grain)
                .setContentTitle(NOTI_TITLE)
                .setContentText(NOTI_TEXT)
                .setTicker(NOTI_TICKER)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        return builder.build();
    }

    public static int getState() {
        return myState;
    }
}