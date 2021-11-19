package com.example.decentspec_v3.federated_learning;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import org.apache.commons.lang3.ArrayUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.shade.jackson.core.JsonProcessingException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HelperMethods {
    // ML helper methods
    // try to translate between paramTable(dl4j) and stateDict(pyTorch)
    public static Map<String, INDArray> stateDict2paramTable(JSONObject stateDict) throws JsonProcessingException, JSONException {
        HashMap ref = new Gson().fromJson(stateDict.toString(), HashMap.class);
        int layerNum = ref.size()/2;
        Map<String, INDArray> result = new HashMap<>();
        for (Object keyObj: ref.keySet()) {
            String key = (String) keyObj;
            String jsonArray = stateDict.getString(key);
            int depth = key.contains("weight") ? 2:1;
            INDArray ndArray = Json2IDNArray(new Gson().fromJson(jsonArray, JsonElement.class), depth);
            if (depth > 1)
                ndArray = ndArray.transpose();  // difference between sd and pt
            result.put(rename_sd2pt(key, layerNum), ndArray);
        }
        // TODO add format check
        return result;
    }

    public static JSONObject paramTable2stateDict(Map<String, INDArray> paramTable) throws JsonProcessingException, JSONException {
        JSONObject ret = new JSONObject();
        int layerNum = paramTable.size()/2;
//        Log.d("dl4j-pytorch", "before: " + paramTable.toString().replace("\n", " "));
        for (String key: paramTable.keySet()) {     // for each layer's each para(wight, bias)
            int depth = key.contains("_W") ? 2:1;
            ArrayList<ArrayList<Double>> doubleBracket = INDArray2ArrayList(paramTable.get(key).transpose());
            if (depth > 1) ret.put(rename_pt2sd(key, layerNum), new JSONArray(new Gson().toJson(doubleBracket)));
            if (depth == 1) ret.put(rename_pt2sd(key, layerNum), new JSONArray(new Gson().toJson(removeBracket(doubleBracket))));
        }
//        Log.d("dl4j-pytorch", "after: " + ret.toString());
        // TODO add format check
        return ret;
    }

    private static ArrayList<Double> removeBracket(ArrayList<ArrayList<Double>> doubleBracket) {
        ArrayList<Double> ret = new ArrayList<>();
        for (ArrayList<Double> smallBracket: doubleBracket) {
            if (smallBracket.size() != 1) {
                Log.d("paraTable2stateDict", "wrong bias format !!!");
                return null;
            }
            ret.add(smallBracket.get(0));
        }
        return ret;
    }

    public static ArrayList<ArrayList<Double>> INDArray2ArrayList(INDArray ndArray) {
        ArrayList<ArrayList<Double>> ret = new ArrayList<>();
        long[] shape = ndArray.shape(); // normally it should be a [x,y] format
        int row = (int)shape[0];
        int col = (int)shape[1];
        for (int i = 0; i < row; i ++) {
            ArrayList<Double> new_row = new ArrayList<>();
            for (int j = 0; j < col; j ++) {
                new_row.add(ndArray.getDouble(i, j));
            }
            ret.add(new_row);
        }
        return ret;
    }


    public static String rename_pt2sd(String pt_name, int layerNum) {  // return a new string
        boolean isWeight = pt_name.contains("_W");
        boolean isBias = pt_name.contains("_b");
        String order = new String(pt_name);
        if (isWeight == isBias) {
            return null;
        }
        order = order.replace("_W", "")
                .replace("_b", "");
        if (Integer.parseInt(order) == layerNum-1)
            order = "ol";
        else
            order = "hidden." + order;
        if (isWeight)
            return order + ".weight";
        if (isBias)
            return order + ".bias";
        return null;
    }
    public static String rename_sd2pt(String sd_name, int layerNum) {  // return a new string
        boolean isWeight = sd_name.contains(".weight");
        boolean isBias = sd_name.contains(".bias");
        String order = new String(sd_name);
        if (isWeight == isBias) {
            return null;
        }
        order = order.replace(".weight", "")
                .replace(".bias", "")
                .replace("hidden.", "");
        if (order.equals("ol"))
            order = String.valueOf(layerNum - 1);
        if (isWeight)
            return order + "_W";
        if (isBias)
            return order + "_b";
        return null;
    }

    // Json helper func
    public static List<Integer> JSONArray2IntList(JSONArray JArray) throws JSONException {
        List<Integer> intList = new ArrayList<Integer>();
        for (int i = 0; i < JArray.length(); i++ )
            intList.add(JArray.getInt(i));
        return intList;
    }
    public static List<Double> JSONArray2DoubleList(JSONArray JArray) throws JSONException {
        List<Double> doubleList = new ArrayList<Double>();
        for (int i = 0; i < JArray.length(); i++ )
            doubleList.add((double)JArray.getDouble(i));
        return doubleList;
    }

    public static INDArray Json2IDNArray(JsonElement json_array, int depth) {
        if (0 >= depth)
            return null;

        ArrayList<Integer> shape = get_shape(json_array, depth);
        Integer[] shape_a = new Integer[shape.size()];
        shape.toArray(shape_a);
        int[] shape_int = ArrayUtils.toPrimitive(shape_a);

        INDArray result;
        if (1 == shape_int.length)
            result = Nd4j.create(shape_int[0]);
        else
            result = Nd4j.zeros(shape_int);

        int json_array_size = json_array.getAsJsonArray().size();
        for(int i = 0; i < json_array_size; ++i){
            if (1 == depth)
                result.putScalar(i, json_array.getAsJsonArray().get(i).getAsDouble());
            else
                result.putRow(i, Json2IDNArray(json_array.getAsJsonArray().get(i), depth - 1));
        }
        return result;
    }

    // get the shape of a json array
    private static ArrayList<Integer> get_shape(JsonElement json_array, int depth) {
        ArrayList<Integer> result = new ArrayList<>();
        if (1 == depth)
            result.add(json_array.getAsJsonArray().size());
        else if (1 < depth) {
            result.add(json_array.getAsJsonArray().size());
            result.addAll(get_shape(json_array.getAsJsonArray().get(0), depth - 1));
        }
        return result;
    }
}
