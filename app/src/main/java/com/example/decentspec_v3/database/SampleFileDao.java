package com.example.decentspec_v3.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.decentspec_v3.database.FileDatabaseMgr;

import java.util.List;

@Dao
public interface SampleFileDao {

    @Query("Select * from SampleFile")
    List<SampleFile> getAllFiles();
    @Query("Select * from SampleFile")
    LiveData<List<SampleFile>> getLiveList();

    @Insert
    long insert(SampleFile newfile); // return the id of inserted entry

    @Update(onConflict = OnConflictStrategy.REPLACE) // TODO this seems didn't work
    void update(SampleFile newfile);

    @Query("UPDATE SampleFile SET stage = :stage WHERE id = :id")
    void update(int id, int stage);

    @Delete
    void delete(SampleFile newfile);
    /* TODO more diverse delete */
}