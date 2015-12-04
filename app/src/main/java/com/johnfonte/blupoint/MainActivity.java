package com.johnfonte.blupoint;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.johnfonte.blupoint.api.BluPointWeb;
import com.johnfonte.blupoint.object.Token;

import java.util.Arrays;
import java.util.HashMap;
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
    BluPointWeb service;
    private Integer personId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.action_settings, Toast.LENGTH_SHORT).show();
            finish();
        }

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);


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

        Retrofit retrofit = new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl("http://hackathon.shafeen.xyz:5000")
                .build();
        service = retrofit.create(BluPointWeb.class);

        Toast.makeText(this, R.string.action_settings, Toast.LENGTH_SHORT).show();
        handler.postDelayed(runnable, 1000);
        Call<Token> signedUp = service.signup("Wolf");
        signedUp.enqueue(new Callback<Token>() {
            @Override
            public void onResponse(Response<Token> response, Retrofit retrofit) {
                personId = response.body().getId();
                Log.d(TAG, String.format("person id: %s", personId));
            }

            @Override
            public void onFailure(Throwable t) {
                Log.d(TAG, String.format("person id: %s", t.getMessage()));

            }
        });

    }

    private Map<String, Integer> availableBLEs = new HashMap<>();

    private Integer SEND_PERIOD = 10000;
    private Integer SCAN_PERIOD = 3000;
    private boolean mScanning = true;
    final Handler handler = new Handler();
    final Handler scanHandler = new Handler();
    Runnable runnable = new Runnable() {

        @Override
        public void run() {
            try{
                //do your code here
                if (mScanning) {
                    // Stops scanning after a pre-defined scan period.
                    availableBLEs.clear();
                    mScanning = false;
                    BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
                    scanner.startScan(mLeScanCallback);
                    //also call the same runnable
                    handler.postDelayed(this, SCAN_PERIOD);
                } else {
                    mScanning = true;
                    BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
                    scanner.stopScan(mLeScanCallback);
                    for(String key : availableBLEs.keySet()) {
                        Log.d(TAG, String.format("RSSI: %s TAG: %d", key, availableBLEs.get(key)));
                    }

                    //also call the same runnable
                    handler.postDelayed(this, SEND_PERIOD);
                }

            }
            catch (Exception e) {
                // TODO: handle exception
            }
            finally{
                //also call the same runnable
                handler.postDelayed(this, SEND_PERIOD);
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
