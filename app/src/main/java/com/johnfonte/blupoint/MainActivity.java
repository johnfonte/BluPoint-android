package com.johnfonte.blupoint;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.johnfonte.blupoint.api.BluPointWeb;
import com.johnfonte.blupoint.object.Location;
import com.johnfonte.blupoint.object.Report;
import com.johnfonte.blupoint.object.Token;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit.Call;
import retrofit.Callback;
import retrofit.GsonConverterFactory;
import retrofit.Response;
import retrofit.Retrofit;


public class MainActivity extends Activity {

    public static final String TAG = "MainActivity";
    private BluetoothAdapter mBluetoothAdapter;
    private final static int REQUEST_ENABLE_BT = 1;
    private final static String SHARED_PREFS_KEY = "BluPointPrefs";
    private final static String SHARED_PREFS_PERSONID_KEY = "BluPointPrefsPersonId";
    BluPointWeb service;
    private Integer personId;
    private Map<String, Integer> availableBLEs = new HashMap<>();
    private final static Integer SEND_PERIOD = 10000;
    private final static Integer SCAN_PERIOD = 3000;
    final Handler handler = new Handler();
    BluetoothLeScanner scanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.action_settings, Toast.LENGTH_SHORT).show();
            finish();
        }

        setupRetrofit();

        setupBluetooth();

        setupAccount();

        handler.postDelayed(scanRunnable, 1000);

    }

    private void setupRetrofit() {
        Retrofit retrofit = new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl("http://hackathon.shafeen.xyz:5000")
                .build();
        service = retrofit.create(BluPointWeb.class);

    }

    private void setupBluetooth() {
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        mBluetoothAdapter.startDiscovery();
        scanner = mBluetoothAdapter.getBluetoothLeScanner();
    }

    private void setupAccount() {
        SharedPreferences sp = getSharedPreferences(SHARED_PREFS_KEY, Activity.MODE_PRIVATE);
        int myIntValue = sp.getInt(SHARED_PREFS_PERSONID_KEY, -1);
        personId = myIntValue;

        if(myIntValue == -1) {
            AccountManager manager = (AccountManager) getSystemService(ACCOUNT_SERVICE);
            Account[] list = manager.getAccounts();
            String gmail = null;

            for(Account account: list)
            {
                if(account.type.equalsIgnoreCase("com.google"))
                {
                    gmail = account.name;
                    break;
                }
            }

            Log.d(TAG, gmail);
            if(gmail != null) {
                Call<Token> signedUp = service.signup(gmail);
                signedUp.enqueue(new Callback<Token>() {
                    @Override
                    public void onResponse(Response<Token> response, Retrofit retrofit) {
                        personId = response.body().getId();
                        Log.d(TAG, String.format("person id: %s", personId));
                        SharedPreferences sp = getSharedPreferences(SHARED_PREFS_KEY, Activity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sp.edit();
                        editor.putInt(SHARED_PREFS_PERSONID_KEY, personId);
                        editor.apply();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.d(TAG, String.format("person id: %s", t.getMessage()));

                    }
                });
            }
        } else {
            Log.d(TAG, String.format("person id: %s", personId));
        }

    }

    Runnable scanRunnable = new Runnable() {

        @Override
        public void run() {
            // Stops scanning after a pre-defined scan period.
            availableBLEs.clear();
            scanner.startScan(mLeScanCallback);
            handler.postDelayed(reportRunnable, SCAN_PERIOD);
        }
    };

    Runnable reportRunnable = new Runnable() {
        @Override
        public void run() {
            scanner.stopScan(mLeScanCallback);

            Report report = new Report();
            report.setId(personId);
            List<Location> locations = new ArrayList<>();
            for(String key : availableBLEs.keySet()) {
                Location newLocation = new Location();
                newLocation.setBeaconId(key);
                newLocation.setStrength(availableBLEs.get(key));
                locations.add(newLocation);
                Log.d(TAG, String.format("RSSI: %s TAG: %d", key, availableBLEs.get(key)));
            }

            WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            if (!wifiManager.isWifiEnabled())
            {
                Toast.makeText(getApplicationContext(), "Enabling WiFi", Toast.LENGTH_LONG).show();
                wifiManager.setWifiEnabled(true);
            }
            List<android.net.wifi.ScanResult> scanResults = wifiManager.getScanResults();
            for(android.net.wifi.ScanResult scanResult : scanResults) {
                if(scanResult.SSID.equals("Cambium")) {
                    Log.d(TAG, String.format("wifi %s", scanResult.toString()));
                    Location newLocation = new Location();
                    newLocation.setBeaconId(scanResult.BSSID);
                    newLocation.setStrength(scanResult.level);
                    locations.add(newLocation);
                }
            }

            report.setLocation(locations);

            if(!locations.isEmpty()) {
                Call<String> reporting = service.report(report, personId);
                reporting.enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(Response<String> response, Retrofit retrofit) {
                        Log.d(TAG, String.format("Report Succeeded"));
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.d(TAG, String.format("Report Failed"));
                        Log.d(TAG, String.format("%s", t.toString()));
                    }
                });
            }
            handler.postDelayed(scanRunnable, SEND_PERIOD);
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
                        String bytesHex = bytesToHex(bytes);
                        if (bytesHex.substring(0, 26).equals("11112222333344445555555555")) {
                            // add it to send map
                            availableBLEs.put(bytesHex.substring(0, 28), rssi);
//                        Log.d(TAG, String.format("tag: %s", bytesHex.substring(0, 28)));
                        }
//                    Log.d(TAG, String.format("onScanResult: %s length: %d substr: %s", bytesHex, bytes.length, bytesHex.substring(0, 25)));
                    }
                }
            }

        };

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
