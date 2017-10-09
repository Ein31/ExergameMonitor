package com.crmaitland.exergamemonitor;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessStatusCodes;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Subscription;
import com.google.android.gms.fitness.result.DailyTotalResult;
import com.google.android.gms.fitness.result.ListSubscriptionsResult;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Time;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Cameron on 10/5/2017.
 */
public class ExergameBackgroundService extends Service{

    private static final String TAG = "ExergameBackgroundService";
    private int sessionStartSteps;
    private int sessionEndSteps;
    private int sessionTotalSteps;

    //For Writing CSV
    FileWriter fw;
    File root;
    File stepFile;

    private ResultCallback<Status> mSubscribeResultCallback;
    private ResultCallback<Status> mCancelSubscriptionResultCallback;
    private ResultCallback<ListSubscriptionsResult> mListSubscriptionsResultCallback;
    private GoogleApiClient mClient;

    @Override
    public void onCreate() {
        super.onCreate();
        sessionEndSteps = 0;
        sessionStartSteps = 0;

        /*
        root = Environment.getExternalStorageDirectory();
        stepFile = new File(root, "ExergameStepCount.csv");
        */

        buildFitnessClient();
        Log.e(TAG, "Created Service");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // if we are currently trying to get a location and the alarm manager has called this again,
        // no need to start processing a new location.
        Log.e(TAG, "Service Started");



        //The Thread that checks for step count
        new Thread(new Runnable(){
            public void run() {
                // TODO Auto-generated method stub
                boolean appFlagged = false;
                while(true)
                {
                    try {
                        Thread.sleep(5000);

                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    //Start Actual service Functions
                    Log.e(TAG, "App Flagged: " + String.valueOf(appFlagged));
                    if(exergameRunning()){
                        if(appFlagged == false){
                            //sessionStartSteps = getDailyStepCount();
                            Log.e(TAG, "Clash Just Brought to Foreground");
                            readData();
                        }
                        appFlagged = true;

                    }
                    else{
                        if(appFlagged == true){
                            Log.e(TAG, "Exited Clash");
                            readData();
                            /*//After Exit the App Report Step Count
                            sessionEndSteps= getDailyStepCount();
                            sessionTotalSteps = sessionEndSteps - sessionStartSteps;
                            Log.e(TAG, "Steps:" + Integer.toString(sessionTotalSteps));*/
                        }
                        appFlagged = false;
                    }
                }

            }
        }).start();


        return START_NOT_STICKY;
    }



    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent){
        return null;
    }


    private void buildFitnessClient() {
        // Create the Google API Client
        mClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.RECORDING_API)
                .addApi(Fitness.HISTORY_API)
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {

                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "Connected!!!");
                                // Now you can make calls to the Fitness APIs.  What to do?
                                // Subscribe to some data sources!
                                subscribe();
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                // If your connection to the sensor gets lost at some point,
                                // you'll be able to determine the reason and react to it here.
                                if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.w(TAG, "Connection lost.  Cause: Network Lost.");
                                } else if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.w(TAG, "Connection lost.  Reason: Service Disconnected");
                                }
                            }
                        }
                ).build();
        mClient.connect();
    }


    /**
     * Record step data by requesting a subscription to background step data.
     */
    public void subscribe() {
        // To create a subscription, invoke the Recording API. As soon as the subscription is
        // active, fitness data will start recording.
        Fitness.RecordingApi.subscribe(mClient, DataType.TYPE_STEP_COUNT_CUMULATIVE)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            if (status.getStatusCode()
                                    == FitnessStatusCodes.SUCCESS_ALREADY_SUBSCRIBED) {
                                Log.i(TAG, "Existing subscription for activity detected.");
                            } else {
                                Log.i(TAG, "Successfully subscribed!");
                            }
                        } else {
                            Log.w(TAG, "There was a problem subscribing.");
                        }
                    }
                });
    }


    /**
     * Read the current daily step total, computed from midnight of the current day
     * on the device's current timezone.
     */
    private class VerifyDataTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            long total = 0;

            PendingResult<DailyTotalResult> result = Fitness.HistoryApi.readDailyTotal(mClient, DataType.TYPE_STEP_COUNT_DELTA);
            DailyTotalResult totalResult = result.await(20, TimeUnit.SECONDS);
            if (totalResult.getStatus().isSuccess()) {
                DataSet totalSet = totalResult.getTotal();
                total = totalSet.isEmpty()
                        ? 0
                        : totalSet.getDataPoints().get(0).getValue(Field.FIELD_STEPS).asInt();
            } else {
                Log.w(TAG, "There was a problem getting the step count.");
                total=-1;
            }

            Log.i(TAG, "Total steps: " + total);

            return null;
        }
    }

    private void readData() {
        new VerifyDataTask().execute();
    }

    //ToDo
    private void writeStepCount(int steps, Date time){
        /*
        try {
            fw.append("String");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Error Writing to StepCount");
        }
        */


    }






    /**getForeGroundApp()
     * Returns the Name of Foreground Application
     * From Users arslan haktic and Oliver Pearmain on StackOverflow
     * https://stackoverflow.com/questions/2166961/determining-the-current-foreground-application-from-a-background-task-or-service
     */
    private boolean exergameRunning() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningAppProcessInfo = am.getRunningAppProcesses();

        for (int i = 0; i < runningAppProcessInfo.size(); i++) {
            if (runningAppProcessInfo.get(i).processName.equals("com.supercell.clashroyale")) {
                return true; // Do your stuff here.
            }
        }

        return false;
        }
}
