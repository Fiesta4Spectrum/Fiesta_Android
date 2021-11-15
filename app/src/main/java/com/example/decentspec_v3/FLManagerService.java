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
    private HTTPAccessor mHTTPAccessor;                 // http entity

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

    private void doTheWork() {
        /* find the cached data in local storage*/
        mTrainingTrigger = new TrainingTrigger();
        mHTTPAccessor = new HTTPAccessor(context);
        myDaemon = new Thread(new Runnable() {
            @Override
            public void run() {
                mTrainingTrigger.flushDatabase();
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        // REMOTE ready: new global available
                        TrainingPara mTPara = mTrainingTrigger.getNewGlobal();
                        // LOCAL ready: wifi, battery, and local files
                        SampleFile dataFile = mTrainingTrigger.getDataset(mTPara); // TODO change it to file list instead fo a single file
                        if (dataFile != null && mTPara != null) {
                            /* use this file to make a local train */
                            oneLocalTraining(dataFile, mTPara);
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
            }

            // TODO add more fine interrupt check
            private void oneLocalTraining(SampleFile file, TrainingPara mTrainingPara) {

                // start of ML pipeline ============================================================
                changeState(FL_TRAIN);
                // ***** read train dataset *****
                FileAccessor mFileAccessor = new FileAccessor(context);                 // io entity
                // TODO use the real file data
                List<Pair<double[], double[]>> localTrainList = mFileAccessor.readFrom(file.fileName, mTrainingPara);
                if (localTrainList == null || localTrainList.size() == 0) {
                    Log.d(TAG, "file not available");
                    cleanup();
                    return;
                }
                // ***** create model *****
                DataSetIterator localDataset = new DoublesDataSetIterator(localTrainList, mTrainingPara.BATCH_SIZE);
                MultiLayerNetwork localModel = mTrainingPara.buildModel();
                localModel.init();
                // ***** init with global weight *****
                for (String key : mTrainingPara.GLOBAL_WEIGHT.keySet()) {
                    localModel.setParam(key, mTrainingPara.GLOBAL_WEIGHT.get(key));
                }
                // ***** training *****
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
                // ***** upload to miner *****
                try {
                    changeState(FL_COMM);
                    for (int i = 0; i < mTrainingPara.MINER_LIST.size(); i++) {
                        if (mHTTPAccessor.sendTrainedLocal(
                                mTrainingPara.MINER_LIST.get(i),
                                mTrainingPara.DATASET_SIZE,
                                init_loss - end_loss,
                                mTrainingPara.BASE_GENERATION,
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
                // end of ML cycle =================================================================
                cleanup();
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

        private final FileDatabaseMgr mDBMgr;
        private final ConnectivityManager mConnMgr;
        private SampleFile targetFile;

        public TrainingTrigger() {
            mDBMgr = new FileDatabaseMgr(context);
            mConnMgr = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        }
        public void flushDatabase() {
            List<SampleFile> allList = mDBMgr.getFileList(); // it is not in main thread so its fine
            for (SampleFile file : allList) {
                mDBMgr.markStage(file, STAGE_RECEIVED);
                mDBMgr.progressRst(file);
            }
        }
        public SampleFile getDataset(TrainingPara tp) {
            if (! (myState == FL_IDLE)) {                // first no other training going on
                Log.d(TAG, "I am not idle, why you ask me train more");
                return null;
            }
            Log.d(TAG, "currently wifi: " + wifiReady() +
                                            ", charging: " + chargerReady());
            if (! (wifiReady() && chargerReady()))      // second stationary environment
                return null;
            return dataReady(tp);                         // third, available database
        }
        public TrainingPara getNewGlobal() {
            // fetching knowledge
            changeState(FL_COMM);
            TrainingPara mTrainingPara = new TrainingPara();                        // ML context info from remote
            if (! mHTTPAccessor.fetchMinerList(mTrainingPara)) {                    // get minerlist
                Log.d(TAG, "[fetchGlobal] seed node no connection");
                changeState(FL_IDLE);
                return null;
            }
            for (int i = 0; i < mTrainingPara.MINER_LIST.size(); i++) {             // get ML context
                if (mHTTPAccessor.getLatestGlobal(mTrainingPara.MINER_LIST.get(i), mTrainingPara))
                    break;
                if (i == mTrainingPara.MINER_LIST.size() - 1) {
                    Log.d(TAG, "[fetchGlobal] miner node no connection");
                    changeState(FL_IDLE);
                    return null;
                }
            }
            changeState(FL_IDLE);
            if (! newGlobalSniffed(mTrainingPara)) {
                Log.d(TAG, "[fetchGlobal] there is no update on the global model");
                return null;
            }
            GlobalPrefMgr.setField(GlobalPrefMgr.BASE_GEN, mTrainingPara.BASE_GENERATION);
            GlobalPrefMgr.setField(GlobalPrefMgr.TASK, mTrainingPara.SEED_NAME);
            // inform the main activity update of global model
            Intent intent = new Intent(FL_TASK_FILTER);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            return mTrainingPara;
        }
        // private components:
        private boolean newGlobalSniffed(TrainingPara mtp) {
            if (!NEW_GLOBAL_REQUIRED)
                return true;
            String oldTask = GlobalPrefMgr.getFieldString(GlobalPrefMgr.TASK);
            if (oldTask == null)
                oldTask = "null";
            Integer oldVersion = GlobalPrefMgr.getFieldInt(GlobalPrefMgr.BASE_GEN);
            if (oldVersion == null)
                oldVersion = -1;
            String newTask = mtp.SEED_NAME;
            int newVersion = mtp.BASE_GENERATION;
            if (! oldTask.equals(newTask))
                return true;
            if (newVersion > oldVersion)
                return true;
            return false;
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
        private SampleFile dataReady(TrainingPara tp) {        // need a usable training data
            // clear the history train data file list
            targetFile = null;
            if (USE_DUMMY_DATASET)
                return SampleFile.getDummyFile();   // use R.raw.gps_power
            List<SampleFile> allList = mDBMgr.getFileList(); // it is not in main thread so its fine
            for (SampleFile file : allList) {
                if (rightSampleRange(file, tp)) {
                    targetFile = file;
                    Log.d(TAG, "will use file: " + targetFile.fileName);
                    return file;
                }
            }
            return null;
        }
        public boolean rightSampleRange(SampleFile file, TrainingPara tp) {
            String expectingFileName = MyUtils.genFileName(tp.SAMPLE_CENTER_FREQ, tp.SAMPLE_BANDWIDTH);
            return expectingFileName.equals(file.fileName);
        }
        public void markTraining() {
            if (targetFile == null) return;
            mDBMgr.markStage(targetFile, STAGE_TRAINING);
        }
        public void markTrained() {
            if (targetFile == null) return;
            mDBMgr.markStage(targetFile, STAGE_TRAINED);
        }
        public void rollBack() {
            if (targetFile == null) return;
            mDBMgr.markStage(targetFile, STAGE_RECEIVED);
            mDBMgr.progressRst(targetFile);
        }
        public void progressOn() {
            if (targetFile == null) return;
            mDBMgr.progressOn(targetFile);
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