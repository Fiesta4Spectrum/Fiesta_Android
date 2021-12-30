package com.example.decentspec_v3.federated_learning;

import android.content.Context;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.decentspec_v3.GlobalPrefMgr;
import com.example.decentspec_v3.MyUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nd4j.shade.jackson.core.JsonProcessingException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;

import static com.example.decentspec_v3.Config.*;

public class HTTPAccessor {
    private final RequestQueue HTTPQueue;
    private final String SEED_ADDR;
    private volatile boolean threadDone; // thread flag need to be volatile to avoid cache
                                         // no need to add lock since only one writer and one reader
    private volatile boolean responded;
    private ArrayList<String> minerHistory = new ArrayList<>();
    private double reward = 0.0;

    public HTTPAccessor(Context context, String seedAddr) {
        this.HTTPQueue = Volley.newRequestQueue(context);
        this.SEED_ADDR = seedAddr;
    }

    public void testNull(TrainingPara tp) {
        if (tp.MINER_LIST != null)
            Log.d("HTTP", "miner_list not null");
        else Log.d("HTTP", "miner_list is null");
    }

    public boolean fetchMinerList(TrainingPara tp) {
        tp.MINER_LIST = new ArrayList<>();
        threadDone = false;
        responded = false;
        StringRequest stringRequest = new StringRequest(Request.Method.GET, SEED_ADDR + API_GET_MINER,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject jsonRsp = new JSONObject(response);
                            JSONArray peers = jsonRsp.getJSONArray("peers");
                            for (int i=0; i < peers.length(); i++) {
                                if (tp.MINER_LIST == null)
                                    tp.MINER_LIST = new ArrayList<>();
                                tp.MINER_LIST.add(peers.getString(i));
                            }
                            if (SHUFFLE_MINER_LIST)
                                Collections.shuffle(tp.MINER_LIST);
                            minerHistory = tp.MINER_LIST;
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        threadDone = true;
                        responded = true;
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d("HTTP", error.toString());
                        tp.MINER_LIST = minerHistory;
                        threadDone = true;
                        responded = false;
                    }
                });
        HTTPQueue.add(stringRequest);
        join();
        return tp.MINER_LIST.size() != 0;
    }

    public double fetchReward(String myId) {
        threadDone = false;
        responded = false;
        reward = 0.0;
        StringRequest stringRequest = new StringRequest(Request.Method.GET, SEED_ADDR + API_GET_REWARD + myId,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject jsonRsp = new JSONObject(response);
                            reward = jsonRsp.getDouble("reward");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        threadDone = true;
                        responded = true;
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d("HTTP", error.toString());
                        threadDone = true;
                        responded = false;
                    }
                });
        HTTPQueue.add(stringRequest);
        join();
        return reward;
    }

    public boolean getLatestGlobal(String serverAddr, TrainingPara tp) {
        threadDone = false;
        responded = false;
        StringRequest stringRequest = new StringRequest(Request.Method.GET, serverAddr + API_GET_GLOBAL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject jsonRsp = new JSONObject(response);

                            JSONObject weight_json = jsonRsp.getJSONObject("weight");
                            tp.GLOBAL_WEIGHT = HelperMethods.stateDict2paramTable(weight_json);
//                            Log.d("HTTP", "[get global] " + weight_json.toString());

                            JSONObject preproc_json = jsonRsp.getJSONObject("preprocPara");
                            tp.DATASET_AVG = HelperMethods.JSONArray2DoubleList(preproc_json.getJSONArray("avg"));
                            tp.DATASET_STD = HelperMethods.JSONArray2DoubleList(preproc_json.getJSONArray("std"));

                            JSONObject train_json = jsonRsp.getJSONObject("trainPara");
                            // TODO package train paras into a struct object
                            tp.BATCH_SIZE = train_json.getInt("batch");
                            tp.LEARNING_RATE = train_json.getDouble("lr");
                            tp.EPOCH_NUM = train_json.getInt("epoch");

                            JSONObject sample_json = jsonRsp.getJSONObject("samplePara");
                            tp.SAMPLE_CENTER_FREQ = sample_json.getInt("center_freq");
                            tp.SAMPLE_BANDWIDTH = sample_json.getInt("bandwidth");

                            tp.MODEL_STRUCTURE = HelperMethods.JSONArray2IntList(jsonRsp.getJSONArray("layerStructure"));
                            tp.BASE_GENERATION = jsonRsp.getInt("generation");
                            tp.SEED_NAME = jsonRsp.getString("seed_name");
                        } catch (JSONException | JsonProcessingException e) {
                            e.printStackTrace();
                        }
                        responded = true;
                        threadDone = true;
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d("HTTP", error.toString());
                        responded =false;
                        threadDone = true;
                    }
                });
        HTTPQueue.add(stringRequest);
        join();
        return responded;
    }

    public boolean sendTrainedLocal(String serverAddr, int size, double delta_loss, double end_loss, TrainingPara tp, JSONObject localWeight) throws JSONException {
            /*
                MLdata = {
                    'stat' : {  'size' : size,
                                'lossDelta' : lossDelta,
                                'trainedLoss' : end_loss },
                    'weight' : weight
                }

                data = {
                    'author' : myName,
                    'content' : MLdata,
                    'timestamp' : genTimestamp(),
                    'type' : 'localModelWeight',
                    'plz_spread' : 1,
                    'seed_name' : string,
                }
            */
        threadDone = false;
        responded = false;
        String url = serverAddr + API_SEND_LOCAL;
        JSONObject MLData = new JSONObject();
        JSONObject MLData_stat = new JSONObject();
        MLData_stat.put("size", size);
        MLData_stat.put("lossDelta", delta_loss);
        MLData_stat.put("trainedLoss", end_loss);
        MLData.put("stat", MLData_stat);
        MLData.put("weight", localWeight);
        MLData.put("base_gen", tp.BASE_GENERATION);

        JSONObject jsonBody = new JSONObject();
        jsonBody.put("author", GlobalPrefMgr.getName());
        jsonBody.put("content", MLData);
        jsonBody.put("type", "localModelWeight");
        jsonBody.put("timestamp", MyUtils.genTimestamp());
        jsonBody.put("plz_spread", 1);
        jsonBody.put("seed_name", tp.SEED_NAME);
        final String requestBody = jsonBody.toString();
//        Log.d("HTTP", "[upload global] " + requestBody);

        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        threadDone = true;
                        responded = true;
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d("HTTP", error.toString());
                        threadDone = true;
                        responded = false;
                    }
                })
        {
            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }

            @Override
            public byte[] getBody() throws AuthFailureError {
                return requestBody == null ? null : requestBody.getBytes(StandardCharsets.UTF_8);
            }
        };
        HTTPQueue.add(stringRequest);
        join();
        return responded;
    }
    public void join() {
        while (! threadDone); // spin here to synchronize
    }
}
