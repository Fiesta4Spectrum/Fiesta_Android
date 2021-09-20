package com.example.decentspec_v3.database;

import android.content.Context;
import android.util.Log;

import java.util.List;

// singleton design
public class FileDatabaseMgr {

    private FileDatabase db = null;
    private SampleFileDao dao = null;

    public FileDatabaseMgr(Context context) {
        db = FileDatabase.getInstance(context);
        dao = db.mySampleFileDao();
    }

    // public API
    public List<SampleFile> getFileList() {
        return dao.getAllFiles();
    }
    public SampleFile createEntry(String fileName) {
        SampleFile newSampleFile = new SampleFile(fileName);
        Log.d("dbop", "will add the entry: " + newSampleFile.toString());
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
        Log.d("dbop", "will change the entry: " + file.toString());
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
