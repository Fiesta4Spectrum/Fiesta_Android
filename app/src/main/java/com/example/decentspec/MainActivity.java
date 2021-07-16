package com.example.decentspec;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

public class MainActivity extends AppCompatActivity {

    private static final String SEED_NODE = "http://10.0.2.2:5000";
    private final String myName = myUtil.genName(10);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final TextView deviceId = findViewById(R.id.deviceId);
        deviceId.setText("ID:" + myName);
        // display name
    }

    public void fetchMinerList(View view) {
        // fetchMinerList from 10.0.0.2:5000
    }

    public void fetchChain(View view) {
        // fetchChainList from the selected miner
    }

    public void sendMsg(View view) {
        final EditText inputMsg = findViewById(R.id.inputMsg);
        String msg = inputMsg.getText().toString();
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
        Context context = getApplicationContext();
        int duration = Toast.LENGTH_SHORT;
        Toast.makeText(context, msg, duration).show();
    }
}