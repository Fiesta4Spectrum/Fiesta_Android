package com.example.decentspec_v3;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.telephony.gsm.GsmCellLocation;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.example.decentspec_v3.database.DBViewModel;
import com.example.decentspec_v3.database.SampleFile;

import java.util.List;

import static com.example.decentspec_v3.FLManagerService.FL_COMM;
import static com.example.decentspec_v3.FLManagerService.FL_IDLE;
import static com.example.decentspec_v3.FLManagerService.FL_TRAIN;
import static com.example.decentspec_v3.IntentDirectory.*;
import static com.example.decentspec_v3.SerialListenerService.SERIAL_DISC;
import static com.example.decentspec_v3.SerialListenerService.SERIAL_HANDSHAKE;
import static com.example.decentspec_v3.SerialListenerService.SERIAL_IDLE;
import static com.example.decentspec_v3.SerialListenerService.SERIAL_SAMPLING;


public class MainActivity extends AppCompatActivity {

    // private fields

    // const
    private String TAG = "MainActivity";

    // UI component
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch serial_switch;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch FL_switch;

    private RecyclerView myDBRV;
    private RecyclerViewAdapter myDBRVAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // init route
        if (Config.ALWAYS_ON)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        GlobalPrefMgr.init(this);
        if (GlobalPrefMgr.getName() == null)
            usrInputDeviceId();
        else {
            //display id
            TextView device_id = findViewById(R.id.device_id);
            device_id.setText("id: " + GlobalPrefMgr.getName());
        }

        serial_switch = findViewById(R.id.serial_switch);
        FL_switch = findViewById(R.id.FL_switch);

        // initial states read and resource binding
        // get the states of two service and show
        serial_switch.setChecked(ifMyServiceRunning(SerialListenerService.class));
        FL_switch.setChecked(ifMyServiceRunning(FLManagerService.class));
        switchRadioFL(FLManagerService.getState());
        switchRadioSerial(SerialListenerService.getState());

        myDBRV = findViewById(R.id.rv_database);

        // live data binding
        // database
        DBViewModel mDBViewModel = new ViewModelProvider(this).get(DBViewModel.class);
        Activity activityContext = this;
        mDBViewModel.pull().observe(this, new Observer<List<SampleFile>>() {
            @Override
            public void onChanged(List<SampleFile> sampleFiles) {
                myDBRVAdapter = new RecyclerViewAdapter(activityContext, sampleFiles);
                myDBRV.setLayoutManager(new LinearLayoutManager(activityContext));
                myDBRV.setAdapter(myDBRVAdapter);
            }
        });

        // gps update
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        TextView GPS_text = findViewById(R.id.GPS_textview);
                        @SuppressLint("DefaultLocale")
                        String GPS_value = String.format("(%f ,%f)",
                                intent.getDoubleExtra(GPS_LATI_FIELD ,0.0),
                                intent.getDoubleExtra(GPS_LONGI_FIELD, 0.0));
                        GPS_text.setText(GPS_value);
                        Log.d(TAG, "GPS updates");
                    }
                }, new IntentFilter(SERIAL_GPS_FILTER)
        );
        // serial service update
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        int state = intent.getIntExtra(STATE_FIELD, 0);
                        switchRadioSerial(state);
                    }
                }, new IntentFilter(SERIAL_STATE_FILTER)
        );
        // FL service update
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        int state = intent.getIntExtra(STATE_FIELD, 0);
                        switchRadioFL(state);
                    }
                }, new IntentFilter(FL_STATE_FILTER)
        );
        // update task and version
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String task = intent.getStringExtra(TASK_NAME);
                        int version = intent.getIntExtra(TASK_GEN, -1);
                        int worker = intent.getIntExtra(WORKER_ID, 0);
                        TextView taskName = null;
                        if (worker == 1)
                            taskName = findViewById(R.id.TaskAndVer1);
                        if (worker == 2)
                            taskName = findViewById(R.id.TaskAndVer2);
                        if (taskName != null)
                            taskName.setText(task + " @ " + version);
                    }
                }, new IntentFilter(FL_TASK_FILTER));
        // update reward
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        double reward = intent.getDoubleExtra(REWARD, 0.0);
                        int worker = intent.getIntExtra(WORKER_ID, 0);
                        TextView rewardText = null;
                        if (worker == 1)
                            rewardText = findViewById(R.id.reward1);
                        if (worker == 2)
                            rewardText = findViewById(R.id.reward2);
                        if (rewardText != null)
                            rewardText.setText(String.format("%.2f", reward));
                    }
                }, new IntentFilter(FL_REWARD_FILTER));

        // update uploadsuppression data
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new BroadcastReceiver() {
                    @SuppressLint("DefaultLocale")
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        int worker = intent.getIntExtra(WORKER_ID, 0);
                        TextView up_sup;
                        if (worker == 1) {
                            up_sup = findViewById(R.id.up_sup_1);
                            up_sup.setText(String.format("%d/%d",
                                    GlobalPrefMgr.getFieldInt(GlobalPrefMgr.UPLOADED_INDEX[1]),
                                    GlobalPrefMgr.getFieldInt(GlobalPrefMgr.TRAINED_INDEX[1])));
                        }

                        if (worker == 2) {
                            up_sup = findViewById(R.id.up_sup_2);
                            up_sup.setText(String.format("%d/%d",
                                    GlobalPrefMgr.getFieldInt(GlobalPrefMgr.UPLOADED_INDEX[2]),
                                    GlobalPrefMgr.getFieldInt(GlobalPrefMgr.TRAINED_INDEX[2])));
                        }
                    }
                }, new IntentFilter(FL_UIUPDATE_FILTER));
    }

    private void switchRadioSerial(int state) {
        int radioId = findViewById(R.id.RB_board_disc).getId();
        switch (state) {
            case SERIAL_DISC: radioId = findViewById(R.id.RB_board_disc).getId(); break;
            case SERIAL_HANDSHAKE: radioId = findViewById(R.id.RB_board_hand).getId(); break;
            case SERIAL_SAMPLING: radioId = findViewById(R.id.RB_board_sample).getId(); break;
            case SERIAL_IDLE: radioId = findViewById(R.id.RB_board_idle).getId(); break;
        }
        RadioGroup serialRadio = findViewById(R.id.serialStateGroup);
        serialRadio.check(radioId);
    }
    private void switchRadioFL(int state) {
        int radioId = findViewById(R.id.RB_board_disc).getId();
        switch (state) {
            case FL_TRAIN: radioId = findViewById(R.id.RB_ML_train).getId(); break;
            case FL_COMM: radioId = findViewById(R.id.RB_ML_comm).getId(); break;
            case FL_IDLE: radioId = findViewById(R.id.RB_ML_idle).getId(); break;
        }
        RadioGroup FLRadio = findViewById(R.id.FLStateGroup);
        FLRadio.check(radioId);
    }


    // switch actions
    public void toggleSerialService(View view) {
        boolean iAmRunning = ifMyServiceRunning(SerialListenerService.class);
        if (serial_switch.isChecked()) {
            // start the service request
            if (iAmRunning) {
                return;
            }
            /*start foreground service*/
            Intent runMyService = new Intent(this, SerialListenerService.class);
            runMyService.setAction(START_ACTION);
            startForegroundService(runMyService);
            serial_switch.setChecked(ifMyServiceRunning(SerialListenerService.class));
        } else {
            // turn-off service request
            if (! iAmRunning) {
                return;
            }
            /*stop foreground service*/
            Intent stopMyService = new Intent(this, SerialListenerService.class);
            stopMyService.setAction(STOP_ACTION);
            startService(stopMyService);
        }
    }
    public void toggleFLService(View view) {
        boolean iAmRunning = ifMyServiceRunning(FLManagerService.class);
        if (FL_switch.isChecked()) {
            // start the service request
            if (iAmRunning) {
                return;
            }
            /*start foreground service*/
            Intent runMyService = new Intent(this, FLManagerService.class);
            runMyService.setAction(START_ACTION);
            startForegroundService(runMyService);
            FL_switch.setChecked(ifMyServiceRunning(FLManagerService.class));
        } else {
            // turn-off service request
            if (! iAmRunning) {
                return;
            }
            /*stop foreground service*/
            Intent stopMyService = new Intent(this, FLManagerService.class);
            stopMyService.setAction(STOP_ACTION);
            startService(stopMyService);
        }
    }
    // help actions
    public void onPressHelpSerial(View view) {
        showDialog("A compatible spectrum sensing board is expected to connect to this device, with which data used to fuel local training will be gathered.\nThe Serial Listener will stay in background and package each sampling into files.");
    }
    public void onPressHelpFL(View view) {
        showDialog("Local training starts only when the device is connected to WiFi network with power cable plugged, considering its power consuming.\nAfter that, the trained local model will be upload to miner network.");
    }
    public void onPressHelpData(View view) {
        showDialog("You could view records of your local sampled datasets here.\nFile name is defined by center frequency and bandwidth. You could check the number of sampled data in each file below.");
    }
    private void showDialog(String content) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(content)
                .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                }).show();
    }
    private void usrInputDeviceId() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Give me a unique name (num and alphabet)")
        .setCancelable(false);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                GlobalPrefMgr.initFields(input.getText().toString());
                TextView device_id = findViewById(R.id.device_id);
                device_id.setText("id: " + GlobalPrefMgr.getName());
            }
        });
        builder.setNegativeButton("Random", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                GlobalPrefMgr.initFields("");
                TextView device_id = findViewById(R.id.device_id);
                device_id.setText("id: " + GlobalPrefMgr.getName());
                dialog.cancel();
            }
        });

        builder.show();
    }

    // utility methods
    @SuppressWarnings("deprecation")
    private boolean ifMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
            }
        }
        return false;
    }

    private void myToast(String content) {
        Toast.makeText(this, content, Toast.LENGTH_SHORT).show();
    }
}