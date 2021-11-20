package com.example.decentspec_v3;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import static com.example.decentspec_v3.Config.*;
import static com.example.decentspec_v3.IntentDirectory.*;


public class FLManagerService extends Service {

    // const
    private static final String TAG = "FLManager";
    // service states
    public static final int FL_TRAIN = 0;
    public static final int FL_COMM = 1;
    public static final int FL_IDLE = 2;

    private static int myState = FL_IDLE;

    // notification related
    private NotificationManager mNotificationMgr = null;
    private static final Integer NOTI_ID = 2;
    private static final String CHANNEL_ID = "FL_Channel";
    private static final String CHANNEL_NAME = "DecentSpec FL Notification";
    private static final String CHANNEL_DESC = "no description";
    private static final String NOTI_TITLE = "DecentSpec FL Manager";
    private static final String NOTI_TEXT = "FL Manager is running in background";
    private static final String NOTI_TICKER = "FL Manager is initiating ...";

    // spinner
    private Context context;
    private FLWorker FLWorker_1;
    private FLWorker FLWorker_2;

    public FLManagerService() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        this.context = this;
        GlobalPrefMgr.init(this);
        super.onCreate();
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
            changeState(FL_TRAIN);
            startForeground(NOTI_ID, genForegroundNotification());
            doTheWork();
        }
        return START_NOT_STICKY;
    }

    private void doTheWork() {
        /* find the cached data in local storage*/
        if (ENABLE_WORKER1) {
            FLWorker_1 = new FLWorker("worker_1", context, SEED_NODE_1, 1);
            FLWorker_1.start();
        }
        if (ENABLE_WORKER2) {
            FLWorker_2 = new FLWorker("worker_2", context, SEED_NODE_2, 2);
            FLWorker_2.start();
        }
    }
    private void stopTheWork() {
        if (FLWorker_1 != null && FLWorker_1.isAlive()) {
            FLWorker_1.interrupt();
        }
        if (FLWorker_2 != null && FLWorker_2.isAlive()) {
            FLWorker_2.interrupt();
        }
        changeState(FL_IDLE);
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
    private void appToast(String content) {
        Handler mainHandler = new Handler(getMainLooper());
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), content, Toast.LENGTH_SHORT).show();
            }
        });
    }

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