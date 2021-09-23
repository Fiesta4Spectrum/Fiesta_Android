package com.example.decentspec_v3.federated_learning;

import android.content.Context;
import android.util.Log;

import com.example.decentspec_v3.database.FileDatabase;


import org.nd4j.linalg.primitives.Pair;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static com.example.decentspec_v3.Config.*;

public class FileAccessor {
    private final Context context;

    public FileAccessor(Context context) {
        this.context = context;
    }

    public List<Pair<float[], float[]>> readFrom(String fileName, TrainingPara tp) {
        int IN_SIZE = tp.MODEL_STRUCTURE.get(0);
        int OUT_SIZE = tp.MODEL_STRUCTURE.get(tp.MODEL_STRUCTURE.size() - 1);
        List<Pair<float[],float[]>> trainList = new ArrayList<>();
        try {
            FileInputStream fin = context.openFileInput(fileName);
            if (fin != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
                String line;
                line = reader.readLine();
                while (line != null) {
                    Pair<float[], float[]> newSample = string2float(line, IN_SIZE, OUT_SIZE, tp);
                    trainList.add(newSample);
                    line = reader.readLine();
                }

                fin.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // use a smaller training set
        trainList = trainList.subList(0, MAX_LOCAL_SET_SIZE);

        tp.DATASET_SIZE = trainList.size();
        tp.DATASET_NAME = fileName;
        return trainList;
    }

    private Pair<float[], float[]> string2float(String str, int inSize, int outSize, TrainingPara tp) {
        String[] strList = str.split(" ");
        float[] inList = new float[inSize];
        float[] outList = new float[outSize];
        if (inSize + outSize != strList.length) {
            Log.d("FLManager", "Error: incompatible model structure!");
            return null;
        }
        for (int i=0; i<inSize; i++) {
            inList[i] = (Float.parseFloat(strList[i]) - tp.DATASET_AVG.get(i)) / tp.DATASET_STD.get(i);
        }
        for (int i=inSize; i<inSize+outSize; i++) {
            outList[i-inSize] = (Float.parseFloat(strList[i]) - tp.DATASET_AVG.get(i)) / tp.DATASET_STD.get(i);
        }
        return new Pair<>(inList, outList);
    }
}
