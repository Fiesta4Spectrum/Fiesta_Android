package com.example.decentspec;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String SEED_NODE = "http://10.0.2.2:5000";
    private final String myName = myUtil.genName(10);
    private MyRecyclerViewAdapter minerAdapter, blockAdapter;
    private boolean haveMiner = false;
    private ArrayList<String> blockListArray = new ArrayList<>();
    private ArrayList<String> minerListArray = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // display device id
        final TextView deviceId = findViewById(R.id.deviceId);
        deviceId.setText("ID:" + myName);

        // display miner list
        minerListArray.add("Press \"FETCH MINER LIST\" to get miner list.");
        RecyclerView minerList = findViewById(R.id.minerList);
        minerList.setLayoutManager(new LinearLayoutManager(this));
        minerList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        minerAdapter = new MyRecyclerViewAdapter(this, minerListArray);
        minerList.setAdapter(minerAdapter);
        // display the chain
        blockListArray.add("Press \"FETCH CHAIN\" to get the latest chain.");
        RecyclerView blockList = findViewById(R.id.blockList);
        blockList.setLayoutManager(new LinearLayoutManager(this));
        blockList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        blockAdapter = new MyRecyclerViewAdapter(this, blockListArray);
        blockList.setAdapter(blockAdapter);

    }

    public void fetchMinerList(View view) {
        RequestQueue HTTPQueue = Volley.newRequestQueue(this);
        minerListArray.clear();
        minerAdapter.rstSelect();
        haveMiner = false;
        StringRequest stringRequest = new StringRequest(Request.Method.GET, SEED_NODE + "/miner_peers",
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject jsonRsp = new JSONObject(response);
                            JSONArray peers = jsonRsp.getJSONArray("peers");
                            for (int i=0; i < peers.length(); i++) {
                                minerListArray.add(myUtil.transAddr(peers.getString(i)));
                            }
                            showMsg("MinerList Received!");
                        } catch (JSONException e) {
                            e.printStackTrace();
                            showMsg("bad respond");
                        }
                        if (minerListArray.size() == 0) {
                            minerListArray.add("Press \"FETCH MINER LIST\" to get miner list.");
                            haveMiner = false;
                        } else {
                            haveMiner = true;
                        }
                        minerAdapter.notifyDataSetChanged();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        if (error.getClass().equals(TimeoutError.class)) {
                            showMsg("Timeout!");
                        } else {
                            showMsg(error.toString());
                        }
                        minerListArray.add("Press \"FETCH MINER LIST\" to get miner list.");
                        haveMiner = false;
                        minerAdapter.notifyDataSetChanged();
                    }
                });
        HTTPQueue.add(stringRequest);
    }

    public void fetchChain(View view) {
        // fetchChainList from the selected miner
        if (minerAdapter.getSelect() < 0 || !haveMiner) {
            showMsg("Please select a valid miner first!");
            return;
        }
    }

    public void sendMsg(View view) {
        final EditText inputMsg = findViewById(R.id.inputMsg);
        String msg = inputMsg.getText().toString();
        if (minerAdapter.getSelect() < 0 || !haveMiner) {
            showMsg("Please select a valid miner first!");
            return;
        }
        if (msg.length() == 0) {
            showMsg("Empty Msg!");
        } else {
            showMsg("Gonna send msg: " + msg);
            String url = minerListArray.get(minerAdapter.getSelect()) + "/new_transaction";
            inputMsg.setText("");
        }
    }

    public void flush(View view) {
        RequestQueue HTTPQueue = Volley.newRequestQueue(this);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, SEED_NODE + "/new_seed",
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        showMsg("Reseed Succeed!");
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        if (error.getClass().equals(TimeoutError.class)) {
                            showMsg("Timeout!");
                        } else {
                            showMsg(error.toString());
                        }
            }
        });
        HTTPQueue.add(stringRequest);
    }

    private void showMsg(String msg) {
        int duration = Toast.LENGTH_SHORT;
        Toast.makeText(this, msg, duration).show();
    }
}