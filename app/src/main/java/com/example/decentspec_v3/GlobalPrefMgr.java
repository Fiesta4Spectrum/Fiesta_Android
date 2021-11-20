package com.example.decentspec_v3;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import static com.example.decentspec_v3.MyUtils.genName;
import static com.example.decentspec_v3.Config.*;

public abstract class GlobalPrefMgr {

    // fields name
    public static final String DEVICE_ID = "id";
    public static final String UPLOADED_INDEX_1 = "numOfUploads_1";
    public static final String TRAINED_INDEX_1 = "numOfLocalTraining_1";
    public static final String UPLOADED_INDEX_2 = "numOfUploads_2";
    public static final String TRAINED_INDEX_2 = "numOfLocalTraining_2";

    private static SharedPreferences myPref = null;
    public static void init(Context context) {
        if (myPref == null) {
            synchronized (GlobalPrefMgr.class) {
                if (myPref == null) {
                    myPref = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
                    initFields();
                }
            }
        }
    }
    private static void initFields() {
        if (! myPref.contains(DEVICE_ID)) {     // if the pref data base is fresh
            Log.d("GlobalPref", "it is a new global preference");
            setField(DEVICE_ID, genName(DEVICE_ID_LENGTH));
            // init the other fields
            resetValueFields();
        }
    }
    public static void resetValueFields() {
        setField(UPLOADED_INDEX_1, 0);
        setField(TRAINED_INDEX_1, 0);
        setField(UPLOADED_INDEX_2, 0);
        setField(TRAINED_INDEX_2, 0);
    }
    public static String getName() {
        return getFieldString(DEVICE_ID);
    }
    public static String getFieldString(String fieldName){
        if (myPref == null) return null;
        return myPref.getString(fieldName, null);
    }
    public static Integer getFieldInt(String fieldName) {
        if (myPref == null) return null;
        if (! myPref.contains(fieldName)) return null; // enable a null return
        return myPref.getInt(fieldName, 0);
    }
    public static boolean setField(String fieldName, String value) {
        if (myPref == null) return false;
        myPref.edit()
                .putString(fieldName, value)
                .apply();
        return true;
    }
    public static boolean setField(String fieldName, int value) {
        if (myPref == null) return false;
        myPref.edit()
                .putInt(fieldName, value)
                .apply();
        return true;
    }
}
