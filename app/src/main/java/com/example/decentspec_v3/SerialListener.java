package com.example.decentspec_v3;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.decentspec_v3.database.FileDatabaseMgr;
import com.example.decentspec_v3.database.SampleFile;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import static com.example.decentspec_v3.database.SampleFile.STAGE_RECEIVED;

public class SerialListener extends Service {

    // service states
    private static final int DISC = 0;
    private static final int HANDSHAKE = 1;
    private static final int SAMPLING = 2;
    private static final int IDLE = 3;

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
    private String NOTI_TEXT_DISCONN = "Try to reconnect USB device";
    private String NOTI_TEXT_CONNED = "one USB device attached";
    private String NOTI_TICKER = "Serial Listener is initiating ...";
    private NotificationCompat.Builder mNotificationBuilder = null;

    // listener related
    private USBBroadcastReceiver mUSBBroadcastReceiver = null;
    private USBBroadcastReceiver.USBChangeListener mHandler = null;

    // usb related
    private UsbManager manager = null;

    // sample related
    private OneTimeSample mSampleInstance = null;
    private FileDatabaseMgr myDBMgr = null;

    // intent related
    private String SERIAL_SERVICE_FILTER = "service.serial";
    private String STATE_FIELD = "state";

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(STOP_ACTION)) {
            Log.d(TAG, "stop the service");
            unregisterReceiver(mUSBBroadcastReceiver);
            if (mSampleInstance != null) {
                mSampleInstance.stop();
                mSampleInstance = null;
            }
            notifyState(DISC);
            stopForeground(true);
            stopSelf();
        } else {
            Log.d(TAG, "start the service");
            mNotificationBuilder = genForegroundNotification();
            startForeground(NOTI_ID, mNotificationBuilder.build());
            myDBMgr = new FileDatabaseMgr(this);
            setupReceiver();
            notifyState(DISC);
            UsbDevice myFirstUSB = touchFirstUSB(); // it will trigger the receiver if asking for permission
            if (myFirstUSB != null) { // if we already has the permission
                mNotificationBuilder.setContentText(NOTI_TEXT_CONNED);
                mNotificationMgr.notify(NOTI_ID, mNotificationBuilder.build());
                appToast("one device detected");
                mSampleInstance = new OneTimeSample(myFirstUSB);
            }
        }
        return START_NOT_STICKY;
    }

    private void setupReceiver() {
        mUSBBroadcastReceiver = new USBBroadcastReceiver();
        mHandler = new USBBroadcastReceiver.USBChangeListener() {
            @Override
            public void onUSBStateChange(int state, UsbDevice device) {
                if (state == USBBroadcastReceiver.USBChangeListener.ACTION_GAINED) {
                    if (manager.hasPermission(device)) {
                        mNotificationBuilder.setContentText(NOTI_TEXT_CONNED);
                        appToast("new device attached");
                        mSampleInstance = new OneTimeSample(device);
                    } else {
                        mNotificationBuilder.setContentText(NOTI_TEXT_DISCONN);
                        appToast("please reconnect your device");
                        if (mSampleInstance != null) {
                            mSampleInstance.stop();
                            mSampleInstance = null;
                        }
                        notifyState(DISC);
                    }
                    mNotificationMgr.notify(NOTI_ID, mNotificationBuilder.build());                }
                if (state == USBBroadcastReceiver.USBChangeListener.ACTION_ATTACHED) {
                    if (manager.hasPermission(device)) {
                       mNotificationBuilder.setContentText(NOTI_TEXT_CONNED);
                       mNotificationMgr.notify(NOTI_ID, mNotificationBuilder.build());
                       mSampleInstance = new OneTimeSample(device);
                    } else {
                        touchFirstUSB(); // it will 100% trigger the permission request
                    }
                }
                if (state == USBBroadcastReceiver.USBChangeListener.ACTION_DETACHED) {
                    if (mSampleInstance != null) {
                        mSampleInstance.stop();
                        mSampleInstance = null;
                    }
                    mNotificationBuilder.setContentText(NOTI_TEXT_DISCONN);
                    appToast("USB device disconnected");
                    mNotificationMgr.notify(NOTI_ID, mNotificationBuilder.build());
                    notifyState(DISC);
                }
                if (state == -1) {
                    appToast("unrecognized usb acton");
                }
            }
        };
        mUSBBroadcastReceiver.registerReceiver(this, mHandler);
    }

    private UsbDevice touchFirstUSB() {
        // try to access the first usb device

        // Find 0th driver from attached devices.
        if (manager == null)
            manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty())
            return null;
        // default only one device connect to OTG plug (no one will use a usb hub on mobile ... i guess)
        UsbSerialDriver driver = availableDrivers.get(0);

        if (driver == null) return null;    // there is no driver at all

        if (manager.hasPermission(driver.getDevice()))
            return driver.getDevice();  // return driver only if we have permission

        PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent("android.intent.USB_PERMISSION"), 0);
        manager.requestPermission(driver.getDevice(), mPermissionIntent);
        return null;   // also return null if do not have permission, try to get the permission also
    }

    private void notifyState(int state) {
        /*
        change the state of the service
        0 - Disconnected
        1 - handshake
        2 - sampling and transmitting
        3 - connected but idle
         */
        Intent intent = new Intent(SERIAL_SERVICE_FILTER);
        intent.putExtra(STATE_FIELD, state);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private class OneTimeSample implements SerialInputOutputManager.Listener{
        // Init -> Handshake -> sampling -> Stored/idle
        // 0       1            2           3

        private static final String TAG = "OneTimeSample";
        private UsbDevice myDevice; // actually it is not used, because we use the first device as default
        private UsbSerialPort myPort;
        private SerialInputOutputManager mySerialIOMgr; // a stand alone runnable monitor the input
        private FileOutputStream myWriteStream = null;
        private SampleFile mySampleFileEntry = null;
        private int myState = 0;

        public OneTimeSample(UsbDevice device) {
//            appToast("start one sample");
            myDevice = device;
            myPort = setupUsbPort(0,1);
            if (myPort == null) {
                stop();
            } else {
                handshake(); // send out handshake signal
                startRead(); // start reading data
            }
        }

        private UsbSerialPort setupUsbPort(int driverIndex, int portIndex) {
            if (manager == null) {
                manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            }
            List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
            if (availableDrivers.isEmpty()) {
                Log.d(TAG, "no available USB drivers");
                return null;
            }
            UsbSerialDriver driver = availableDrivers.get(driverIndex);
            UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
            if (connection == null) {
                Log.d(TAG, "no permission to connect");
                return  null;
            }
            UsbSerialPort port = driver.getPorts().get(portIndex);
            try {
                port.open(connection);
                port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            } catch (IOException e) {
                Log.d(TAG, "unknown mistake when init connection");
                return null;
            }
            return  port;
        }

        private void handshake() {
            // currently didn't implement
            notifyState(HANDSHAKE);
            myState = HANDSHAKE;
        }
        private void startRead() {
            /* create file */
            try {
                String filename = "demo_" + MyUtils.genTimestamp() + ".txt";
                myWriteStream = openFileOutput(filename, Context.MODE_PRIVATE);
                mySampleFileEntry = myDBMgr.createEntry(filename);
            } catch (IOException e) {
                Log.d(TAG, "unable to create file");
                stop();
            }
            /* set up IO listener thread*/
            mySerialIOMgr = new SerialInputOutputManager(myPort, this);
            mySerialIOMgr.start();
            notifyState(SAMPLING);
            myState = SAMPLING;
        }

        @Override
        public void onNewData(byte[] data) {        // running on the SerialIOMgr thread
            try {
                if (myWriteStream != null) {
                    myWriteStream.write("|------------|".getBytes()); // add a divider to judge the size of the buffer
                    myWriteStream.write(data);
                }
            } catch (IOException e) {
                Log.d(TAG, "fail to write into the file");
            }
        }

        @Override
        public void onRunError(Exception e) {
            Log.d(TAG, "Serial io error");
        }

        public void stop() {
            /* close serial port*/
            try {
                if (myPort != null)
                    myPort.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            /* terminate reading thread */
            if (mySerialIOMgr != null) {
                mySerialIOMgr.stop();
                mySerialIOMgr = null;
            }
            /* save file */
            try {
                if (myWriteStream != null) {
                    myWriteStream.close();
                    myWriteStream = null;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            /* update database */
            if (mySampleFileEntry != null) {
                myDBMgr.markStage(mySampleFileEntry, STAGE_RECEIVED);
                mySampleFileEntry = null;
            }
            notifyState(IDLE);
            myState = IDLE;
//            appToast("sample is finished");
        }
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
    private NotificationCompat.Builder genForegroundNotification() {
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
                .setContentText(NOTI_TEXT_DISCONN)
                .setTicker(NOTI_TICKER)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        return builder;
    }
}