package com.schrold.uncanav;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.common.GeoPosition;
import com.here.android.mpa.common.LocationDataSourceHERE;
import com.here.android.mpa.common.MapEngine;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.common.PositioningManager;
import com.here.android.mpa.mapping.AndroidXMapFragment;
import com.here.android.mpa.mapping.Map;
import com.here.android.mpa.common.PositioningManager.OnPositionChangedListener;


import java.lang.ref.WeakReference;
import java.util.EnumSet;

/**
 * Wrapper class to handle everything related to the map fragment. Also handles positioning and
 * location updates.
 */
public class MapFragmentView implements OnPositionChangedListener {

    // Flag indicating if the application is paused
    private boolean paused;

    // Flag indicating if the user's position has been found
    private boolean foundPos = false;

    // Reference to the Android MapFragment in the main activity
    private final AndroidXMapFragment mapFragment;

    // The map to be used with HERE
    private Map map;

    // The positioning manager
    private PositioningManager posManager;

    // Reference to the main activity
    private final AppCompatActivity activity;

    // Location method to be used by PositioningManager
    private final PositioningManager.LocationMethod LOCATION_METHOD
            = PositioningManager.LocationMethod.GPS_NETWORK_INDOOR;

    /**
     * Constructor for the MapFragmentView class.
     * @param activity the main activity
     */
    public MapFragmentView(AppCompatActivity activity) {
        this.activity = activity;
        mapFragment = getMapFragment();
    }

    /**
     * Retrieves the Android MapFragment for mapping purposes.
     * @return AndroidXMapFragment for main activity
     */
    private AndroidXMapFragment getMapFragment() {
        return (AndroidXMapFragment) activity.getSupportFragmentManager().findFragmentById(R.id.mapfragment);
    }

    // Initializes Map
    public void initialize() {
        // Initialize Map Fragment View and Map Engine
        mapFragment.init(new OnEngineInitListener() {
            @Override
            public void onEngineInitializationCompleted(
                    final OnEngineInitListener.Error error) {
                if (error == OnEngineInitListener.Error.NONE) {
                    // Retrieve a reference of the map from the map fragment
                    map = mapFragment.getMap();
                    // Set the zoom level
                    map.setZoomLevel(map.getMaxZoomLevel()*0.85);
                    // Set the tilt to 45 degrees
                    map.setTilt(45);
                    // Set initial center of map
                    map.setCenter(new GeoCoordinate(35.614396, -82.566611, 0), Map.Animation.NONE);

                    // Other customization options
                    int nightModeFlags =
                            activity.getApplicationContext().getResources().getConfiguration().uiMode &
                                    Configuration.UI_MODE_NIGHT_MASK;
                    if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                        map.setMapScheme(Map.Scheme.PEDESTRIAN_NIGHT);
                    }

                    /* SCHEME */
                    map.setMapScheme(Map.Scheme.PEDESTRIAN_DAY);
                    map.setPedestrianFeaturesVisible(EnumSet.of(Map.PedestrianFeature.CROSSWALK));
                    map.setLandmarksVisible(true);
                    map.setExtrudedBuildingsVisible(true);

                    // display position indicator
                    mapFragment.getPositionIndicator().setVisible(true);

                    // Initialize positioning updates
                    if (!initPositioning()) {
                        // Positioning initialization failed
                        showErrorMessage("Positioning initialization", "Positioning initialization failed. Exiting.");
                    }
                }
                else {
                    showErrorMessage(error.name(), error.getDetails());
                }
            }
        });
    }

    /**
     * Initialize the positioning service using LOCATION_METHOD.
     *
     * @return true if positioning manager has started successfully; false otherwise
     */
    public boolean initPositioning() {
        posManager = PositioningManager.getInstance();
        LocationDataSourceHERE posDataSource = LocationDataSourceHERE.getInstance();

        posManager.setDataSource(posDataSource);
        posManager.addListener(new WeakReference<PositioningManager.OnPositionChangedListener>(this));
        if (!posManager.start(LOCATION_METHOD)) {
            showToast("PositioningManager.start: failed");
            return false;
        }
        return true;
    }

    // Called when position has been found
    @Override
    public void onPositionUpdated(PositioningManager.LocationMethod locationMethod,
                                  @Nullable GeoPosition geoPosition, boolean b) {
                /* Set the center only when the app is in the foreground
                to reduce CPU consumption */
        if (!paused) {
            map.setCenter(geoPosition.getCoordinate(), Map.Animation.LINEAR);
            if (!foundPos) {
                MainActivity.textToSpeech.speak(activity.getResources().getString(R.string.pos_found),
                        TextToSpeech.QUEUE_ADD, null, TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID);
                foundPos = true;
            }
        }
    }

    // Called when the location method has changed
    @Override
    public void onPositionFixChanged(PositioningManager.LocationMethod locationMethod,
                                     PositioningManager.LocationStatus locationStatus) {
        MainActivity.textToSpeech.speak(String.format("%s %s",
                activity.getResources().getString(R.string.loc_method_change), locationMethod.toString()),
                TextToSpeech.QUEUE_ADD, null, TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID);
    }

    /**
     * Checks to see whether MapEngine is initialized.
     * @return true if MapEngine is initialized
     */
    public boolean isEngineInit() {
        return(MapEngine.isInitialized());
    }

    /**
     * Display a toast on the UI thread.
     * @param message the message to display
     */
    private void showToast(final String message) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity.getApplicationContext(), message, Toast.LENGTH_LONG)
                        .show();
            }
        });
    }

    /**
     * Shows an error in an alert dialog on the UI thread.
     * @param errorName the name of the error
     * @param errorDetails the details of the error
     */
    private void showErrorMessage(final String errorName, final String errorDetails) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                new AlertDialog.Builder(activity).setMessage(
                        "Error : " + errorName + "\n\n" + errorDetails)
                        .setTitle(R.string.engine_init_error)
                        .setNegativeButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(
                                            DialogInterface dialog,
                                            int which) {
                                        activity.finishAffinity();
                                    }
                                }).create().show();
            }
        });
    }

    /**
     * Method to pause positioning updates.
     */
    public void pause() {
        if (posManager != null) {
            posManager.stop();
        }
        paused = true;
        foundPos = false;
    }

    /**
     * Method to resume positioning updates.
     */
    public void resume() {
        paused = false;
        if (posManager != null) {
            posManager.start(LOCATION_METHOD);
        }
    }

    /**
     * Method to stop positioning updates and destroy map.
     */
    public void destroy() {
        if (posManager != null) {
            posManager.stop();
            posManager.removeListener(this);
        }
        map = null;
    }

}
