package com.johnfonte.blupoint;

import android.app.Activity;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.widget.Toast;

import com.johnfonte.blupoint.api.BluPointWeb;
import com.johnfonte.blupoint.object.Location;
import com.johnfonte.blupoint.object.Report;
import com.squareup.okhttp.ResponseBody;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.os.Handler;

import retrofit.Call;
import retrofit.Callback;
import retrofit.GsonConverterFactory;
import retrofit.Response;
import retrofit.Retrofit;

public class SweepService extends JobService {

    public static final String TAG = "SweepService";
    public static final String BASE_URL = "http://hackathon.shafeen.xyz:5000";
    public static final String BT_KEY = "11112222333344445555555555";
    public static final String WIRELESS_KEY = "Cambium";
    private BluetoothAdapter mBluetoothAdapter;
    BluPointWeb service;
    private final static String SHARED_PREFS_KEY = "BluPointPrefs";
    private final static String SHARED_PREFS_PERSONID_KEY = "BluPointPrefsPersonId";
    private Map<String, Integer> availableSensors = new HashMap<>();
    private final static Integer SCAN_PERIOD = 1100;
    BluetoothLeScanner scanner;
    final Handler handler = new Handler();
    private boolean bleEnabled = false;
    private Integer personId;
    private JobParameters params;

    @Override
    public boolean onStartJob(final JobParameters params) {
        Log.d(TAG, "Job Started");
        this.params = params;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences sp = getSharedPreferences(SHARED_PREFS_KEY, Activity.MODE_PRIVATE);
                    personId = sp.getInt(SHARED_PREFS_PERSONID_KEY, -1);
                    if(personId != -1) {
                        availableSensors.clear();
                        Retrofit retrofit = new Retrofit.Builder()
                                .addConverterFactory(GsonConverterFactory.create())
                                .baseUrl(BASE_URL)
                                .build();
                        service = retrofit.create(BluPointWeb.class);

                        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                        if (wifiManager.isWifiEnabled())
                        {
                            List<android.net.wifi.ScanResult> scanResults = wifiManager.getScanResults();
                            for(android.net.wifi.ScanResult scanResult : scanResults) {
                                if(scanResult.SSID.equals(WIRELESS_KEY)) {
//                                    Log.d(TAG, String.format("wifi %s", scanResult.toString()));
                                    availableSensors.put(scanResult.BSSID, scanResult.level);
                                }
                            }
                        }

                        final BluetoothManager bluetoothManager =
                                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                        mBluetoothAdapter = bluetoothManager.getAdapter();

                        // Ensures Bluetooth is available on the device and it is enabled.
                        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
                            mBluetoothAdapter.startDiscovery();
                            scanner = mBluetoothAdapter.getBluetoothLeScanner();
                            bleEnabled = true;
                            scanner.startScan(mLeScanCallback);
                        } else {
                            bleEnabled = false;
                        }
                        handler.postDelayed(runnable, SCAN_PERIOD);

                    } else {
                        jobFinished(params, false);
                    }
                } catch (Exception e) {
                    jobFinished(params, true);
                } finally {
                    jobFinished(params, false);
                }

            }
        }).run();
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "Job Stopped");

        return false;
    }

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if(bleEnabled) {
                scanner.stopScan(mLeScanCallback);
            }

            Report report = new Report();
            report.setId(personId);
            List<Location> locations = new ArrayList<>();

            for(String key : availableSensors.keySet()) {
                Location newLocation = new Location();
                newLocation.setBeaconId(key);
                newLocation.setStrength(availableSensors.get(key));
                locations.add(newLocation);
                Log.d(TAG, String.format("TAG: %s RSSI: %d", key, availableSensors.get(key)));
            }

            if(!locations.isEmpty()) {
                report.setLocation(locations);
                Call<ResponseBody> reporting = service.report(report, personId);
                reporting.enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Response response, Retrofit retrofit) {
                        Log.d(TAG, "Report Succeeded");
                        jobFinished(params, false);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.d(TAG, "Report Failed");
                        Log.d(TAG, String.format("%s", t.toString()));
                        Log.d(TAG, String.format("%s", t.getCause()));
                        jobFinished(params, false);
                    }
                });
            }

        }
    };

    private ScanCallback mLeScanCallback =
        new ScanCallback() {
            @Override
            public void onScanFailed(int errorCode) {
                Log.d(TAG, "onScanFailed: " + errorCode);
            }

            @Override
            public void onScanResult(int callbackType, ScanResult result) {

                Integer rssi = result.getRssi();
                if(result.getScanRecord() != null) {
                    byte[] bytes = result.getScanRecord().getManufacturerSpecificData(76);
                    if (bytes != null && bytes.length > 4) {
                        bytes = Arrays.copyOfRange(bytes, 4, 36);
                        String bytesHex = MainActivity.bytesToHex(bytes);
                        if (bytesHex.substring(0, 26).equals(BT_KEY)) {
                            // add it to send map
                            availableSensors.put(bytesHex.substring(0, 28), rssi);
//                            Log.d(TAG, String.format("tag: %s", bytesHex.substring(0, 28)));
                        }
//                    Log.d(TAG, String.format("onScanResult: %s length: %d substr: %s", bytesHex, bytes.length, bytesHex.substring(0, 25)));
                    }
                }
            }

        };
}

