package com.example.decentspec_v3;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import static com.example.decentspec_v3.MyUtils.genName;
import static com.example.decentspec_v3.database.Config.*;

public abstract class GlobalPrefMgr {
    // fields
    private static final String DEVICE_ID = "id";

    private static SharedPreferences myPref = null;
    public static void init(Context context) {
        if (myPref == null) {
            synchronized (GlobalPrefMgr.class) {
                if (myPref == null) {
                    myPref = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
                }
            }
        }
    }
    public static String getName() {
        if (myPref == null) return null;
        if (! myPref.contains(DEVICE_ID)) {
            Log.d("GlobalPref", "didn't find previous id record");
            String device_id = genName(DEVICE_ID_LENGTH);
            myPref.edit()
                    .putString(DEVICE_ID, device_id)
                    .apply();
        }
        return myPref.getString(DEVICE_ID, null);
    }
}
