package com.example.admin.gcmdemo;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

public class DemoActivity extends Activity{


    public String TAG = "GCM Demo";

    private TextView mDisplay;
    private Context context;
    private GoogleCloudMessaging gcm;
    private String regid;
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private static  final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";

    //Put Sender Id Here . Sender Id obtained From Google Developer Console.
    private String SENDER_ID = "";
    private String SERVER_URL = "";
    private static final int BACKOFF_MILLI_SECONDS = 2000;
    private static final int MAX_ATTEMPTS = 5;
    private static final Random random = new Random();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mDisplay = (TextView) findViewById(R.id.display);

        context = getApplicationContext();

        if(checkPlayServices()){
            gcm = GoogleCloudMessaging.getInstance(this);
            regid = getRegistrationId(context);

            if (regid.isEmpty()){
                registerInBackground();
            }
        } else {
            Log.i(TAG, "No valid Google Play Services APK found");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPlayServices();
    }

    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS){
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)){
                GooglePlayServicesUtil.getErrorDialog(resultCode, this, PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(TAG, "This device is not supported ");
                finish();
            }
            return false;
        }
        //Return true if Google Play Service Available
        return true;
    }

    private void storeRegistrationId(Context context, String regId){
        final SharedPreferences prefs = getGcmPreferences(context);
        int appVersion = getAppVersion(context);
        Log.i(TAG, "Saving RegId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();

    }

    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    private SharedPreferences getGcmPreferences(Context context) {
        return getSharedPreferences(DemoActivity.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGcmPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID,"");
        if (registrationId.isEmpty()){
            Log.i(TAG,"Registration Not found. Device Needs To Register To GCm");
            return "";
        }

        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion){
            Log.i(TAG, "App version Changed. Updating Registeration Id");
            return "";
        }
        return registrationId;
    }

    private void registerInBackground() {
        new AsyncTask<Void,Void,String>() {
            @Override
            protected String doInBackground(Void... params) {
                String msg = "";
                try {
                    if (gcm == null){
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    regid = gcm.register(SENDER_ID);
                    msg = "Device registered, registration ID=" + regid;

                    Log.i("GCM ID", msg);

                    sendRegistrationIdToBackend(regid);
                    storeRegistrationId(context, regid);
                } catch (IOException ex){
                    msg = "Error 123: " + ex.getMessage();
                }
                return msg;
            }

            @Override
            protected void onPostExecute(String msg) {
                mDisplay.append(msg + "\n");

            }
        }.execute();
    }

    private void sendRegistrationIdToBackend(String regId) {
        String serverURL = SERVER_URL;
        Map<String, String> params = new HashMap<String, String>();

        params.put("regId", regId);
        params.put("name", "zorro");
        params.put("email", "zorro_kty@erer.oru");



        for (int i=0; i <= MAX_ATTEMPTS; i++){
            Log.i(TAG, "Attempt # " + i  + " to register");

                post(serverURL, params,i);
        }

    }

    private void post(String endPoint, Map<String, String> params, int attempt) {

        long backoff = BACKOFF_MILLI_SECONDS + random.nextInt(1000);

        URL url;

        try {
            url =new URL(endPoint);

        } catch (MalformedURLException e){
            throw new IllegalArgumentException("invalid url: " + endPoint);
        }

        StringBuilder bodyBuilder = new StringBuilder();
        Iterator<Entry<String, String>> iterator = params.entrySet().iterator();

        //Construct the post body usingn parameters
        while (iterator.hasNext()){
            Entry<String, String> param = iterator.next();
            bodyBuilder.append(param.getKey())
                    .append('=')
                    .append(param.getValue());
            if (iterator.hasNext()){
                bodyBuilder.append('&');
            }
        }
        String body = bodyBuilder.toString();
        Log.v(TAG, "Posting: " + body + " to " + url);
        byte[] bytes = body.getBytes();
        HttpURLConnection conn = null;
        try {
            Log.e("URL ", "> " + url);
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application-x-www-form-urlencoded;charset=UTF-8");

            //post the request
            OutputStream out = conn.getOutputStream();
            out.write(bytes);
            out.close();
            //handle the response
            int status = conn.getResponseCode();
            if (status != 200){
                throw new IOException("Post failed with error code " +  status);
            }
            if (status == 200){
                Log.v(TAG, "Device Registered to server");
            }
        } catch (IOException e){
            Log.e(TAG, "Failed to register on attempt " + attempt + ":" + e);
            try {
                Log.d(TAG, "Sleeping for " + backoff + " ms before retry");
                Thread.sleep(backoff);
                backoff *= 5;
            } catch (InterruptedException e1){
                Log.d(TAG, "Thread interrupted: abort remaining retries!");
                Thread.currentThread().interrupt();
                return;
            }

        } finally {
                if (conn != null){
                    conn.disconnect();
                }
        }

    }


}