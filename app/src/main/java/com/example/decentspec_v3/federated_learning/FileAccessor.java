package com.example.decentspec_v3.federated_learning;

import android.content.Context;
import android.util.Log;

import com.example.decentspec_v3.MyUtils;
import com.example.decentspec_v3.R;
import com.example.decentspec_v3.database.FileDatabase;


import org.nd4j.linalg.primitives.Pair;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.example.decentspec_v3.Config.*;

public class FileAccessor {
    private final Context context;
    private final static String TAG = "FileAccessor";

    public FileAccessor(Context context) {
        this.context = context;
    }

    public List<Pair<float[], float[]>> readFrom(String fileName, TrainingPara tp) {
        // TODO read from several files instead of one file
        List<Pair<float[],float[]>> trainList = new ArrayList<>();
        try {
            InputStream fin;
            if (USE_DUMMY_DATASET) {
                fin = context.getResources().openRawResource(R.raw.gps_power);
            } else
                fin = context.openFileInput(fileName);
            if (fin != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(fin));
                String line;
                line = reader.readLine();
                while (line != null) {
                    Pair<float[], float[]> newSample = string2float(line, tp);
                    if (newSample != null)
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

    private Pair<float[], float[]> tvPreprocess(float[] floatList, TrainingPara tp) {
        int inSize = tp.MODEL_STRUCTURE.get(0);
        int outSize = tp.MODEL_STRUCTURE.get(tp.MODEL_STRUCTURE.size() - 1);
        float[] inList;
        float[] outList;
        boolean assertCond = floatList.length > 3;
        if (!assertCond) {
            Log.d(TAG, "Error, inconsistent dataset format");
            return null;
        }
        inList = Arrays.copyOfRange(floatList, 0, 2);
        float[] outListResource = Arrays.copyOfRange(floatList, 3, 30 + 3);
        outList = MyUtils.powerMerge(outListResource, outListResource.length);
        return standardize(inList, outList, tp);
    }
    private Pair<float[], float[]> ltePreprocess(float[] floatList, TrainingPara tp) {
        int inSize = tp.MODEL_STRUCTURE.get(0);
        int outSize = tp.MODEL_STRUCTURE.get(tp.MODEL_STRUCTURE.size() - 1);
        float[] inList;
        float[] outList;
        boolean assertCond = (inSize == outSize) && (floatList.length == inSize + 3);
        if (!assertCond) {
            Log.d(TAG, "Error, inconsistent dataset format");
            return null;
        }
        inList = Arrays.copyOfRange(floatList, 3, floatList.length);
        outList = Arrays.copyOfRange(floatList, 3, floatList.length);
        return standardize(inList, outList, tp);
    }

    private Pair<float[], float[]> dummyPreprocess(float[] floatList, TrainingPara tp) {
        int inSize = tp.MODEL_STRUCTURE.get(0);
        int outSize = tp.MODEL_STRUCTURE.get(tp.MODEL_STRUCTURE.size() - 1);
        float[] inList;
        float[] outList;
        boolean assertCond = (inSize + outSize) == floatList.length;
        if (!assertCond) {
            Log.d(TAG, "Error, inconsistent dataset format");
            return null;
        }
        inList = Arrays.copyOfRange(floatList, 0, inSize);
        outList = Arrays.copyOfRange(floatList, inSize, floatList.length);
        return standardize(inList, outList, tp);
    }

    private Pair<float[], float[]> standardize(float[] inList, float[] outList, TrainingPara tp) {
        boolean assertCond = (inList.length + outList.length == tp.DATASET_AVG.size()) && (tp.DATASET_AVG.size() == tp.DATASET_STD.size());
        if (!assertCond) {
            Log.d(TAG, "Error, inconsistent standardize format");
            return null;
        }
        for (int i = 0; i < inList.length; i++) {
            inList[i] = (inList[i] - tp.DATASET_AVG.get(i)) / tp.DATASET_STD.get(i);
        }
        for (int i = 0; i < outList.length; i++) {
            outList[i] = (outList[i] - tp.DATASET_AVG.get(i + inList.length)) / tp.DATASET_STD.get(i + inList.length);
        }
        return new Pair<>(inList, outList);
    }

    private Pair<float[], float[]> string2float(String str, TrainingPara tp) {
        String[] strList = str.split(" ");
        float[] floatList = new float[strList.length];
        for (int i=0; i<strList.length; i++)
            floatList[i] = Float.parseFloat(strList[i]);
        // for dummy dataset
        if (USE_DUMMY_DATASET) {
            return dummyPreprocess(floatList, tp);
        }
        if (tp.SEED_NAME.contains("lte")) {
            return ltePreprocess(floatList, tp);
        }
        if (tp.SEED_NAME.contains("tv")) {
            return tvPreprocess(floatList, tp);
        }
        return null;
    }
}
