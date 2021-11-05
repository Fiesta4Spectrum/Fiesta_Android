package com.example.decentspec_v3;

public class Config {
    // global preference
    public static final int DEVICE_ID_LENGTH = 10;

    // sampling related
    public static final int GPS_UPDATE_INTERVAL = 1000; // 1s update

    // ML related
    public static final int ML_TASK_INTERVAL = 5000; // check the condition per 5s
    public static final int MAX_LOCAL_SET_SIZE = 4000; // too large the size will lead to too long time training
    public static final boolean USE_DUMMY_DATASET = true;
    public static final int MAX_PROGRESS_BAR = 5;
    public static final boolean ENABLE_GC_FREQ_LIMIT = true;
    public static final boolean NEW_GLOBAL_NEEDED = true;

    // api
    public static final String SEED_NODE = "http://api.decentspec.org:5000";
    public static final String API_GET_MINER = "/miner_peers";
    public static final String API_SEND_LOCAL = "/new_transaction";
    public static final String API_GET_GLOBAL = "/global_model";
}
