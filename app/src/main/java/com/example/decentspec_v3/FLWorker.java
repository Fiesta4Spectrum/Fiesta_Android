package com.example.decentspec_v3;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.decentspec_v3.database.FileDatabaseMgr;
import com.example.decentspec_v3.database.SampleFile;
import com.example.decentspec_v3.federated_learning.FileAccessor;
import com.example.decentspec_v3.federated_learning.HTTPAccessor;
import com.example.decentspec_v3.federated_learning.HelperMethods;
import com.example.decentspec_v3.federated_learning.ScoreListener;
import com.example.decentspec_v3.federated_learning.TrainingPara;

import org.deeplearning4j.datasets.iterator.DoublesDataSetIterator;
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

public class FLWorker extends Thread {
    private final String TAG;
    private final Context context;
    private final int id;

    private TrainingPara mTP;
    private HTTPAccessor mHTTP;
    private TrainingTrigger mTrigger;

    private String oldTask = "null";
    private int oldVersion = -1;
    private boolean notified = false;

    // Constructor
    public FLWorker(String tag, Context context, String seedAddr, int id) {
        this.id = id;
        this.TAG = tag;
        this.context = context;
        this.mHTTP = new HTTPAccessor(context, seedAddr);
        this.mTrigger = new TrainingTrigger();
    }

    private void appToast(String content) {
        Handler mainHandler = new Handler(context.getMainLooper());
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context.getApplicationContext(), content, Toast.LENGTH_SHORT).show();
            }
        });
    }
    // thread loop
    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // REMOTE ready: new global available
                TrainingPara mTPara = mTrigger.getNewGlobal();
                // LOCAL ready: wifi, battery, and local files
                SampleFile dataFile = mTrigger.getDataset(mTPara); // TODO change it to file list instead fo a single file
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

    // training procedure
    private void oneLocalTraining(SampleFile file, TrainingPara mTrainingPara) {

        // start of ML pipeline ============================================================
        // ***** read train dataset *****
        FileAccessor mFileAccessor = new FileAccessor(context);                 // io entity
        List<Pair<double[], double[]>> localTrainList = mFileAccessor.readFrom(file.fileName, mTrainingPara);
        if (localTrainList == null || localTrainList.size() == 0) {
            Log.d(TAG, "file not available");
            cleanup();
            return;
        }
        updateReward();
        // ***** create model *****
        if (SHUFFLE_DATASET_AHEAD)
            Collections.shuffle(localTrainList);
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
        if (ENABLE_GC_FREQ_LIMIT)
            Nd4j.getMemoryManager().setAutoGcWindow(5000);
        for (int i = 0; i < mTrainingPara.EPOCH_NUM; i++) {
            try {
                // check interrupt signal between epochs
                if (Thread.currentThread().isInterrupted()) {
                    cleanup();
                    return; // early return due to interrupt
                }

                ScoreListener mySL = new ScoreListener(TAG, mTrainingPara);
                localModel.setListeners(mySL);
                localModel.fit(localDataset);
                Log.d(TAG, "one epoch complete!");
                if (i == 0)
                    init_loss = mySL.getScore(); // TODO this is not accurate, a score after one epoch training.
                if (i == mTrainingPara.EPOCH_NUM - 1)
                    end_loss = mySL.getScore();
            } catch (RuntimeException e) {
                Thread.currentThread().interrupt();
                cleanup();
                return; // early return due to interrupt
            }
        }
        // ***** upload to miner *****
        if (id == 1)
            GlobalPrefMgr.setField(GlobalPrefMgr.TRAINED_INDEX_1,
                    GlobalPrefMgr.getFieldInt(GlobalPrefMgr.TRAINED_INDEX_1) + 1);
        if (id == 2)
            GlobalPrefMgr.setField(GlobalPrefMgr.TRAINED_INDEX_2,
                    GlobalPrefMgr.getFieldInt(GlobalPrefMgr.TRAINED_INDEX_2) + 1);
        double delta_loss = init_loss-end_loss;
        if ((! UPLOAD_SUPPRESSION) || (delta_loss >= 0)) {
            try {
                for (int i = 0; i < mTrainingPara.MINER_LIST.size(); i++) {
                    if (mHTTP.sendTrainedLocal(
                            mTrainingPara.MINER_LIST.get(i),
                            mTrainingPara.DATASET_SIZE,
                            delta_loss,
                            end_loss,
                            mTrainingPara,
                            HelperMethods.paramTable2stateDict(localModel.paramTable()))) {
                        oldVersion = mTrainingPara.BASE_GENERATION;
                        oldTask = mTrainingPara.SEED_NAME;
                        if (id == 1)
                            GlobalPrefMgr.setField(GlobalPrefMgr.UPLOADED_INDEX_1,
                                    GlobalPrefMgr.getFieldInt(GlobalPrefMgr.UPLOADED_INDEX_1) + 1);
                        if (id == 2)
                            GlobalPrefMgr.setField(GlobalPrefMgr.UPLOADED_INDEX_2,
                                    GlobalPrefMgr.getFieldInt(GlobalPrefMgr.UPLOADED_INDEX_2) + 1);
                        break;
                    }
                    if (i == mTrainingPara.MINER_LIST.size() - 1) {
                        Log.d(TAG, "[updateLocal] miner node no connection");
                        cleanup();
                        return;
                    }
                }
            } catch (JsonProcessingException | JSONException e) {
                e.printStackTrace();
            }
        }
        // end of ML cycle =================================================================
        updateNumbers();
        updateReward();
        cleanup();
    }

    private void updateNumbers() {
        // update upload/trained
        Intent intent = new Intent(FL_UIUPDATE_FILTER)
                .putExtra(WORKER_ID, id);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
    private void updateReward() {
        // update reward
        Double reward = mHTTP.fetchReward(GlobalPrefMgr.getFieldString(GlobalPrefMgr.DEVICE_ID));
        Intent reward_intent = new Intent(FL_REWARD_FILTER)
                .putExtra(WORKER_ID, id)
                .putExtra(REWARD, reward);
        LocalBroadcastManager.getInstance(context).sendBroadcast(reward_intent);
    }
    private void cleanup() { // call when interrupted
        // seems no specific things need to do
    }

    // Training trigger
    private class TrainingTrigger {

        private final FileDatabaseMgr mDBMgr;
        private final ConnectivityManager mConnMgr;

        public TrainingTrigger() {
            mDBMgr = new FileDatabaseMgr(context);
            mConnMgr = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        }
        public SampleFile getDataset(TrainingPara tp) {
            Log.d(TAG, "currently wifi: " + wifiReady() +
                    ", charging: " + chargerReady());
            if (! (wifiReady() && chargerReady()))      // second stationary environment
                return null;
            return dataReady(tp);                         // third, available database
        }
        public TrainingPara getNewGlobal() {
            // fetching knowledge
            TrainingPara mTrainingPara = new TrainingPara();                        // ML context info from remote
            if (! mHTTP.fetchMinerList(mTrainingPara)) {                    // get minerlist
                Log.d(TAG, "[fetchGlobal] seed node no connection");
                if (! notified) {
                    appToast("Seed Server " + id + " Offline");
                    notified = true;
                }
                return null;
            }
            if (mTrainingPara.MINER_LIST == null) {
                Log.d(TAG, "[fetchGlobal] empty miner list");
                return null;
            }
            if (mTrainingPara.MINER_LIST.size() == 0) {
                Log.d(TAG, "[fetchGlobal] no online miner");
                return null;
            }
            for (int i = 0; i < mTrainingPara.MINER_LIST.size(); i++) {             // get ML context
                if (mHTTP.getLatestGlobal(mTrainingPara.MINER_LIST.get(i), mTrainingPara))
                    break;
                if (i == mTrainingPara.MINER_LIST.size() - 1) {
                    Log.d(TAG, "[fetchGlobal] miner node no connection");
                    return null;
                }
            }
            if (! newGlobalSniffed(mTrainingPara)) {
                Log.d(TAG, "[fetchGlobal] there is no update on the global model");
                return null;
            }
            // inform the main activity update of global model
            Intent intent = new Intent(FL_TASK_FILTER)
                    .putExtra(TASK_GEN, mTrainingPara.BASE_GENERATION)
                    .putExtra(TASK_NAME, mTrainingPara.SEED_NAME)
                    .putExtra(WORKER_ID, id);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            return mTrainingPara;
        }
        // private components:
        private boolean newGlobalSniffed(TrainingPara mtp) {
            if (!NEW_GLOBAL_REQUIRED)
                return true;
            if (mtp == null)
                return false;
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
            if (DEADLY_TRAINER)
                return true;
            Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;
        }
        private SampleFile dataReady(TrainingPara tp) {        // need a usable training data
            // clear the history train data file list
            if (tp == null)
                return null;
            SampleFile targetFile = null;
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
            if ((tp == null) || (tp.SAMPLE_CENTER_FREQ == null) || (tp.SAMPLE_BANDWIDTH == null))
                return false;
            String expectingFileName = MyUtils.genFileName(tp.SAMPLE_CENTER_FREQ, tp.SAMPLE_BANDWIDTH);
            return expectingFileName.equals(file.fileName);
        }
    }
}
