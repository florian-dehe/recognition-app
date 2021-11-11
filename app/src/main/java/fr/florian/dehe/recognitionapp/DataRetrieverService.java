package fr.florian.dehe.recognitionapp;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class DataRetrieverService extends Service {

    private static final long UPDATE_INTERVAL = 10 * 1000; // 10 sec
    private static final long FASTEST_INTERVAL = 2000; // 2 sec
    private static final String TRANSITIONS_RECEIVER_ACTION = "TRANSITIONS_RECEIVER_ACTION";
    public static final String LOCATION_RECEIVER_ACTION = "LOCATION_RECEIVER_ACTION";

    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationRequest locationRequest;

    private double currentLatLocation;
    private double currentLongLocation;

    private File outputFile;

    private String currentActivity = "Unknown";
    private List<ActivityTransition> activityTransitionList;
    private PendingIntent recognitionPendingIntent;

    private List<DataRecognitionEntry> dataRecognitionEntries;

    public static final String PROCESS_NAME = "DataRetrieverService";

    private ActivityTransitionReceiver activityTransitionReceiver;
    private LocationDataReceiver locationDataReceiver;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O)
            createNotificationChanel();
        else
            startForeground(1, new Notification());

        int[] activities = new int[]{
                DetectedActivity.IN_VEHICLE,
                DetectedActivity.ON_BICYCLE,
                DetectedActivity.RUNNING,
                DetectedActivity.STILL,
                DetectedActivity.WALKING
        };

        activityTransitionList = new ArrayList<>();

        for (int activity : activities) {
            activityTransitionList.add(new ActivityTransition.Builder()
                    .setActivityType(activity)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build());
            activityTransitionList.add(new ActivityTransition.Builder()
                    .setActivityType(activity)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build());
        }

        dataRecognitionEntries = new ArrayList<>();

        Intent recognitionIntent = new Intent(TRANSITIONS_RECEIVER_ACTION);
        recognitionPendingIntent = PendingIntent.getBroadcast(this, 0, recognitionIntent, 0);

        File externalStoragePublicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        outputFile = new File(externalStoragePublicDirectory, MainActivity.RECORD_FILENAME);

        //Broadcast receiver to listen for activity transitions
        activityTransitionReceiver = new ActivityTransitionReceiver();
        locationDataReceiver = new LocationDataReceiver();

        registerReceiver(activityTransitionReceiver, new IntentFilter(TRANSITIONS_RECEIVER_ACTION));
        registerReceiver(locationDataReceiver, new IntentFilter(LOCATION_RECEIVER_ACTION));

        requestLocationUpdates();
        enableActivityTransitions();
    }

    private void requestLocationUpdates() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return;

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);

                Location location = locationResult.getLastLocation();

                Intent broadcasted_intent = new Intent(LOCATION_RECEIVER_ACTION);
                broadcasted_intent.putExtra("lat", location.getLatitude());
                broadcasted_intent.putExtra("long", location.getLongitude());

                sendBroadcast(broadcasted_intent);
            }
        }, null);
    }

    private void storeData(String activity, double latitude, double longitude) {
        Date currentDate = Calendar.getInstance().getTime();
        DataRecognitionEntry dataRecognitionEntry = new DataRecognitionEntry(   currentDate.getTime(),
                                                                                activity,
                                                                                latitude,
                                                                                longitude);
        dataRecognitionEntries.add(dataRecognitionEntry);
    }

    private void writeToFile(DataRecognitionEntry entry) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(outputFile, true);
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);

        outputStreamWriter.write("\n");
        outputStreamWriter.write(entry.serialized());

        outputStreamWriter.close();
    }

    private void writeEverythingToFile() throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(outputFile, true);
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);

        for (int i=0; i < dataRecognitionEntries.size(); i++) {
            DataRecognitionEntry entry = dataRecognitionEntries.get(i);
            outputStreamWriter.write("\n");
            outputStreamWriter.write(entry.serialized());
        }

        outputStreamWriter.close();
    }

    private void requestWriteToFile() {
        try {
            Date currentTime = Calendar.getInstance().getTime();
            writeToFile(new DataRecognitionEntry(   currentTime.getTime(),
                                                    currentActivity,
                                                    currentLatLocation,
                                                    currentLongLocation));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void requestStoreData() {
        storeData(currentActivity, currentLatLocation, currentLongLocation);
    }

    private void enableActivityTransitions() {
        ActivityTransitionRequest request = new ActivityTransitionRequest(activityTransitionList);

        Task<Void> task = ActivityRecognition.getClient(this)
                .requestActivityTransitionUpdates(request, recognitionPendingIntent);

        task.addOnSuccessListener(
                result -> {
                    Log.d(PROCESS_NAME, "Transitions Api was successfully registered.");
                });

        task.addOnFailureListener(
                e -> {
                    Log.e(PROCESS_NAME, "Transitions Api could NOT be registered: " + e);
                });
    }

    private void disableActivityTransitions() {
        ActivityRecognition.getClient(this).removeActivityUpdates(recognitionPendingIntent)
                .addOnSuccessListener(unused -> {
                    Log.d(PROCESS_NAME, "Transition API unregistered !");
                })
                .addOnFailureListener(e -> {
                    Log.e(PROCESS_NAME, "Transitions could not be unregistered: " + e);
                });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (MainActivity.shouldWriteEverythingAtTheEnd()) {
            try {
                writeEverythingToFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        disableActivityTransitions();

        unregisterReceiver(activityTransitionReceiver);
        unregisterReceiver(locationDataReceiver);

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("restartservice");
        broadcastIntent.setClass(this, RestartBackgroundService.class);
        this.sendBroadcast(broadcastIntent);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChanel() {
        String NOTIFICATION_CHANNEL_ID = "fr.florian.dehe.recognitionapp";
        String channelName = "Background Service";
        NotificationChannel chan = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_NONE
        );
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(chan);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);

        Notification notification =  notificationBuilder.setOngoing(true)
                .setContentTitle("Activity Recognition app in background !")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(2, notification);
    }

    public class ActivityTransitionReceiver extends BroadcastReceiver {

        private static final String PROCESS_NAME = "ActivityTransitionReceiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!TextUtils.equals(TRANSITIONS_RECEIVER_ACTION, intent.getAction())) {
                Log.e(PROCESS_NAME, "Unsupported action : " + intent.getAction());
                return;
            }

            if (ActivityTransitionResult.hasResult(intent)) {
                ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
                Log.d(DataRetrieverService.PROCESS_NAME, "Transitions results : " + result.getTransitionEvents().size());
                for (ActivityTransitionEvent event : result.getTransitionEvents()) {
                    if (event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                        currentActivity = activityTypeToString(event.getActivityType());
                    }
                }
            }
            if (MainActivity.shouldWriteEverythingAtTheEnd())
                requestStoreData();
            else
                requestWriteToFile();
        }
    }

    public class LocationDataReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!TextUtils.equals(LOCATION_RECEIVER_ACTION, intent.getAction())) {
                Log.e("LocationDataReceiver", "Unsupported action : " + intent.getAction());
                return;
            }
            currentLatLocation = intent.getDoubleExtra("lat", -1);
            currentLongLocation = intent.getDoubleExtra("long", -1);

            if (MainActivity.shouldWriteEverythingAtTheEnd())
                requestStoreData();
            else
                requestWriteToFile();
        }
    }

    private static String activityTypeToString(int activityType) {
        switch (activityType) {
            case DetectedActivity.WALKING:
                return "Walking";
            case DetectedActivity.STILL:
                return "Still";
            case DetectedActivity.IN_VEHICLE:
                return "In Vehicle";
            case DetectedActivity.ON_BICYCLE:
                return "On Bicycle";
            case DetectedActivity.RUNNING:
                return "Running";
            default:
                return "Unknown";
        }
    }
}
