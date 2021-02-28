package com.schrold.uncanav;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;
import android.widget.Toast;
import android.speech.tts.TextToSpeech;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.here.android.mpa.common.PositioningManager;
import com.here.android.mpa.mapping.AndroidXMapFragment;

import org.w3c.dom.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    // permissions request code
    private final static int REQUEST_CODE_ASK_PERMISSIONS = 1234;

    /**
     * Permissions that need to be explicitly requested from end user.
     */
    private static final String[] REQUIRED_SDK_PERMISSIONS = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};

    // map fragment activity
    private MapFragmentActivity mapFragment = null;

    // Location method
    private PositioningManager.LocationMethod LOCATION_METHOD
            = PositioningManager.LocationMethod.GPS;

    // thread executor service
    public static ExecutorService executorService;

    // tts engine
    public static TextToSpeech textToSpeech;

    private boolean speakable = false;

    private final int DL_ACTIVITY_CODE = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler(new MyUncaughtExceptionHandler());

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitDiskReads()
                .permitDiskWrites()
                .build();
        StrictMode.setThreadPolicy(policy);

        executorService =  Executors.newFixedThreadPool(4);


        checkPermissions();
    }


    // handle return from activity result
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DL_ACTIVITY_CODE && resultCode == RESULT_OK) {
            // successfully downloaded map data
            System.out.println("updated");
            initializeMaps();
        }
    }

    // Initialize the application
    private void initialize() {
        // This will use external storage to save map cache data
        //deleteRecursive(new File(this.getExternalFilesDir(null), ".here-maps"));
        File externalCacheFile = new File(this.getExternalFilesDir(null), ".here-maps");
        com.here.android.mpa.common.MapSettings.setDiskCacheRootPath(externalCacheFile.getAbsolutePath());

        // initialize texttospeech in background thread
        executorService.execute(runTTS());

        // update map data and download it in background thread
        System.out.println("starting activity for dl");
        executorService.execute(runMapCheck());
    }

    private void initializeMaps() {
        // change view
        setContentView(R.layout.activity_main);
        // initialize map fragment on a new thread
        mapFragment = new MapFragmentActivity(this, getMapFragment(), LOCATION_METHOD);
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                mapFragment.initialize();
            }
        });
        // queue positioning service on a new thread
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                while (!mapFragment.isEngineInit()) {
                    // wait till mapEngine is initialized
                }
                System.out.println("Engine is initialized!");
                mapFragment.initPositioning();
            }
        });
    }

    /**
     * Checks the dynamically controlled permissions and requests missing permissions from end user.
     */
    protected void checkPermissions() {
        final List<String> missingPermissions = new ArrayList<String>();
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                for (int index = permissions.length - 1; index >= 0; --index) {
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                        // exit the app if one permission is not granted
                        Toast.makeText(this, "Required permission '" + permissions[index]
                                + "' not granted, exiting", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                }
                // all permissions were granted
                initialize();
                break;
        }
    }

    // Retrieves the map fragment from main layout
    public AndroidXMapFragment getMapFragment() {
        return (AndroidXMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapfragment);
    }


    // Stop listening to position when application is paused
    public void onPause() {
        System.out.println("pausing in main activity");
        if(textToSpeech != null && speakable) {
            textToSpeech.stop();
        }
        if (mapFragment != null) {
            mapFragment.pause();
        }
        super.onPause();
    }

    // Start listening to position when application is resumed
    public void onResume() {
        if (mapFragment != null && mapFragment.isEngineInit()) {
            mapFragment.resume();
        }
        super.onResume();
    }

    // Cleanup after application is closed
    public void onDestroy() {
        System.out.println("destroying main act");
        if(textToSpeech != null && speakable) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (mapFragment != null) {
            mapFragment.destroy();
            mapFragment = null;
        }
        executorService.shutdown();
        super.onDestroy();
    }

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
                                }
                            }});
                    }
                };
    }

    // returns a runnable that runs the map check
    private Runnable runMapCheck() {
        Intent intent = new Intent(this, DownloadMap.class);
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

        fileOrDirectory.delete();
    }

    // Runs on the initializtion of textToSpeech
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            speakable = true;
        }
    }
}