package com.crmaitland.exergamemonitor;

import android.app.ActivityManager;
import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.result.DailyTotalResult;
import com.google.android.gms.fitness.result.DataSourcesResult;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.concurrent.TimeUnit;

/**
 * Created by Cameron on 10/5/2017.
 */


public class ExergameBackgroundService extends Service implements GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener{

    private static final String TAG = "ExergameBackgroundService";
    private int sessionStartSteps;
    private int sessionEndSteps;
    private int sessionTotalSteps;
    private boolean appFlagged;


    private boolean currentlyProcessingLocation = false;
    private GoogleApiClient googleApiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        sessionEndSteps = 0;
        sessionStartSteps = 0;
        appFlagged=false;
        Log.e(TAG, "Created Service");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // if we are currently trying to get a location and the alarm manager has called this again,
        // no need to start processing a new location.
        Log.e(TAG, "Service Started");
        if (!currentlyProcessingLocation) {
            currentlyProcessingLocation = true;
            connectService();
        }



        //The Thread that checks for step count
        new Thread(new Runnable(){
            public void run() {
                // TODO Auto-generated method stub
                while(true)
                {
                    try {
                        Thread.sleep(5000);

                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    if(getForegroundApp() == "ExergameMonitor"){
                        appFlagged = true;
                        sessionStartSteps = getDailyStepCount();
                    }

                    while(appFlagged){
                        if(getForegroundApp()!="ExergameMonitor") {
                            appFlagged = false;
                            sessionEndSteps= getDailyStepCount();
                            sessionTotalSteps = sessionEndSteps - sessionStartSteps;
                            Log.e(TAG, "Steps:" + Integer.toString(sessionTotalSteps));
                        }
                    }
                }

            }
        }).start();


        return START_NOT_STICKY;
    }



    @Override
    public void onDestroy() {
        stopLocationUpdates();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }



    private void stopLocationUpdates() {
        if (googleApiClient != null && googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
    }

    /**
     * Called by Location Services when the request to connect the
     * client finishes successfully. At this point, you can
     * request the current location or start periodic updates
     */
    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "onConnectionFailed");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e(TAG, "GoogleApiClient connection has been suspend");
    }

    private void connectService(){
        if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS) {

            googleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            if (!googleApiClient.isConnected() || !googleApiClient.isConnecting()) {
                googleApiClient.connect();
            }
        } else {
            Log.e(TAG, "unable to connect to google play services.");
        }
    }

    /**getDailyStepCount()
     * Returns Daily Step Count
     * -1 if Couldn't Retrive in 10 seconds or no steps for the day
     */
    private int getDailyStepCount(){
        int steps = -1;
        PendingResult<DailyTotalResult> result = Fitness.HistoryApi.readDailyTotal(googleApiClient, DataType.AGGREGATE_STEP_COUNT_DELTA);
        DailyTotalResult totalResult = result.await(20, TimeUnit.SECONDS);
        if (totalResult.getStatus().isSuccess()) {
            DataSet totalSet = totalResult.getTotal();
            steps = totalSet.isEmpty() ? -1 : totalSet.getDataPoints().get(0).getValue(Field.FIELD_STEPS).asInt();
        }
        return steps;
    }

    /**getForeGroundApp()
     * Returns the Name of Foreground Application
     * From Users arslan haktic and Oliver Pearmain on StackOverflow
     * https://stackoverflow.com/questions/2166961/determining-the-current-foreground-application-from-a-background-task-or-service
     */
    private String getForegroundApp(){
        try{
            ActivityManager am = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
            // The first in the list of RunningTasks is always the foreground task.
            ActivityManager.RunningTaskInfo foregroundTaskInfo = am.getRunningTasks(1).get(0);
            String foregroundTaskPackageName = foregroundTaskInfo .topActivity.getPackageName();
            PackageManager pm = this.getPackageManager();
            PackageInfo foregroundAppPackageInfo = pm.getPackageInfo(foregroundTaskPackageName, 0);
            String foregroundTaskAppName = foregroundAppPackageInfo.applicationInfo.loadLabel(pm).toString();
            Log.e(TAG, "ForgroundTask: " + foregroundTaskAppName);
            return foregroundTaskAppName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Exception Thrown in getForegroundApp");
            e.printStackTrace();
            return "EXCEPTION_ERROR_IN_FIND_TASK";
        }

    }
}
