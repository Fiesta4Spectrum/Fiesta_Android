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
import android.view.View;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.example.decentspec_v3.database.DBViewModel;
import com.example.decentspec_v3.database.SampleFile;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    // private fields

    // const
    private String TAG = "MainActivity";
    private String STOP_ACTION = "STOP";
    private String START_ACTION = "START";
    private String ID_INTENT_FILTER = "device.id.update";
    private String ID_UPDATE_FIELD = "new_id";
    private String SERIAL_SERVICE_FILTER = "service.serial";
    private String FL_SERVICE_FILTER = "service.fl";
    private String STATE_FIELD = "state";

    // UI component
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch serial_switch;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch FL_switch;

    // live data source
    private DBViewModel mDBViewModel;
    private RecyclerView myDBRV;
    private RecyclerViewAdapter myDBRVAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serial_switch = findViewById(R.id.serial_switch);
        FL_switch = findViewById(R.id.FL_switch);

        serial_switch.setChecked(ifMyServiceRunning(SerialListener.class));
        FL_switch.setChecked(ifMyServiceRunning(FLManager.class));

        myDBRV = findViewById(R.id.rv_database);
        mDBViewModel = new ViewModelProvider(this).get(DBViewModel.class);
        Activity activityContext = this;
        mDBViewModel.pull().observe(this, new Observer<List<SampleFile>>() {
            @Override
            public void onChanged(List<SampleFile> sampleFiles) {
                myDBRVAdapter = new RecyclerViewAdapter(activityContext, sampleFiles);
                myDBRV.setLayoutManager(new LinearLayoutManager(activityContext));
                myDBRV.setAdapter(myDBRVAdapter);
            }
        });
        // update states value from service
        // device id update
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String new_id = intent.getStringExtra(ID_UPDATE_FIELD);
                        TextView device_id_text = findViewById(R.id.device_id);
                        device_id_text.setText(new_id);
                    }
                }, new IntentFilter(ID_INTENT_FILTER)
        );
        // serial service update
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        int state = intent.getIntExtra(STATE_FIELD, 0);
                        int radioId = findViewById(R.id.RB_board_disc).getId();
                        switch (state) {
                            case 0: radioId = findViewById(R.id.RB_board_disc).getId(); break;
                            case 1: radioId = findViewById(R.id.RB_board_hand).getId(); break;
                            case 2: radioId = findViewById(R.id.RB_board_sample).getId(); break;
                            case 3: radioId = findViewById(R.id.RB_board_idle).getId(); break;
                        }
                        RadioGroup serialRadio = findViewById(R.id.serialStateGroup);
                        serialRadio.check(radioId);
                    }
                }, new IntentFilter(SERIAL_SERVICE_FILTER)
        );
    }

    // switch actions
    public void toggleSerialService(View view) {
        boolean iAmRunning = ifMyServiceRunning(SerialListener.class);
        if (serial_switch.isChecked()) {
            // start the service request
            if (iAmRunning) {
                return;
            }
            /*start foreground service*/
            Intent runMyService = new Intent(this, SerialListener.class);
            runMyService.setAction(START_ACTION);
            startForegroundService(runMyService);
            serial_switch.setChecked(ifMyServiceRunning(SerialListener.class));
        } else {
            // turn-off service request
            if (! iAmRunning) {
                return;
            }
            /*stop foreground service*/
            Intent stopMyService = new Intent(this, SerialListener.class);
            stopMyService.setAction(STOP_ACTION);
            startService(stopMyService);
        }
    }
    public void toggleFLService(View view) {
        boolean iAmRunning = ifMyServiceRunning(FLManager.class);
        if (FL_switch.isChecked()) {
            // start the service request
            if (iAmRunning) {
                return;
            }
            /*start foreground service*/
            Intent runMyService = new Intent(this, FLManager.class);
            runMyService.setAction(START_ACTION);
            startForegroundService(runMyService);
            FL_switch.setChecked(ifMyServiceRunning(FLManager.class));
        } else {
            // turn-off service request
            if (! iAmRunning) {
                return;
            }
            /*stop foreground service*/
            Intent stopMyService = new Intent(this, FLManager.class);
            stopMyService.setAction(STOP_ACTION);
            startService(stopMyService);
        }
    }
    // help actions
    public void onPressHelpSerial(View view) {
        showDialog("A compatible spectrum sensing board is expected to connect to this device, with which data used to fuel local training will be gathered.\nThe Serial Listener will stay in background and package each sampling into files.");
    }
    public void onPressHelpFL(View view) {
        showDialog("Local training starts only when the device is connected to WiFi network with power cable plugged, considering its bandwidth and power consuming.\nAfter that, the trained local model will be upload to miner network.");
    }
    public void onPressHelpData(View view) {
        showDialog("You could view records of your local sampled datasets here.\n\"Receiving\" means it is under transmission through the USB cable.\n\"Training\" means it is under local training");
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