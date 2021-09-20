package com.example.decentspec_v3.database;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

// model viewer for activity to show database information lively
public class DBViewModel extends AndroidViewModel {
    private FileDatabaseMgr myDBMgr;
    public DBViewModel(Application app) {
        super(app);
        myDBMgr = new FileDatabaseMgr(app);
    }
    public LiveData<List<SampleFile>> pull() {
        return myDBMgr.getLiveList();
    }
}
