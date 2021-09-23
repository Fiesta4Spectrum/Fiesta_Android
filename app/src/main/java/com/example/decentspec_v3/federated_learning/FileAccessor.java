package com.example.decentspec_v3.federated_learning;

import android.content.Context;

import com.example.decentspec_v3.database.FileDatabase;

import org.nd4j.linalg.primitives.Pair;

import java.util.List;

public class FileAccessor {
    private Context context;
    public FileAccessor(Context context) {
        this.context = context;
    }
    public List<Pair<float[], float[]>> readFrom(String filename) {

    }
}
