package com.example.decentspec_v3;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.widget.Toast;

import static android.os.Looper.getMainLooper;

public class USBBroadcastReceiver extends BroadcastReceiver {
    public static final String ACTION_USB_PERMISSION = "android.intent.USB_PERMISSION";
    private USBChangeListener mUSBListener;

    public void registerReceiver(Context context, USBChangeListener USBListener) {
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        context.registerReceiver(this, filter);
        this.mUSBListener = USBListener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        int state = -1;
        if (ACTION_USB_PERMISSION.equals(action))
            state = 0;
        else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action))
            state = 1;
        else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action))
            state = 2;
        if (mUSBListener != null) {
            mUSBListener.onUSBStateChange(state, device);
        }
    }

    public interface USBChangeListener {
        int ACTION_GAINED = 0;
        int ACTION_ATTACHED = 1;
        int ACTION_DETACHED = 2;
        void onUSBStateChange(int state, UsbDevice device);
    }

}
