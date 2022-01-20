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

    public List<Pair<double[], double[]>> readFrom(String fileName, TrainingPara tp) {
        // TODO read from several files instead of one file
        List<Pair<double[],double[]>> trainList = new ArrayList<>();
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
                    Pair<double[], double[]> newSample = string2double(line, tp);
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
        if (quickTraining() && trainList.size() > MAX_LOCAL_SET_SIZE)
            trainList = trainList.subList(trainList.size() - MAX_LOCAL_SET_SIZE, MAX_LOCAL_SET_SIZE);

        tp.DATASET_SIZE = trainList.size();
        tp.DATASET_NAME = fileName;
        return trainList;
    }

    private boolean quickTraining() {
        return LIMIT_TRAIN_SIZE;
    }

    private Pair<double[], double[]> tvPreprocess(double[] doubleList, TrainingPara tp) {
        int inSize = tp.MODEL_STRUCTURE.get(0);
        int outSize = tp.MODEL_STRUCTURE.get(tp.MODEL_STRUCTURE.size() - 1);
        double[] inList;
        double[] outList;
        boolean assertCond = doubleList.length > 3;
        if (!assertCond) {
            Log.d(TAG, "Error, inconsistent dataset format");
            return null;
        }
        inList = Arrays.copyOfRange(doubleList, 0, 2);
        if (inList[0] == 0.0 || inList[1] == 0.0)
            return null;
        double[] outListResource = Arrays.copyOfRange(doubleList, 3, 30 + 3);
        outList = MyUtils.powerMerge(outListResource, outListResource.length);
        return standardize(inList, outList, tp);
    }
    private Pair<double[], double[]> tvMultiPreprocess(double[] doubleList, TrainingPara tp) {
        int inSize = tp.MODEL_STRUCTURE.get(0);
        int outSize = tp.MODEL_STRUCTURE.get(tp.MODEL_STRUCTURE.size() - 1);
        double[] inList;
        double[] outList;
        boolean assertCond = doubleList.length > 3;
        if (!assertCond) {
            Log.d(TAG, "Error, inconsistent dataset format");
            return null;
        }
        inList = Arrays.copyOfRange(doubleList, 0, 2);
        if (inList[0] == 0.0 || inList[1] == 0.0)
            return null;
        double[] outListResource = Arrays.copyOfRange(doubleList, 3, 30 * 8 + 3);
        outList = MyUtils.powerMerge(outListResource, 30);
        return standardize(inList, outList, tp);
    }
    private Pair<double[], double[]> ltePreprocess(double[] doubleList, TrainingPara tp) {
        int inSize = tp.MODEL_STRUCTURE.get(0);
        int outSize = tp.MODEL_STRUCTURE.get(tp.MODEL_STRUCTURE.size() - 1);
        double[] inList;
        double[] outList;
        boolean assertCond = (inSize == outSize) && (doubleList.length == inSize + 3);
        if (!assertCond) {
            Log.d(TAG, "Error, inconsistent dataset format");
            return null;
        }
        inList = Arrays.copyOfRange(doubleList, 3, doubleList.length);
        outList = Arrays.copyOfRange(doubleList, 3, doubleList.length);
        return standardize(inList, outList, tp);
    }

    private Pair<double[], double[]> dummyPreprocess(double[] doubleList, TrainingPara tp) {
        int inSize = tp.MODEL_STRUCTURE.get(0);
        int outSize = tp.MODEL_STRUCTURE.get(tp.MODEL_STRUCTURE.size() - 1);
        double[] inList;
        double[] outList;
        boolean assertCond = (inSize + outSize) == doubleList.length;
        if (!assertCond) {
            Log.d(TAG, "Error, inconsistent dataset format");
            return null;
        }
        inList = Arrays.copyOfRange(doubleList, 0, inSize);
        outList = Arrays.copyOfRange(doubleList, inSize, doubleList.length);
        return standardize(inList, outList, tp);
    }

    private Pair<double[], double[]> standardize(double[] inList, double[] outList, TrainingPara tp) {
        if ((inList == null) || (outList == null)) return null;
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
//        Log.d(TAG, "input is " + array2string(inList));
//        Log.d(TAG, "output is " + array2string(outList));
        return new Pair<>(inList, outList);
    }

    private Pair<double[], double[]> string2double(String str, TrainingPara tp) {
        String[] strList = str.split(" ");
        double[] doubleList = new double[strList.length];
        for (int i=0; i<strList.length; i++)
            doubleList[i] = Double.parseDouble(strList[i]);
        // for dummy dataset
        if (USE_DUMMY_DATASET) {
            return dummyPreprocess(doubleList, tp);
        }
        if (tp.SEED_NAME.contains("lte")) {
            return ltePreprocess(doubleList, tp);
        }
        if (tp.SEED_NAME.contains("mtv")) {
            return tvMultiPreprocess(doubleList, tp);
        }
        if (tp.SEED_NAME.contains("tv")) {
            return tvPreprocess(doubleList, tp);
        }
        return null;
    }

    private String array2string(double[] arr) {
        String ret = "";
        for (int i = 0; i<arr.length; i++) {
            ret = ret + arr[i] + " ";
        }
        return ret;
    }
}
