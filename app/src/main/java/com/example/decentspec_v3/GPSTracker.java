package com.example.decentspec_v3;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.List;

import static com.example.decentspec_v3.IntentDirectory.GPS_LATI_FIELD;
import static com.example.decentspec_v3.IntentDirectory.GPS_LONGI_FIELD;
import static com.example.decentspec_v3.IntentDirectory.SERIAL_GPS_FILTER;

public class GPSTracker {

    private LocationManager mLocationManager = null;
    private LocationListener mLocationListener = null;
    private Location curLocation = null;
    private final Object curLocationLock = new Object();
    private boolean avail = false;
    private Context context = null;

    public GPSTracker(Context context) {
        this.context = context;
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        List<String> providerList = mLocationManager.getProviders(true);
        if (! providerList.contains(LocationManager.GPS_PROVIDER))
            avail = false;
        else if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            avail = false;
        else
            avail = true;
    }

    public boolean setupGPSListener(int interval_ms) {
        if (! avail)
            return false;
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return false;
        synchronized (curLocationLock) {    // the init value of gps
            curLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            broadcastGPS();
        }
        mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                synchronized (curLocationLock) {
                    curLocation = location;
                    broadcastGPS();
                }
            }
        };
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, interval_ms, 0, mLocationListener);
        return true;
    }

    public void broadcastGPS() {
        Intent intent;
        if (curLocation == null)
            intent = new Intent(SERIAL_GPS_FILTER)
                    .putExtra(GPS_LATI_FIELD, 0.0)
                    .putExtra(GPS_LONGI_FIELD, 0.0);
        else intent = new Intent(SERIAL_GPS_FILTER)
                .putExtra(GPS_LATI_FIELD, curLocation.getLatitude())
                .putExtra(GPS_LONGI_FIELD, curLocation.getLongitude());
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    public void cleanGPSListener() {
        if (mLocationListener != null && mLocationManager != null) {
            mLocationManager.removeUpdates(mLocationListener);
            curLocation = null;
            mLocationListener = null;
            broadcastGPS();
        }
    }

    public boolean isAvail() {
        return avail;
    }

}
