package com.example.decentspec_v3;

import androidx.annotation.IntRange;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // private fields

    // const
    private String TAG = "MainActivity";
    private String STOP_ACTION = "STOP";
    private String START_ACTION = "START";
    private String ID_INTENT_FILTER = "device id update";
    private String ID_UPDATE_FIELD = "new_id";

    // UI component
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch serial_switch;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch FL_switch;
    private TextView device_id_text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serial_switch = findViewById(R.id.serial_switch);
        FL_switch = findViewById(R.id.FL_switch);

        serial_switch.setChecked(ifMyServiceRunning(SerialListener.class));
        FL_switch.setChecked(ifMyServiceRunning(FLManager.class));

        device_id_text = findViewById(R.id.device_id);

        // update states value from service
        // device id update
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String new_id = intent.getStringExtra(ID_UPDATE_FIELD);
                        device_id_text.setText(new_id);
                    }
                }, new IntentFilter(ID_INTENT_FILTER)
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
            if (touchUSB() && touchLocation()) {
                myToast("USD device detected");
                Intent runMyService = new Intent(this, SerialListener.class);
                runMyService.setAction(START_ACTION);
                startForegroundService(runMyService);
            } else {
                myToast("no available USB device");
            }
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
            if (touchNetwork()) {
                Intent runMyService = new Intent(this, FLManager.class);
                runMyService.setAction(START_ACTION);
                startForegroundService(runMyService);
            } else {
                myToast("Internet unavailable");
            }
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

    // touch methods to make sure the permission
    private boolean touchUSB() {
        // try to open the USB from this activity
        // to make sure the service can visit usb device
        // Find all available drivers from attached devices.
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            Log.d(TAG, "no avail USB drivers");
            return false;
        }

        // Open a connection to the first available driver.
        // default only one device connect to OTG plug (no one will use a usb hub on mobile ... i guess)
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            Log.d(TAG, "no USB permission");
            PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent("android.intent.USB_PERMISSION"), 0);
            manager.requestPermission(driver.getDevice(), mPermissionIntent);
            connection = manager.openDevice(driver.getDevice());
        }
        if (connection == null)
            return false;
        else return true;
        // we simply touch and do not transmission so no need to open the port
//        UsbSerialPort port = driver.getPorts().get(1); // Most devices have just one port (port 0), damn, DIGILENT has 2!!!!!
//        port.open(connection);
//        port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
//        port.close();
    }
    private boolean touchNetwork() {
        return true; // actually the manager did not need network
    }
    private boolean touchLocation() {
        return true; // actually the location is already granted at installation
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