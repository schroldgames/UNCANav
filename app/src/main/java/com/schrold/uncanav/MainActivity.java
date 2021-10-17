package com.schrold.uncanav;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.WindowManager;
import android.widget.Toast;
import android.speech.tts.TextToSpeech;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // Request code for application permissions
    private final static int REQUEST_CODE_ASK_PERMISSIONS = 1234;

    // Activity result code for MapDownloadActivity
    private final int DL_ACTIVITY_CODE = 5679;

    /**
     * Permissions that need to be explicitly requested from end user.
     */
    private static final String[] REQUIRED_SDK_PERMISSIONS = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};

    // Map fragment activity
    private MapFragmentView mapFragmentView = null;

    // Executor for multithreading processes
    public static ExecutorService executorService;

    // Engine to be used with Google's Text-to-Speech
    public static TextToSpeech textToSpeech;

    // Flag for the initialization of TTS engine
    private boolean canSpeak = false;

    /**
     * Called when application is started.
     * @param savedInstanceState unused
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep the screen on when in the app
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Setting up multithreading
        Thread.setDefaultUncaughtExceptionHandler(new MyUncaughtExceptionHandler());
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitDiskReads()
                .permitDiskWrites()
                .permitNetwork()
                .build();
        StrictMode.setThreadPolicy(policy);

        // Creating a pool of 4 threads
        executorService =  Executors.newFixedThreadPool(4);

        // Check the user's permissions, then proceed
        checkPermissions();
    }

    /**
     * Handles result returned by requested activity.
     * @param requestCode the request code for the activity
     * @param resultCode the result code returned by the activity
     * @param data intent data returned by activity
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DL_ACTIVITY_CODE && resultCode == RESULT_OK) {
            // Successfully downloaded map data
            System.out.println("dl completed successfully");
            // Initialize the map for the user
            initializeMaps();
        } else {
            // Map data failed to download
            System.out.println("map data failed to dl");
            finish();
        }
    }

    /**
     * Initialize the application.
     */
    private void initialize() {
        //deleteRecursive(new File(this.getExternalFilesDir(null), ".here-maps"));

        // This will use external storage to save map cache data
        File externalCacheFile = new File(this.getExternalFilesDir(null), ".here-maps");
        com.here.android.mpa.common.MapSettings.setDiskCacheRootPath(externalCacheFile.getAbsolutePath());

        // Initialize TTS engine in background thread
        executorService.execute(runTTS());

        // Update and download map data in background thread
        executorService.execute(runMapDownload());
    }

    /**
     * Initialize the map view.
     */
    private void initializeMaps() {
        // Set the content view to main activity
        setContentView(R.layout.activity_main);

        // Initialize map fragment on a new thread
        mapFragmentView = new MapFragmentView(this);
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                mapFragmentView.initialize();
            }
        });
    }

    /**
     * Checks the dynamically controlled permissions and requests missing permissions from end user.
     */
    protected void checkPermissions() {
        final List<String> missingPermissions = new ArrayList<>();
        // check all required dynamic permissions
        for (final String permission : REQUIRED_SDK_PERMISSIONS) {
            final int result = ContextCompat.checkSelfPermission(this, permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (!missingPermissions.isEmpty()) {
            // request all missing permissions
            final String[] permissions = missingPermissions
                    .toArray(new String[missingPermissions.size()]);
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_ASK_PERMISSIONS);
        } else {
            final int[] grantResults = new int[REQUIRED_SDK_PERMISSIONS.length];
            Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED);
            onRequestPermissionsResult(REQUEST_CODE_ASK_PERMISSIONS, REQUIRED_SDK_PERMISSIONS,
                    grantResults);
        }
    }

    /**
     * Called when all permissions have been checked by the user. If user has granted all required
     * permissions, the application continues.
     * @param requestCode permissions request code
     * @param permissions permissions to be requested
     * @param grantResults array of permission results
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_ASK_PERMISSIONS) {
            for (int index = permissions.length - 1; index >= 0; --index) {
                if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                    // exit the app if one permission is not granted
                    Toast.makeText(this, "Required permission '" + permissions[index]
                            + "' not granted, exiting", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
            // all permissions were granted, continue with application
            initialize();
        }
    }

    /**
     * Called when application is paused.
     */
    @Override
    protected void onPause() {
        // Stop TTS engine
        if(textToSpeech != null && canSpeak) {
            textToSpeech.stop();
        }

        // Stop positioning updates
        if (mapFragmentView != null) {
            mapFragmentView.pause();
        }
        super.onPause();
    }

    /**
     * Called when application is resumed.
     */
    @Override
    protected void onResume() {
        super.onResume();
        // Resume positioning updates
        if (mapFragmentView != null && mapFragmentView.isEngineInit()) {
            mapFragmentView.resume();
        }
    }

    /**
     * Cleans up after application is closed.
     */
    @Override
    protected void onDestroy() {
        // Shuts down TTS engine
        if(textToSpeech != null && canSpeak) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        // Stops positioning updates
        if (mapFragmentView != null) {
            mapFragmentView.destroy();
            mapFragmentView = null;
        }
        // Shuts down thread executor service
        executorService.shutdown();
        super.onDestroy();
    }

    /**
     * Returns a runnable object for instantiating the TTS engine.
     */
    private Runnable runTTS() {
        return
                new Runnable() {
                    @Override
                    public void run() {
                        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                            @Override
                            public void onInit(int status) {
                                if(status != TextToSpeech.ERROR) {
                                    textToSpeech.setLanguage(Locale.US);
                                    canSpeak = true;
                                }
                            }});
                    }
                };
    }

    /**
     * Returns a runnable object for downloading map data.
     */
    private Runnable runMapDownload() {
        final Intent intent = new Intent(this, MapDownloadActivity_bbox.class);
        return
                new Runnable() {
                    @Override
                    public void run() {
                        startActivityForResult(intent, DL_ACTIVITY_CODE);
                    }
                };
    }

    /** For debugging purposes. */
    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

    }


}