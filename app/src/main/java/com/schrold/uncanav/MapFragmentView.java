package com.schrold.uncanav;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PointF;
import android.speech.tts.TextToSpeech;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.here.android.mpa.common.ApplicationContext;
import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.common.GeoPolyline;
import com.here.android.mpa.common.GeoPosition;
import com.here.android.mpa.common.LocationDataSourceHERE;
import com.here.android.mpa.common.MapEngine;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.common.PositioningManager;
import com.here.android.mpa.common.ViewObject;
import com.here.android.mpa.mapping.Map;
import com.here.android.mpa.common.PositioningManager.OnPositionChangedListener;
import com.here.android.mpa.mapping.MapGesture.OnGestureListener;
import com.here.android.mpa.mapping.MapPolyline;
import com.here.android.mpa.mapping.MapRoute;
import com.here.android.mpa.routing.CoreRouter;
import com.here.android.mpa.routing.RouteOptions;
import com.here.android.mpa.routing.RoutePlan;
import com.here.android.mpa.routing.RouteResult;
import com.here.android.mpa.routing.RouteWaypoint;
import com.here.android.mpa.routing.Router;
import com.here.android.mpa.routing.RoutingError;
import com.here.android.mpa.venues3d.CombinedRoute;
import com.here.android.mpa.venues3d.DeselectionSource;
import com.here.android.mpa.venues3d.Level;
import com.here.android.mpa.venues3d.RoutingController;
import com.here.android.mpa.venues3d.RoutingController.RoutingControllerListener;
import com.here.android.mpa.venues3d.Space;
import com.here.android.mpa.venues3d.Venue;
import com.here.android.mpa.venues3d.VenueMapFragment;
import com.here.android.mpa.venues3d.VenueService;


import java.lang.ref.WeakReference;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Wrapper class to handle everything related to the map fragment. Also handles positioning and
 * location updates.
 */
public class MapFragmentView implements VenueMapFragment.VenueListener,
        OnPositionChangedListener,
        OnGestureListener,
        RoutingControllerListener {

    // Flag indicating if the application is paused
    private boolean paused;

    // Flag indicating if the user's position has been found
    private boolean foundPos = false;

    // Reference to the Android MapFragment in the main activity
    private final VenueMapFragment mapFragment;

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
     *
     * @param activity the main activity
     */
    public MapFragmentView(AppCompatActivity activity) {
        this.activity = activity;
        mapFragment = getMapFragment();
    }

    /**
     * Retrieves the map fragment for mapping purposes.
     *
     * @return the map fragment from the main activity
     */
    private VenueMapFragment getMapFragment() {
        return (VenueMapFragment) activity.getFragmentManager().findFragmentById(R.id.mapfragment);
    }

    /**
     * Initializes the map engine and calls to start positioning updates.
     */
    public void initialize() {
        mapFragment.init(new OnEngineInitListener() {
            @Override
            public void onEngineInitializationCompleted(
                    final OnEngineInitListener.Error error) {
                if (error == OnEngineInitListener.Error.NONE) {
                    // Add listener for venues
                    mapFragment.addListener(MapFragmentView.this);
                    // Add listener for map gestures
                    mapFragment.getMapGesture().addOnGestureListener(MapFragmentView.this, 0, true);
                    // Add listener for routing
                    if (mapFragment.getRoutingController() != null) {
                        mapFragment.getRoutingController().addListener(MapFragmentView.this);
                    }

                    // Retrieve a reference of the map from the map fragment
                    map = mapFragment.getMap();
                    // Set the zoom level
                    map.setZoomLevel(map.getMaxZoomLevel() * 0.90);
                    // Set the tilt to 45 degrees
                    map.setTilt(45);
                    // Set initial center of map
                    map.setCenter(new GeoCoordinate(35.615330, -82.5659220, 0), Map.Animation.NONE);

                    // Other customization options
                    map.setMapScheme(Map.Scheme.PEDESTRIAN_DAY);
                    int nightModeFlags =
                            activity.getApplicationContext().getResources().getConfiguration().uiMode &
                                    Configuration.UI_MODE_NIGHT_MASK;
                    if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                        map.setMapScheme(Map.Scheme.PEDESTRIAN_NIGHT);
                    }
                    map.setPedestrianFeaturesVisible(EnumSet.of(Map.PedestrianFeature.CROSSWALK));
                    map.setLandmarksVisible(true);
                    map.setExtrudedBuildingsVisible(true);
                } else {
                    showErrorMessage(error.name(), error.getDetails());
                }
            }
        }, new VenueService.VenueServiceListener() {
            @Override
            public void onInitializationCompleted(VenueService.InitStatus initStatus) {
                switch (initStatus) {
                    case IN_PROGRESS:
                        break;
                    case OFFLINE_SUCCESS:
                    case ONLINE_SUCCESS:
                        // Initialize positioning updates
                        if (!initPositioning()) {
                            // Positioning initialization failed
                            showErrorMessage("Positioning initialization", "Positioning initialization failed. Exiting.");
                        }

                        // Select Rhoades-Robinson building
                        mapFragment.selectVenueAsync("DM_15755");
                        break;
                    default:
                        // Means that it failed, retry
                        System.out.println("INITSTATUS:" + initStatus.toString());
                        initialize();
                        break;
                }
            }
        });
    }

    /**
     * Initialize the positioning service using LOCATION_METHOD.
     *
     * @return true if positioning manager has started successfully; false otherwise
     */
    private boolean initPositioning() {
        posManager = PositioningManager.getInstance();
        LocationDataSourceHERE posDataSource = LocationDataSourceHERE.getInstance();

        posManager.setDataSource(posDataSource);
        posManager.addListener(new WeakReference<>(this));
        if (!posManager.start(LOCATION_METHOD)) {
            showToast("PositioningManager.start: failed");
            return false;
        }
        // Set the position and accuracy indicator to be visible
        mapFragment.getPositionIndicator().setVisible(true);
        mapFragment.getPositionIndicator().setAccuracyIndicatorVisible(true);
        return true;
    }

    @Override
    public void onPositionUpdated(PositioningManager.LocationMethod locationMethod,
                                  @Nullable GeoPosition geoPosition, boolean b) {
        /* Set the center only when the app is in the foreground
        to reduce CPU consumption */
        if (!paused && geoPosition != null) {
            map.setCenter(geoPosition.getCoordinate(), Map.Animation.BOW);
            if (!foundPos) {
                MainActivity.textToSpeech.speak(activity.getResources().getString(R.string.pos_found),
                        TextToSpeech.QUEUE_ADD, null, TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID);
                foundPos = true;
            }
        }

        // Update the location information in the text view
        updateLocationInfo(geoPosition);
    }

    @Override
    public void onPositionFixChanged(PositioningManager.LocationMethod locationMethod,
                                     PositioningManager.LocationStatus locationStatus) {
        // Called when the location method has changed
        MainActivity.textToSpeech.speak(String.format("%s %s",
                activity.getResources().getString(R.string.loc_method_change), locationMethod.toString()),
                TextToSpeech.QUEUE_ADD, null, TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID);
    }

    /**
     * Checks to see whether MapEngine is initialized.
     *
     * @return true if MapEngine is initialized
     */
    public boolean isEngineInit() {
        return(MapEngine.isInitialized());
    }

    /**
     * Display a toast on the UI thread.
     *
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
     *
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

    @Override
    public void onVenueTapped(Venue venue, float v, float v1) {

    }

    @Override
    public void onVenueSelected(Venue venue) {

    }

    @Override
    public void onVenueDeselected(Venue venue, DeselectionSource deselectionSource) {

    }

    @Override
    public void onSpaceSelected(Venue venue, Space space) {

    }

    @Override
    public void onSpaceDeselected(Venue venue, Space space) {

    }

    @Override
    public void onFloorChanged(Venue venue, Level level, Level level1) {

    }

    @Override
    public void onVenueVisibleInViewport(Venue venue, boolean b) {

    }



    @Override
    public void onPanStart() {

    }

    @Override
    public void onPanEnd() {

    }

    @Override
    public void onMultiFingerManipulationStart() {

    }

    @Override
    public void onMultiFingerManipulationEnd() {

    }

    @Override
    public boolean onMapObjectsSelected(@NonNull List<ViewObject> list) {
        return false;
    }

    @Override
    public boolean onTapEvent(@NonNull PointF pointF) {
        /*
         Create a simple route and show it on screen, starting from current
         location and ending at tapped location.
        */
        if (!foundPos || mapFragment.getRoutingController() == null){
            showToast(activity.getResources().getString(R.string.waiting_positioning));
            return false;
        }
        GeoCoordinate touchLocation = map.pixelToGeo(pointF);
        if (touchLocation != null) {
            double lat = touchLocation.getLatitude();
            double lon = touchLocation.getLongitude();

            // Show a toast with tapped location geo-coordinate
            String StrGeo = String.format("%.6f, %.6f", lat, lon);
            showToast(StrGeo);

            // Declare the CoreRouter
            CoreRouter router = new CoreRouter();

            // Create the RoutePlan and add two waypoints
            RoutePlan routePlan = new RoutePlan();
            GeoCoordinate startLocation = new GeoCoordinate(posManager.getPosition().getCoordinate());
            routePlan.addWaypoint(new RouteWaypoint(startLocation));
            routePlan.addWaypoint(new RouteWaypoint(touchLocation));

            // Create the RouteOptions and set its transport mode & routing type
            RouteOptions routeOptions = new RouteOptions();
            routeOptions.setTransportMode(RouteOptions.TransportMode.PEDESTRIAN);
            routeOptions.setRouteType(RouteOptions.Type.FASTEST);
            routePlan.setRouteOptions(routeOptions);

            // Draw a line on map from one point to other
            GeoPolyline pl = new GeoPolyline();
            pl.add(startLocation);
            pl.add(touchLocation);
            map.addMapObject(new MapPolyline(pl));

            // Calculate the route
            router.calculateRoute(routePlan, new Router.Listener<List<RouteResult>, RoutingError>() {
                @Override
                public void onProgress(int percentage) {
                    // Display a message indicating calculation progress
                }

                @Override
                public void onCalculateRouteFinished(@NonNull List<RouteResult> routeResults, @NonNull RoutingError routingError) {
                    // If the route was calculated successfully
                    if (routingError == RoutingError.NONE) {
                        // Render the route on the map
                        MapRoute mapRoute = new MapRoute(routeResults.get(0).getRoute());
                        map.addMapObject(mapRoute);

                        // Change colors of route
                        mapRoute.setRenderType(MapRoute.RenderType.PRIMARY);
                        mapRoute.setColor(Color.GREEN);
                    }
                    else {
                        showToast("Route calculation error!");
                    }
                }
            });

        }
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(@NonNull PointF pointF) {
        // Remove all map objects on screen
        map.removeAllMapObjects();
        return true;
    }

    @Override
    public void onPinchLocked() {

    }

    @Override
    public boolean onPinchZoomEvent(float v, @NonNull PointF pointF) {
        return false;
    }

    @Override
    public void onRotateLocked() {

    }

    @Override
    public boolean onRotateEvent(float v) {
        return false;
    }

    @Override
    public boolean onTiltEvent(float v) {
        return false;
    }

    @Override
    public boolean onLongPressEvent(@NonNull PointF pointF) {
        return false;
    }

    @Override
    public void onLongPressRelease() {

    }

    @Override
    public boolean onTwoFingerTapEvent(@NonNull PointF pointF) {
        return false;
    }

    @Override
    public void onCombinedRouteCompleted(CombinedRoute combinedRoute) {

    }

    /**
     * Update location information to the text view.
     */
    private void updateLocationInfo(GeoPosition geoPosition) {
        TextView mLocationInfo = activity.findViewById(R.id.textViewPosInfo);
        if (mLocationInfo == null) {
            return;
        }
        final StringBuilder sb = new StringBuilder();
        final GeoCoordinate coord = geoPosition.getCoordinate();
        if(geoPosition.getPositionSource() != GeoPosition.UNKNOWN) {
            sb.append("Position Source: ").append(String.format(Locale.US, "%s\n", positionSourceToString(geoPosition)));
        }
        if (geoPosition.getPositionTechnology() != GeoPosition.UNKNOWN) {
            sb.append("Position Technology: ").append(String.format(Locale.US, "%s\n", positionTechnologyToString(geoPosition)));
        }
        sb.append("Coordinate:").append(String.format(Locale.US, "%.6f, %.6f\n", coord.getLatitude(), coord.getLongitude()));
        if (geoPosition.getLatitudeAccuracy() != GeoPosition.UNKNOWN) {
            sb.append("Uncertainty:").append(String.format(Locale.US, "%.2fm\n", geoPosition.getLatitudeAccuracy()));
        }
        if (coord.getAltitude() != GeoCoordinate.UNKNOWN_ALTITUDE) {
            sb.append("Altitude:").append(String.format(Locale.US, "%.2fm\n", coord.getAltitude()));
        }
        if (geoPosition.getHeading() != GeoPosition.UNKNOWN) {
            sb.append("Heading:").append(String.format(Locale.US, "%.2f\n", geoPosition.getHeading()));
        }
        if (geoPosition.getSpeed() != GeoPosition.UNKNOWN) {
            sb.append("Speed:").append(String.format(Locale.US, "%.2fm/s\n", geoPosition.getSpeed()));
        }
        if (geoPosition.getBuildingName() != null) {
            sb.append("Building: ").append(geoPosition.getBuildingName());
            if (geoPosition.getBuildingId() != null) {
                sb.append(" (").append(geoPosition.getBuildingId()).append(")\n");
            } else {
                sb.append("\n");
            }
        }
        if (geoPosition.getFloorId() != null) {
            sb.append("Floor ID: ").append(geoPosition.getFloorId()).append("\n");
        }
        sb.deleteCharAt(sb.length() - 1);
        mLocationInfo.setText(sb.toString());
    }

    /**
     * Converting position source to string.
     */
    private String positionSourceToString(GeoPosition geoPosition) {
        final int sources = geoPosition.getPositionSource();
        if (sources == GeoPosition.SOURCE_NONE) {
            return "NONE";
        }
        final StringBuilder result = new StringBuilder();
        if ((sources & GeoPosition.SOURCE_CACHE) != 0) {
            result.append("CACHE ");
        }
        if ((sources & GeoPosition.SOURCE_FUSION) != 0) {
            result.append("FUSION ");
        }
        if ((sources & GeoPosition.SOURCE_HARDWARE) != 0) {
            result.append("HARDWARE ");
        }
        if ((sources & GeoPosition.SOURCE_INDOOR) != 0) {
            result.append("INDOOR ");
        }
        if ((sources & GeoPosition.SOURCE_OFFLINE) != 0) {
            result.append("OFFLINE ");
        }
        if ((sources & GeoPosition.SOURCE_ONLINE) != 0) {
            result.append("ONLINE ");
        }
        return result.toString().trim();
    }

    /**
     * Converting position technology to string.
     */
    private String positionTechnologyToString(GeoPosition geoPosition) {
        final int technologies = geoPosition.getPositionTechnology();
        if (technologies == GeoPosition.TECHNOLOGY_NONE) {
            return "NONE";
        }
        final StringBuilder result = new StringBuilder();
        if ((technologies & GeoPosition.TECHNOLOGY_BLE) != 0) {
            result.append("BLE ");
        }
        if ((technologies & GeoPosition.TECHNOLOGY_CELL) != 0) {
            result.append("CELL ");
        }
        if ((technologies & GeoPosition.TECHNOLOGY_GNSS) != 0) {
            result.append("GNSS ");
        }
        if ((technologies & GeoPosition.TECHNOLOGY_WIFI) != 0) {
            result.append("WIFI ");
        }
        if ((technologies & GeoPosition.TECHNOLOGY_SENSORS) != 0) {
            result.append("SENSORS ");
        }
        return result.toString().trim();
    }
}
