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

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.decentspec_v3.database.FileDatabaseMgr;
import com.example.decentspec_v3.database.SampleFile;
import com.example.decentspec_v3.federated_learning.FileAccessor;
import com.example.decentspec_v3.federated_learning.HTTPAccessor;
import com.example.decentspec_v3.federated_learning.HelperMethods;
import com.example.decentspec_v3.federated_learning.ScoreListener;
import com.example.decentspec_v3.federated_learning.TrainingPara;

import org.deeplearning4j.datasets.iterator.FloatsDataSetIterator;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.json.JSONException;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import org.nd4j.shade.jackson.core.JsonProcessingException;

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
    private Thread myDaemon;
    private TrainingTrigger mTrainingTrigger;

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
            changeState(FL_IDLE);
            startForeground(NOTI_ID, genForegroundNotification());
            doTheWork();
        }
        return START_NOT_STICKY;
    }

    private volatile boolean running = true;
    private void doTheWork() {
        /* find the cached data in local storage*/
        mTrainingTrigger = new TrainingTrigger();
        myDaemon = new Thread(new Runnable() {
            @Override
            public void run() {
                mTrainingTrigger.validateDatabase();
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        SampleFile dataFile = mTrainingTrigger.getDataset();
                        if (dataFile != null) {
                            /* use this file to make a local train */
                            oneLocalTraining(dataFile);
                            // end of one time training
                        }
                        Thread.sleep(ML_TASK_INTERVAL); // save time through check the env per interval
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
//                        Log.d(TAG, "interrupted during sleep");
                        cleanup();
                        break;
                    }
                }
                cleanup();
                // end due to external interrupt
                return;
            }

            // TODO add more fine interrupt check
            private void oneLocalTraining(SampleFile file) {

                // fetching knowledge
                changeState(FL_COMM);
                TrainingPara mTrainingPara = new TrainingPara();                        // ML context info from remote
                HTTPAccessor mHTTPAccessor = new HTTPAccessor(context);                 // http entity
                if (! mHTTPAccessor.fetchMinerList(mTrainingPara)) {                    // get minerlist
                    Log.d(TAG, "seed node no connection");
                    cleanup();
                    return;
                };
                for (int i = 0; i < mTrainingPara.MINER_LIST.size(); i++) {             // get ML context
                    if (mHTTPAccessor.getLatestGlobal(mTrainingPara.MINER_LIST.get(i), mTrainingPara))
                        break;
                    if (i == mTrainingPara.MINER_LIST.size() - 1) {
                        Log.d(TAG, "[fetchGlobal] miner node no connection");
                        cleanup();
                        return;
                    }
                }
                FileAccessor mFileAccessor = new FileAccessor(context);                 // io entity
                // TODO use the real file data
                List<Pair<float[], float[]>> localTrainList = mFileAccessor.readFrom(file.fileName, mTrainingPara);
                if (localTrainList == null || localTrainList.size() == 0) {
                    Log.d(TAG, "file not available");
                    cleanup();
                    return;
                }
                // create model
                DataSetIterator localDataset = new FloatsDataSetIterator(localTrainList, mTrainingPara.BATCH_SIZE);
                MultiLayerNetwork localModel = mTrainingPara.buildModel();
                localModel.init();

                // init with global weight
                for (String key : mTrainingPara.GLOBAL_WEIGHT.keySet()) {
                    localModel.setParam(key, mTrainingPara.GLOBAL_WEIGHT.get(key));
                }

                // training
                changeState(FL_TRAIN);
                double init_loss = 0.0;
                double end_loss = 0.0;

                mTrainingTrigger.markTraining();
                if (ENABLE_GC_FREQ_LIMIT)
                    Nd4j.getMemoryManager().setAutoGcWindow(5000);
                for (int i = 0; i < mTrainingPara.EPOCH_NUM; i++) {
                    try {
                        // check interrupt signal between epochs
                        if (Thread.currentThread().isInterrupted()) {
                            mTrainingTrigger.rollBack(); // roll back database
                            cleanup();
                            return; // early return due to interrupt
                        }

                        ScoreListener mySL = new ScoreListener(mTrainingPara);
                        localModel.setListeners(mySL);
                        localModel.fit(localDataset);
                        Log.d(TAG, "one epoch complete!");
                        if (i == 0)
                            init_loss = mySL.getScore(); // TODO this is not accurate, a score after one epoch training.
                        if (i == mTrainingPara.EPOCH_NUM - 1)
                            end_loss = mySL.getScore();
                        mTrainingTrigger.progressOn();
                    } catch (RuntimeException e) {
                        Thread.currentThread().interrupt();
                        mTrainingTrigger.rollBack(); // roll back database
                        cleanup();
                        return; // early return due to interrupt
                    }
                }

                // upload to miner
                try {
                    changeState(FL_COMM);
                    for (int i = 0; i < mTrainingPara.MINER_LIST.size(); i++) {
                        if (mHTTPAccessor.sendTrainedLocal(
                                mTrainingPara.MINER_LIST.get(i),
                                mTrainingPara.DATASET_SIZE,
                                init_loss - end_loss,
                                HelperMethods.paramTable2stateDict(localModel.paramTable())))
                            break;
                        if (i == mTrainingPara.MINER_LIST.size() - 1) {
                            Log.d(TAG, "[updateLocal] miner node no connection");
                            mTrainingTrigger.rollBack(); // roll back
                            cleanup();
                            return;
                        }
                    }

                } catch (JsonProcessingException | JSONException e) {
                    e.printStackTrace();
                }
                mTrainingTrigger.markTrained();
                cleanup();
                return;
            }

            private void cleanup() { // call when interrupted
                changeState(FL_IDLE);
                // seems no specific things need to do
            }

        });
        myDaemon.start();
    }
    private void stopTheWork() {
        if (myDaemon.isAlive()) {
            myDaemon.interrupt();
        }
        /* clean up here */
    }

    private class TrainingTrigger {

        private FileDatabaseMgr mDBMgr;
        private ConnectivityManager mConnMgr;
        private SampleFile curFile;

        public TrainingTrigger() {
            mDBMgr = new FileDatabaseMgr(context);
            mConnMgr = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        }
        public void validateDatabase() {
            List<SampleFile> allList = mDBMgr.getFileList(); // it is not in main thread so its fine
            for (SampleFile file : allList) {
                if (file.stage == STAGE_TRAINING) {
                    mDBMgr.markStage(file, STAGE_RECEIVED);
                    mDBMgr.progressRst(file);
                }
            }
        }
        public SampleFile getDataset() {
            if (! (myState == FL_IDLE))                 // first no other training going on
                return null;
            Log.d("trainingThread", "currently wifi: " + wifiReady() +
                                            ", charging: " + chargerReady());
            if (! (wifiReady() && chargerReady()))      // second stationary environment
                return null;
            curFile = null;                             // reset the file object
            return dataReady();                         // third, available database
        }
        private boolean wifiReady() {       // need to be under usable wifi
            Network curNetwork = mConnMgr.getActiveNetwork();
            NetworkCapabilities caps = mConnMgr.getNetworkCapabilities(curNetwork);
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }
        private boolean chargerReady() {    // need to be under charging
            Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;
        }
        private SampleFile dataReady() {        // need a usable training data
            List<SampleFile> allList = mDBMgr.getFileList(); // it is not in main thread so its fine
            for (SampleFile file : allList) {
                if (file.stage == STAGE_RECEIVED) {
                    curFile = file;
                    Log.d("trainingThread", "will use file: " + curFile.fileName);
                    return file;
                }
            }
            return null;
        }
        public void markTraining() {
            if (curFile == null) return;
            mDBMgr.markStage(curFile, STAGE_TRAINING);
        }
        public void markTrained() {
            if (curFile == null) return;
            mDBMgr.markStage(curFile, STAGE_TRAINED);
        }
        public void rollBack() {
            if (curFile == null) return;
            mDBMgr.markStage(curFile, STAGE_RECEIVED);
            mDBMgr.progressRst(curFile);
        }
        public void progressOn() {
            if (curFile == null) return;
            mDBMgr.progressOn(curFile);
        }
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