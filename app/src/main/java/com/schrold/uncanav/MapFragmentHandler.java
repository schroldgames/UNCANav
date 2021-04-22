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


import java.lang.ref.WeakReference;

/** Wrapper class to handle everything related to the map fragment. Also handles positioning and
 * location updates.
 */
public class MapFragmentHandler {

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

    // The location method
    private final PositioningManager.LocationMethod LOCATION_METHOD;

    // The listener for handling position changes
    private PositioningManager.OnPositionChangedListener positionListener;

    // Reference to the main activity
    private final AppCompatActivity activity;

    /**
     * Constructor for the MapFragmentHandler class.
     * @param activity the main activity
     * @param mapFragment the map fragment
     * @param LOCATION_METHOD the location method
     */
    public MapFragmentHandler(AppCompatActivity activity, AndroidXMapFragment mapFragment,
                                    PositioningManager.LocationMethod LOCATION_METHOD) {
        this.activity = activity;
        this.mapFragment = mapFragment;
        this.LOCATION_METHOD = LOCATION_METHOD;
    }

    // Initializes Map
    public void initialize() {
        // Initialize listener for position updates
        positionListener = getPositionListener();

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

                    /*
                    map.setExtrudedBuildingsVisible(true);
                    MapBuildingGroup buildingGroup = map.getMapBuildingLayer().createNewBuildingGroup(MapBuildingLayer.DefaultBuildingColor.SELECTED);
                    buildingGroup.setVerticalScale(.90f);
                    buildingGroup.setColor(Color.RED, EnumSet.of(MapBuildingGroup.BuildingFace.ROOF));*/

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
     * Returns a listener for position updates.
     * @return a listener for position updates
     */
    private PositioningManager.OnPositionChangedListener getPositionListener() {
        return new PositioningManager.OnPositionChangedListener() {
            // Called when position has been found
            @Override
            public void onPositionUpdated(PositioningManager.LocationMethod locationMethod, @Nullable GeoPosition geoPosition, boolean b) {
                /* Set the center only when the app is in the foreground
                to reduce CPU consumption */
                if (!paused) {
                    map.setCenter(geoPosition.getCoordinate(), Map.Animation.LINEAR);
                    if (!foundPos) {
                        MainActivity.textToSpeech.speak("Position found",
                                TextToSpeech.QUEUE_ADD, null, TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID);
                        foundPos = true;
                    }
                }
            }

            // Called when the location method has changed
            @Override
            public void onPositionFixChanged(PositioningManager.LocationMethod locationMethod, PositioningManager.LocationStatus locationStatus) {
                System.out.println("Location Method Changed!");
                System.out.println(locationStatus.toString());
                System.out.println(locationMethod.toString());
                MainActivity.textToSpeech.speak(String.format("Location method changed to %s", locationMethod.toString()),
                        TextToSpeech.QUEUE_ADD, null, TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID);
            }
        };
    }

    /**
     * Initialize the positioning service using LOCATION_METHOD.
     * @return true if positioning manager has started successfully; false otherwise
     */
    public boolean initPositioning() {
        // TODO in background
        posManager = PositioningManager.getInstance();
        LocationDataSourceHERE posDataSource = LocationDataSourceHERE.getInstance();

        posManager.setDataSource(posDataSource);
        posManager.addListener(new WeakReference<PositioningManager.OnPositionChangedListener>(positionListener));
        if (!posManager.start(LOCATION_METHOD)) {
            showToast("PositioningManager.start: failed");
            return false;
        }
        return true;
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
    private void showToast(String message) {
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
    private void showErrorMessage(String errorName, String errorDetails) {
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
            posManager.start(LOCATION_METHOD);
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

}
