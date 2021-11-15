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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.example.decentspec_v3.Config.*;
import static com.example.decentspec_v3.IntentDirectory.*;

public class SerialListenerService extends Service {

    // const
    private final String TAG = "SerialListener";
    private final int SERIAL_WRITE_TIMEOUT = 1000;

    // service states
    public static final int SERIAL_DISC = 0;
    public static final int SERIAL_HANDSHAKE = 1;
    public static final int SERIAL_SAMPLING = 2;
    public static final int SERIAL_IDLE = 3;

    private static int myState = SERIAL_DISC;

    // notification related
    private final static Integer NOTI_ID = 1;
    private final static String CHANNEL_ID = "Serial_Channel";
    private final static String CHANNEL_NAME = "DecentSpec Serial Notification";
    private final static String CHANNEL_DESC = "no description";
    private final static String NOTI_TITLE = "DecentSpec Serial Listener";
    private final static String NOTI_TEXT_DISCONN = "Try to reconnect USB device";
    private final static String NOTI_TEXT_CONNED = "one USB device attached";
    private final static String NOTI_TICKER = "Serial Listener is initiating ...";
    private NotificationManager mNotificationMgr = null;
    private NotificationCompat.Builder mNotificationBuilder = null;

    // usb related
    private USBBroadcastReceiver mUSBBroadcastReceiver = null;
    private USBBroadcastReceiver.USBChangeListener mUSBHandler = null;
    private UsbManager mUSBManager = null;

    // location related
    private GPSTracker mGPSTracker = null;

    // sample database related
    private OneTimeSample mSampleInstance = null;
    private WriteStreamMgr mWriteStreamMgr = null;
    private FileDatabaseMgr mDBMgr = null;
    private int switchCounter = 0;

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        GlobalPrefMgr.init(this);
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
            changeState(SERIAL_DISC);
            stopForeground(true);
            stopSelf();
        } else {
            Log.d(TAG, "start the service");
            changeState(SERIAL_DISC);
            mNotificationBuilder = genForegroundNotification();
            startForeground(NOTI_ID, mNotificationBuilder.build());
            mDBMgr = new FileDatabaseMgr(this);
            mWriteStreamMgr = new WriteStreamMgr();
            setupUSBReceiver();
            mGPSTracker = new GPSTracker(this);
            if (! mGPSTracker.isAvail())
                appToast("no available GPS Tracker");
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

    private void setupUSBReceiver() {
        mUSBBroadcastReceiver = new USBBroadcastReceiver();
        mUSBHandler = new USBBroadcastReceiver.USBChangeListener() {
            @Override
            public void onUSBStateChange(int state, UsbDevice device) {
                if (state == USBBroadcastReceiver.USBChangeListener.ACTION_GAINED) {
                    if (mUSBManager.hasPermission(device)) {
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
                        changeState(SERIAL_DISC);
                    }
                    mNotificationMgr.notify(NOTI_ID, mNotificationBuilder.build());                }
                if (state == USBBroadcastReceiver.USBChangeListener.ACTION_ATTACHED) {
                    if (mUSBManager.hasPermission(device)) {
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
                    changeState(SERIAL_DISC);
                }
                if (state == -1) {
                    appToast("unrecognized usb acton");
                }
            }
        };
        mUSBBroadcastReceiver.registerReceiver(this, mUSBHandler);
    }

    private UsbDevice touchFirstUSB() {
        // try to access the first usb device

        // Find 0th driver from attached devices.
        if (mUSBManager == null)
            mUSBManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(mUSBManager);
        if (availableDrivers.isEmpty())
            return null;
        // default only one device connect to OTG plug (no one will use a usb hub on mobile ... i guess)
        UsbSerialDriver driver = availableDrivers.get(0);

        if (driver == null) return null;    // there is no driver at all

        if (mUSBManager.hasPermission(driver.getDevice()))
            return driver.getDevice();  // return driver only if we have permission

        PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent("android.intent.USB_PERMISSION"), 0);
        mUSBManager.requestPermission(driver.getDevice(), mPermissionIntent);
        return null;   // also return null if do not have permission, try to get the permission also
    }

    private void changeState(int state) {
        /*
        change the state of the service
        0 - Disconnected
        1 - handshake
        2 - sampling and transmitting
        3 - connected but idle
         */
        myState = state;
        Intent intent = new Intent(SERIAL_STATE_FILTER)
                .putExtra(STATE_FIELD, state);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private class OneTimeSample implements SerialInputOutputManager.Listener{
        // Init -> Handshake -> sampling -> Stored/idle
        // 0       1            2           3

        private static final String TAG = "OneTimeSample";
        private final UsbDevice myDevice; // actually it is not used, because we use the first device as default
        private final UsbSerialPort myPort;
        private SerialInputOutputManager mySerialIOMgr; // a stand alone runnable monitor the input
        private SampleReconfiger mySampleReconfiger;

        public OneTimeSample(UsbDevice device) {    // max sample length 10s
//            appToast("start one sample");
            myState = SERIAL_DISC;
            myDevice = device;
            myPort = setupUsbPort(0,1);
            if (myPort == null) {
                stop();
            } else {
                // try to gather data first
                startRead(); // setup reading data thread
                // reconfig the board after a while
                if (SAMPLE_RECONFIG)
                    mySampleReconfiger = new SampleReconfiger(switchCounter);
                switchCounter = (switchCounter + 1) % SAMPLE_RANGE_NUM;
            }
        }

        private UsbSerialPort setupUsbPort(int driverIndex, int portIndex) {
            if (mUSBManager == null) {
                mUSBManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            }
            List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(mUSBManager);
            if (availableDrivers.isEmpty()) {
                Log.d(TAG, "no available USB drivers");
                return null;
            }
            UsbSerialDriver driver = availableDrivers.get(driverIndex);
            UsbDeviceConnection connection = mUSBManager.openDevice(driver.getDevice());
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

        private void startRead() {
            /* open target file */
            if (mWriteStreamMgr == null || ! mWriteStreamMgr.start()) {
                Log.d(TAG, "unable to open file list");
                stop();
            }
            /* set up GPS listener thread */
            mGPSTracker.setupGPSListener(GPS_UPDATE_INTERVAL);
            /* set up IO listener thread */
            mySerialIOMgr = new SerialInputOutputManager(myPort, this);
            mySerialIOMgr.start();
            changeState(SERIAL_SAMPLING);
        }

        @Override
        public void onNewData(byte[] data) {        // running on the SerialIOMgr thread
            if (mWriteStreamMgr != null) {
                mWriteStreamMgr.record(data);
            }
        }

        @Override
        public void onRunError(Exception e) {
            Log.d(TAG, "Serial io error");
        }

        private class SampleReconfiger {
            private final String TAG = "serialReconfig";
            // it will only reconfig only once, because board will reboot then serial disconnect
            private final Thread myThread;
            public SampleReconfiger(int configIndex) {
                myThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (!Thread.currentThread().isInterrupted())
                            try {
                                Thread.sleep(SAMPLE_SWITCH_INTERVAL);
                                try {
                                    myPort.write(MyUtils.genConfigData(configIndex).getBytes(), SERIAL_WRITE_TIMEOUT);
                                } catch (IOException e) {
                                    Log.d(TAG, "unable to write to serial");
                                    e.printStackTrace();
                                }
                            } catch (InterruptedException e) {
                                Log.d(TAG, "Auto reconfigure quit");
                                return;
                            }
                    }
                });
                myThread.start();
            }
            public void stop() {
                if (myThread.isAlive()) {
                    myThread.interrupt();
                }
            }
        }

        public void stop() {
            /* close sample reconfiger */
            if (mySampleReconfiger != null) {
                mySampleReconfiger.stop();
                mySampleReconfiger = null;
            }
            /* close serial port */
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
            /* pause GPS listener */
            mGPSTracker.cleanGPSListener();
            /* pause writer thread */
            if (mWriteStreamMgr != null) {
                mWriteStreamMgr.stop();
            }
            changeState(SERIAL_IDLE);
//            appToast("sample is finished");
        }
    }

    private class WriteStreamMgr {
        private final String TAG = "DataFileWriter";
        private ArrayList<String> myFileNames;
        private String longBuffer = "";
        private final Object bufferLock = new Object();
        private final Runnable writerTemplate;
        private Thread writerThread;

        public WriteStreamMgr() {
            writerTemplate = new Runnable() {
                @Override
                public void run() {
                    while (!Thread.currentThread().isInterrupted()) {
                        String new_line = null;
                        synchronized (bufferLock) {
                            int startIndex = longBuffer.indexOf(SAMPLE_START_SIGNAL);
                            int endIndex = longBuffer.indexOf(SAMPLE_END_SIGNAL);
                            if (startIndex >= 0 && endIndex > 0) {   // if both exist
                                new_line = longBuffer.substring(startIndex, endIndex);
                                longBuffer = longBuffer.substring(endIndex);
                            }
                        }
                        if (new_line != null)
                            writeIntoFile(new_line);
                    }
                }
                // because the consuming speed of buffer is much faster than the serial speed
                // we could add the gps and timestamp only when we store the data into file
                public void writeIntoFile(String content) {
                    double[] gps = mGPSTracker.getCurLocation();
                    long time = MyUtils.genTimestamp();
                    String[] elements = content.replace(SAMPLE_START_SIGNAL, "")
                                                .replace(SAMPLE_END_SIGNAL, "")
                                                .split("\\s+");
                    int centerFreq = Integer.parseInt(elements[0]);
                    int bandwidth = Integer.parseInt(elements[1]);
                    String[] PSD = Arrays.copyOfRange(elements, 2, elements.length);
                    if (PSD.length != SAMPLE_BIN_NUM) {
                        Log.d(TAG, "incorrect serial input format!");
                        return;
                    }
                    String newLine = String.format("%f %f %d %s\n", gps[0], gps[1], time, String.join(" ", PSD));
                    String targetFileName = MyUtils.genFileName(centerFreq, bandwidth);
                    List<SampleFile> currentFiles = mDBMgr.getFileList();
                    SampleFile targetFile = null;
                    for (SampleFile file : currentFiles) {
                        if (file.fileName.equals(targetFileName)) {
                            targetFile = file;
                        }
                    }
                    if (targetFile == null) {
                        targetFile = mDBMgr.createEntry(targetFileName);
                    }
                    mDBMgr.markStage(targetFile, SampleFile.STAGE_RECEIVING);
                    try {
                        FileOutputStream myOutputStream = openFileOutput(targetFileName, Context.MODE_APPEND);
                        myOutputStream.write(newLine.getBytes());
                        myOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mDBMgr.markStage(targetFile, SampleFile.STAGE_RECEIVED);
                }
            };
        }
        public void record(byte[] content) {
            synchronized (bufferLock) {
                String rawString = new String(content);
                longBuffer += rawString;
            }
        }
        public boolean start() {
            if (writerThread != null && writerThread.isAlive()) {
                flushBuffer();
                return true;
            }
            writerThread = new Thread(writerTemplate);
            writerThread.start();
            return true;
        }
        public boolean stop() {
            if (writerThread == null) {
                return false;
            }
            writerThread.interrupt();
            return writerThread.isInterrupted();
        }
        public void flushBuffer() {
            synchronized (bufferLock) {
                longBuffer = "";
            }
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

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.serial)
                .setContentTitle(NOTI_TITLE)
                .setContentText(NOTI_TEXT_DISCONN)
                .setTicker(NOTI_TICKER)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
    }

    public static int getState() {
        return myState;
    }
}