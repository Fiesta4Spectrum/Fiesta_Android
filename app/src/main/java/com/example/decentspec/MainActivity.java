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

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity
                          implements MyRecyclerViewAdapter.ItemClickListener {

    private static final String SEED_NODE = "http://10.0.2.2:5000";
    private final String myName = myUtil.genName(10);
    private MyRecyclerViewAdapter adapter;
    private int currentMiner = -1;
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
        minerListArray.add("Press \"FETCH MINER LIST\" to get miner list ... ");
        RecyclerView minerList = findViewById(R.id.minerList);
        minerList.setLayoutManager(new LinearLayoutManager(this));
        minerList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        adapter = new MyRecyclerViewAdapter(this, minerListArray);
        adapter.setClickListener(this);
        minerList.setAdapter(adapter);
        // display the chain
        blockListArray.add("Press \"FETCH CHAIN\" to get the latest chain ... ");
        RecyclerView blockList = findViewById(R.id.blockList);
        blockList.setLayoutManager(new LinearLayoutManager(this));
        blockList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        adapter = new MyRecyclerViewAdapter(this, blockListArray);
        blockList.setAdapter(adapter);
    }

    @Override
    public void onItemClick(View view, int position) {
        showMsg("you click" + adapter.getItem(position) + " on row number " + position);
//        currentMiner = position;
    }

    public void fetchMinerList(View view) {
        // fetchMinerList from 10.0.0.2:5000
    }

    public void fetchChain(View view) {
        // fetchChainList from the selected miner
        if (currentMiner < 0) {
            showMsg("Please select miner first!");
            return;
        }
    }

    public void sendMsg(View view) {
        final EditText inputMsg = findViewById(R.id.inputMsg);
        String msg = inputMsg.getText().toString();
        if (currentMiner < 0) {
            showMsg("Please select miner first!");
            return;
        }
        if (msg.length() == 0) {
            showMsg("Empty Msg!");
        } else {
            showMsg("Gonna send msg: " + msg);
            inputMsg.setText("");
        }
    }

    public void flush(View view) {
        RequestQueue HTTPQueue = Volley.newRequestQueue(this);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, SEED_NODE + "/new_seed",
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
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