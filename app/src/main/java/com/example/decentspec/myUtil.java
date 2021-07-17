package com.example.decentspec;

import java.security.SecureRandom;

public class myUtil {

    /* system config
     */
    static final boolean AVD_TEST = true; // test on a Android Virtual Device

    static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    static SecureRandom rnd = new SecureRandom();

    public static String genName(int len){
        StringBuilder sb = new StringBuilder(len);
        for(int i = 0; i < len; i++)
            sb.append(AB.charAt(rnd.nextInt(AB.length())));
        return sb.toString();
    }

    public static String transAddr(String old){
        if (AVD_TEST) {
            return old.replace("127.0.0.1", "10.0.2.2");
        }
        return old;
    }
}
