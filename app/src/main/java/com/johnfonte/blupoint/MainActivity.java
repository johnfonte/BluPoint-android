package com.johnfonte.blupoint;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.johnfonte.blupoint.api.BluPointWeb;
import com.johnfonte.blupoint.object.Token;

import retrofit.Call;
import retrofit.Callback;
import retrofit.GsonConverterFactory;
import retrofit.Response;
import retrofit.Retrofit;


public class MainActivity extends Activity {

    public static final String TAG = "MainActivity";
    private final static int REQUEST_ENABLE_BT = 1;
    private final static int REQUEST_ENABLE_WIFI = 2;
    public static final String BASE_URL = "http://hackathon.shafeen.xyz:5000";
    private final static String SHARED_PREFS_KEY = "BluPointPrefs";
    private final static String SHARED_PREFS_PERSONID_KEY = "BluPointPrefsPersonId";
    BluPointWeb service;
    private final static Integer SEND_PERIOD = 30000;
    private final static Integer mJobId = 1234567;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        setupRetrofit();
        setupAccount();
        setupWifi();
        setupBluetooth();

        // wrap your stuff in a componentName
        ComponentName mServiceComponent = new ComponentName(getApplicationContext(), SweepService.class);
// set up conditions for the job
        JobInfo task = new JobInfo.Builder(mJobId, mServiceComponent)
                .setPeriodic(SEND_PERIOD)
                .setPersisted(true)
                .build();
// inform the system of the job
        JobScheduler jobScheduler = (JobScheduler) getApplicationContext().getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(mJobId);
        int jobSuccessful = jobScheduler.schedule(task);
        if(jobSuccessful > 0) {
            Toast.makeText(this, R.string.job_started, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.job_didnot_start, Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "bluetooth enabled");
            }
        } else if(requestCode == REQUEST_ENABLE_WIFI) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "wifi enabled");
            }
        }
    }

    private void setupRetrofit() {
        Retrofit retrofit = new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl(BASE_URL)
                .build();
        service = retrofit.create(BluPointWeb.class);

    }

    private void setupWifi() {
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled())
        {
            Intent enableWifiIntent = new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS);
            startActivityForResult(enableWifiIntent, REQUEST_ENABLE_WIFI);
        }
    }

    private void setupBluetooth() {
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void setupAccount() {
        SharedPreferences sp = getSharedPreferences(SHARED_PREFS_KEY, Activity.MODE_PRIVATE);
        Integer personId = sp.getInt(SHARED_PREFS_PERSONID_KEY, -1);

        if(personId == -1) {
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
                        Integer personId = response.body().getId();
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
