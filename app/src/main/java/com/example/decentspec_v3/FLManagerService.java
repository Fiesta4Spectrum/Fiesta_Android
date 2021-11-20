package com.example.decentspec_v3;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.decentspec_v3.database.FileDatabaseMgr;
import com.example.decentspec_v3.database.SampleFile;
import com.example.decentspec_v3.federated_learning.FileAccessor;
import com.example.decentspec_v3.federated_learning.HTTPAccessor;
import com.example.decentspec_v3.federated_learning.HelperMethods;
import com.example.decentspec_v3.federated_learning.ScoreListener;
import com.example.decentspec_v3.federated_learning.TrainingPara;

import org.deeplearning4j.datasets.iterator.AbstractDataSetIterator;
import org.deeplearning4j.datasets.iterator.DoublesDataSetIterator;
import org.deeplearning4j.datasets.iterator.FloatsDataSetIterator;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.json.JSONException;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import org.nd4j.shade.jackson.core.JsonProcessingException;

import java.util.Collections;
import java.util.List;

import static com.example.decentspec_v3.Config.*;
import static com.example.decentspec_v3.IntentDirectory.*;
import static com.example.decentspec_v3.database.SampleFile.STAGE_RECEIVED;
import static com.example.decentspec_v3.database.SampleFile.STAGE_TRAINED;
import static com.example.decentspec_v3.database.SampleFile.STAGE_TRAINING;


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
    private FLWorker TV_thread;
    private FLWorker LTE_thread;

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
        if (ENABLE_TV) {
            TV_thread = new FLWorker("tv_training", context, SEED_NODE_TV);
            TV_thread.start();
        }
        if (ENABLE_LTE) {
            LTE_thread = new FLWorker("lte_training", context, SEED_NODE_LTE);
            LTE_thread.start();
        }
    }
    private void stopTheWork() {
        if (TV_thread != null && TV_thread.isAlive()) {
            TV_thread.interrupt();
        }
        if (LTE_thread != null && LTE_thread.isAlive()) {
            LTE_thread.interrupt();
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