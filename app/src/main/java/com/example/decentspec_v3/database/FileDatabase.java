package com.example.decentspec_v3.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.decentspec_v3.database.FileDatabaseMgr;

@Database(entities = {SampleFile.class}, version = 1, exportSchema = false)
public abstract class FileDatabase extends RoomDatabase {
    private static FileDatabase INSTANCE = null;
    private static final String DATABASE_NAME = "DecentSpec_Sampling_Record.db";
    public static FileDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (FileDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    FileDatabase.class,
                                    DATABASE_NAME).
                            build();
                }
            }
        }
        return INSTANCE;
    }
    protected FileDatabase() {
    }
    public abstract SampleFileDao mySampleFileDao();
}