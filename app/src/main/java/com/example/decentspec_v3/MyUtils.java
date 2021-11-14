package com.example.decentspec_v3;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import org.apache.commons.lang3.ArrayUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.shade.jackson.core.JsonProcessingException;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.decentspec_v3.Config.SAMPLE_BIN_NUM;
import static com.example.decentspec_v3.Config.SAMPLE_END_SIGNAL;
import static com.example.decentspec_v3.Config.SAMPLE_PARA_BD;
import static com.example.decentspec_v3.Config.SAMPLE_PARA_CF;
import static com.example.decentspec_v3.Config.SAMPLE_RATE_INTERVAL;
import static com.example.decentspec_v3.Config.SAMPLE_START_SIGNAL;

public class MyUtils {
    static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    static SecureRandom rnd = new SecureRandom();

    public static String genName(int len){
        StringBuilder sb = new StringBuilder(len);
        for(int i = 0; i < len; i++)
            sb.append(AB.charAt(rnd.nextInt(AB.length())));
        return sb.toString();
    }
    public static long genTimestamp(){
        return System.currentTimeMillis() / 1000;
    }
    public static String genFileName(int center_freq, int bandwidth) {
        return String.format("CF%dMHz_BD%dMHz_%d.txt", center_freq / 1000000, bandwidth / 1000000, SAMPLE_BIN_NUM);
    }
    public static String genConfigData(int configIndex) {
        int center_freq = SAMPLE_PARA_CF[configIndex];
        int bandwidth = SAMPLE_PARA_BD[configIndex];
        return String.format("%s\n\r1\n\r%d\n\r%d\n\r%d\n\r%s\n\r", SAMPLE_START_SIGNAL, center_freq, bandwidth, SAMPLE_RATE_INTERVAL, SAMPLE_END_SIGNAL);
    }
}
