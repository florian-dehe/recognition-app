package fr.florian.dehe.recognitionapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class MainActivity extends AppCompatActivity {

    private final static String PROCESS_NAME = "MainActivity";

    //Permissions
    private final boolean runningQorLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    private static final int PERMISSION_REQUEST_ACTIVITY_RECOGNITION = 45;
    private static final int EXTERNAL_STORAGE_PERMISSION_CODE = 55;
    private static final int PERMISSION_REQUEST_LOCATION = 50;

    // Location
    private DataRetrieverService locationRetrieverService;
    private Intent locationServiceIntent;

    // Write to file
    public static final String RECORD_FILENAME = "record.csv";
    private File externalStorageFolder;
    private File outputFile;

    private static boolean writeEverythingAtTheEnd = false;

    //UI
    private Button startTrackingButton;
    private Button stopTrackingButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!Util.isLocationEnabledOrNot(this)) {
            Util.showAlertLocation(
                    this,
                    "Enable GPS",
                    "Please enable GPS !",
                    "ok");
        }

        //UI view
        startTrackingButton = findViewById(R.id.start_tracking_button);
        startTrackingButton.setOnClickListener(view -> {
            startLocationUpdates();
            try {
                startWritingToFile();
            } catch (IOException e) {
                Log.e("MainActivity", e.toString());
            }
            startTrackingButton.setEnabled(false);
            stopTrackingButton.setEnabled(true);
        });
        stopTrackingButton = findViewById(R.id.stop_tracking_button);
        stopTrackingButton.setOnClickListener(view -> {
            stopService(locationServiceIntent);
            startTrackingButton.setEnabled(true);
            stopTrackingButton.setEnabled(false);
        });
        stopTrackingButton.setEnabled(false);
    }

    public static boolean shouldWriteEverythingAtTheEnd() {
        return writeEverythingAtTheEnd;
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        stopService(locationServiceIntent);

        super.onDestroy();
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[] {  Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION },
                    PERMISSION_REQUEST_LOCATION);
            return;
        }

        if (!activityRecognitionPermissionApproved()) {
            requestRequiredPermissions();
        }

        locationRetrieverService = new DataRetrieverService();
        locationServiceIntent = new Intent(this, locationRetrieverService.getClass());

        if (!Util.isMyServiceRunning(locationRetrieverService.getClass(), this)) {
            startService(locationServiceIntent);
            Toast.makeText(
                    this,
                    "Service started successfully !",
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            Toast.makeText(
                    this,
                    "Service already running !",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void startWritingToFile() throws IOException {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            return;

        ActivityCompat.requestPermissions(this,
                new String[] { Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE },
                EXTERNAL_STORAGE_PERMISSION_CODE);

        externalStorageFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        outputFile = new File(externalStorageFolder, RECORD_FILENAME);
        if (outputFile.exists()) {
            outputFile.delete();
        }

        FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);

        outputStreamWriter.write("time_ms,current_activity,lat,long");
        outputStreamWriter.flush();
        outputStreamWriter.close();

        Toast.makeText(this, "File written : " + outputFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
    }

    private boolean activityRecognitionPermissionApproved() {
        if (runningQorLater) {
            return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACTIVITY_RECOGNITION
            );
        }
        else {
            return true;
        }
    }

    private void requestRequiredPermissions() {
        Log.d(PROCESS_NAME, "Requesting permissions ...");
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                PERMISSION_REQUEST_ACTIVITY_RECOGNITION);
    }
}