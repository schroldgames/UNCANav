package com.schrold.uncanav;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.HandlerCompat;
import androidx.fragment.app.FragmentActivity;

import com.here.android.mpa.common.ApplicationContext;
import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.common.GeoPosition;
import com.here.android.mpa.common.LocationDataSourceHERE;
import com.here.android.mpa.common.MapEngine;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.common.PositioningManager;
import com.here.android.mpa.mapping.AndroidXMapFragment;
import com.here.android.mpa.mapping.Map;


import java.lang.ref.WeakReference;
import java.util.concurrent.Executors;

public class MapFragmentActivity extends FragmentActivity {

    private boolean paused;
    private boolean foundPos = false;
    private AndroidXMapFragment mapFragment;
    private Map map;
    private PositioningManager posManager;
    private LocationDataSourceHERE posDataSource;
    private PositioningManager.LocationMethod LOCATION_METHOD;
    private PositioningManager.OnPositionChangedListener positionListener;
    private AppCompatActivity activity;

    // constructor
    public MapFragmentActivity(AppCompatActivity activity, AndroidXMapFragment mapFragment,
                                    PositioningManager.LocationMethod LOCATION_METHOD) {
        this.activity = activity;
        this.mapFragment = mapFragment;
        this.LOCATION_METHOD = LOCATION_METHOD;
    }

    // Initializes map fragment view
    public void initialize() {
         positionListener = new PositioningManager.OnPositionChangedListener() {
            // When a new position is updated
            @Override
            public void onPositionUpdated(PositioningManager.LocationMethod locationMethod, @Nullable GeoPosition geoPosition, boolean b) {
            /* set the center only when the app is in the foreground
            to reduce CPU consumption */
                if (!paused) {
                    map.setCenter(geoPosition.getCoordinate(), Map.Animation.LINEAR);
                    if (!foundPos) {
                        MainActivity.textToSpeech.speak("Position found!", TextToSpeech.QUEUE_FLUSH, null, TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID);
                        foundPos = true;
                    }
                }
            }

            // When the position method changes
            @Override
            public void onPositionFixChanged(PositioningManager.LocationMethod locationMethod, PositioningManager.LocationStatus locationStatus) {
                System.out.println("Location Method Changed!");
                System.out.println(locationStatus.toString());
                System.out.println(locationMethod.toString());
            }
        };

        // Initialize Map Fragment and Engine
        mapFragment.init(new OnEngineInitListener() {
            @Override
            public void onEngineInitializationCompleted(
                    final OnEngineInitListener.Error error) {
                if (error == OnEngineInitListener.Error.NONE) {
                    // retrieve a reference of the map from the map fragment
                    map = mapFragment.getMap();
                    // Set the zoom level
                    map.setZoomLevel(map.getMaxZoomLevel()*0.85);
                    // Set the tilt to 45 degrees
                    map.setTilt(45);
                    // set initial center of map
                    map.setCenter(new GeoCoordinate(35.614396, -82.566611, 0), Map.Animation.NONE);
                    // display position indicator
                    mapFragment.getPositionIndicator().setVisible(true);
                }
                else {
                    showErrorMessage(error);
                }
            }
        });
    }

    public boolean isEngineInit() {
        return(MapEngine.isInitialized());
    }

    /**
     * Initialize the positioning service using LOCATION_METHOD.
     * @return
     */
    public boolean initPositioning() {
        // TODO in background
        posManager = PositioningManager.getInstance();
        posDataSource = LocationDataSourceHERE.getInstance();
        if (posDataSource == null) {
            showToast("LocationDataSourceHERE.getInstance(): failed, exiting");
            return false;
        }
        posManager.setDataSource(posDataSource);
        posManager.addListener(new WeakReference<PositioningManager.OnPositionChangedListener>(positionListener));
        if (posManager.start(LOCATION_METHOD)) {
            // Position updates started successfully.
        }
        else {
            showToast("PositioningManager.start: failed");
            return false;
        }
        return true;
    }

    // Shows the error message for engine initialization
    private void showErrorMessage(OnEngineInitListener.Error error) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                new AlertDialog.Builder(activity).setMessage(
                        "Error : " + error.name() + "\n\n" + error.getDetails())
                        .setTitle(R.string.engine_init_error)
                        .setNegativeButton(android.R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(
                                            DialogInterface dialog,
                                            int which) {
                                        finishAffinity();
                                    }
                                }).create().show();
            }
        });
    }

    // Displays toast on UI thread
    private void showToast(String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, s, Toast.LENGTH_LONG).show();
            }
        });
    }

    public void pause() {
        System.out.println("this is pausing mapfrag");
        if (posManager != null) {
            posManager.stop();
        }
        paused = true;
        foundPos = false;
    }

    public void resume() {
        System.out.println("this is resuming mapfrag");
        paused = false;
        if (posManager != null) {
            posManager.start(PositioningManager.LocationMethod.GPS);
        }
    }

    public void destroy() {
        System.out.println("destroying mapfrag");
        if (posManager != null) {
            // Cleanup
            posManager.removeListener(positionListener);
        }
        map = null;
    }

    // Stop listening to position when application is paused
    protected void onPause() {
        super.onPause();
    }

    // Start listening to position when application is resumed
    public void onResume() {
        super.onResume();
    }

    // Cleanup after application is closed
    public void onDestroy() {
        super.onDestroy();
    }
}
