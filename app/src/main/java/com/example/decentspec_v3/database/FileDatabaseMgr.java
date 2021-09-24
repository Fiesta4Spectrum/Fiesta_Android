package com.example.decentspec_v3.database;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import java.util.List;

// access wrapper for database singleton
// because cant execute blocking access in main UI
public class FileDatabaseMgr {

    private static final String TAG = "Database";
    private FileDatabase db = null;
    private SampleFileDao dao = null;

    public FileDatabaseMgr(Context context) {
        db = FileDatabase.getInstance(context);
        dao = db.mySampleFileDao();
    }

    // public API
    public List<SampleFile> getFileList() {
        return dao.getAllFiles();                       // not actually used, it might be blocked
    }

    public LiveData<List<SampleFile>> getLiveList() {   // live list to enable view update
        return dao.getLiveList();
    }

    public SampleFile createEntry(String fileName) {
        SampleFile newSampleFile = new SampleFile(fileName);
//        Log.d(TAG, "insert: " + newSampleFile.toString());
        new Thread(new Runnable() {
            @Override
            public void run() {
                newSampleFile.id = (int) dao.insert(newSampleFile);
            }
        }).start();
        return newSampleFile;
    }

    public void markStage(SampleFile file, int stage) {
        file.stage = stage;
//        Log.d(TAG, "update: " + file.toString());
        new Thread(new Runnable() {
            @Override
            public void run() {
                dao.update(file);  //simple update seems not working
            }
        }).start();
    }
    public void deleteEntry(SampleFile file) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                dao.delete(file);
            }
        }).start();
    }
}
