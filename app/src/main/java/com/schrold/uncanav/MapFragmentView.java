package com.schrold.uncanav;

import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PointF;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.common.GeoPolyline;
import com.here.android.mpa.common.GeoPosition;
import com.here.android.mpa.common.LocationDataSourceHERE;
import com.here.android.mpa.common.MapEngine;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.common.PositioningManager;
import com.here.android.mpa.common.PositioningManager.OnPositionChangedListener;
import com.here.android.mpa.common.ViewObject;
import com.here.android.mpa.ftcr.FTCRLaneInformation;
import com.here.android.mpa.ftcr.FTCRManeuver;
import com.here.android.mpa.ftcr.FTCRNavigationManager;
import com.here.android.mpa.ftcr.FTCRRoute;
import com.here.android.mpa.ftcr.FTCRRouteOptions;
import com.here.android.mpa.ftcr.FTCRRoutePlan;
import com.here.android.mpa.ftcr.FTCRRouter;
import com.here.android.mpa.ftcr.FTCRVoiceGuidanceOptions;
import com.here.android.mpa.guidance.AudioPlayerDelegate;
import com.here.android.mpa.mapping.Map;
import com.here.android.mpa.mapping.MapGesture.OnGestureListener;
import com.here.android.mpa.mapping.MapMarker;
import com.here.android.mpa.mapping.MapPolyline;
import com.here.android.mpa.routing.RouteWaypoint;
import com.here.android.mpa.routing.RoutingError;
import com.here.android.mpa.venues3d.DeselectionSource;
import com.here.android.mpa.venues3d.Level;
import com.here.android.mpa.venues3d.Space;
import com.here.android.mpa.venues3d.Venue;
import com.here.android.mpa.venues3d.VenueMapFragment;
import com.here.android.mpa.venues3d.VenueService;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wrapper class to handle everything related to the map fragment. Also handles positioning and
 * location updates.
 */
public class MapFragmentView {

    // Flag indicating if the application is paused
    private boolean paused;

    // Flag indicating if the user's position has been found
    private boolean foundPos = false;

    // Reference to the MapFragment in the main activity
    private final VenueMapFragment mapFragment;

    // The map to be used with HERE
    private Map map;

    // The positioning manager
    private PositioningManager posManager;

    // The router object
    private FTCRRouter router;

    // The navigation manager
    private FTCRNavigationManager navigationManager;

    // Reference to the main activity
    private final AppCompatActivity activity;

    // Location method to be used by PositioningManager
    private final PositioningManager.LocationMethod LOCATION_METHOD
            = PositioningManager.LocationMethod.GPS_NETWORK_INDOOR;

    // Current routing task
    private FTCRRouter.CancellableTask ftcrRoutingTask;

    // Flag to determine if user has been welcomed
    private boolean speak_welcome = false;

    // Map object indicating current route
    private MapPolyline currentRoute;

    //TODO: get rid of
    private PointF tapPoint;

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
     * Initializes the map engine and starts positioning updates.
     */
    public void initialize() {
        OnEngineInitListener m_onEngineInitListener = new OnEngineInitListener() {
            @Override
            public void onEngineInitializationCompleted(final OnEngineInitListener.Error error) {
                if (error == OnEngineInitListener.Error.NONE) {
                    // Add listener for venues
                    mapFragment.addListener(m_venueListener);
                    // Add listener for map gestures
                    mapFragment.getMapGesture().addOnGestureListener(m_onGestureListener, 0, true);
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
        };

        VenueService.VenueServiceListener m_venueServiceListener = new VenueService.VenueServiceListener() {
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
                        // Initialize the FTCRRouter and NavigationManager
                        router = new FTCRRouter();
                        navigationManager = new FTCRNavigationManager();
                        // Set up the navigation manager and speech settings
                        navigationManager.setMap(map);
                        navigationManager.addNavigationListener(m_FTCRNavigationListener);
                        navigationManager.setMapTrackingMode(FTCRNavigationManager.TrackingMode.NONE);
                        navigationManager.getAudioPlayer().setDelegate(m_audioPlayerDelegate);
                        navigationManager.getVoiceGuidanceOptions().setVoicePromptDistanceRangeFromPreviousManeuver(new FTCRVoiceGuidanceOptions.Range(10, -1));
                        navigationManager.getVoiceGuidanceOptions().setVoicePromptDistanceRangeToNextManeuver(new FTCRVoiceGuidanceOptions.Range(-1, 4));
                        navigationManager.getVoiceGuidanceOptions().setVoicePromptTimeRangeToNextManeuver(new FTCRVoiceGuidanceOptions.Range(-1, -1));
                        break;
                    default:
                        // Initialization failed, retry
                        System.out.println("Initialization status:" + initStatus.toString());
                        initialize();
                        break;
                }
            }
        };

        mapFragment.init(m_onEngineInitListener, m_venueServiceListener);
    }

    /**
     * Initializes the positioning service using LOCATION_METHOD.
     *
     * @return true if positioning manager has started successfully; false otherwise
     */
    private boolean initPositioning() {
        posManager = PositioningManager.getInstance();
        posManager.setDataSource(LocationDataSourceHERE.getInstance());
        posManager.addListener(new WeakReference<>(m_onPositionChangedListener));
        if (!posManager.start(LOCATION_METHOD)) {
            MainActivity.speak(activity.getResources().getString(R.string.pos_failed));
            return false;
        }
        // Set the position and accuracy indicator to be visible
        mapFragment.getPositionIndicator().setVisible(true);
        mapFragment.getPositionIndicator().setAccuracyIndicatorVisible(true);
        return true;
    }

    /**
     * Checks to see whether the MapEngine is initialized.
     *
     * @return true if MapEngine is initialized
     */
    public boolean isEngineInit() {
        return(MapEngine.isInitialized());
    }

    /**
     * Handles audio to be played during navigation.
     */
    private final AudioPlayerDelegate m_audioPlayerDelegate = new AudioPlayerDelegate() {
        @Override public boolean playText(@NonNull final String s) {
            // Trim out 'road names'
            if (s.contains("on") && !s.contains("arrive")) {
                MainActivity.speak(s.substring(0, s.indexOf("on")));
            }
            return true;
        }

        @Override public boolean playFiles(@NonNull String[] strings) {
            return false;
        }
    };

    /**
     * Draws a given route onto the map and removes previous route.
     *
     * @param route the route to draw on the map
     */
    private void drawRoute(FTCRRoute route) {
        if (currentRoute != null) {
            map.removeMapObject(currentRoute);
        }
        currentRoute = new MapPolyline(new GeoPolyline(route.getGeometry()));
        currentRoute.setLineColor(Color.argb(255, 185, 63, 2));
        currentRoute.setLineWidth(15);
        currentRoute.setPatternStyle(MapPolyline.PatternStyle.DASH_PATTERN);
        map.addMapObject(currentRoute);
    }

    /**
     * Begins the routing process using a GeoCoordinate as input.
     *
     * @param destination the GeoCoordinate of the destination
     */
    private void startRouting(GeoCoordinate destination) {
        // Clear the map
        map.removeAllMapObjects();

        // Create the RouteOptions and set transport mode & routing type
        FTCRRouteOptions routeOptions = new FTCRRouteOptions();
        routeOptions.setTransportMode(FTCRRouteOptions.TransportMode.PEDESTRIAN);
        routeOptions.setRouteType(FTCRRouteOptions.Type.SHORTEST);
        routeOptions.enableUTurnAtWaypoint(true);

        // Create the RoutePlan with two waypoints
        List<RouteWaypoint> routePoints = new ArrayList<>();
        GeoCoordinate currentLocation = new GeoCoordinate(posManager.getPosition().getCoordinate());
        routePoints.add(new RouteWaypoint(currentLocation));
        routePoints.add(new RouteWaypoint(destination));
        FTCRRoutePlan routePlan = new FTCRRoutePlan(routePoints, routeOptions);

        // Set the name of the FTCR map overlay to use
        // See:     https://tcs.ext.here.com/examples/v3/cre_submit_overlay
        routePlan.setOverlay("OVERLAYRRO1");

        // Add a marker on map for destination
        MapMarker endMapMarker = new MapMarker(destination);
        map.addMapObject(endMapMarker);

        // Calculate the route
        ftcrRoutingTask = router.calculateRoute(routePlan, new FTCRRouter.Listener() {
            @Override
            public void onCalculateRouteFinished(@NonNull List<FTCRRoute> routeResults, @NonNull FTCRRouter.ErrorResponse errorResponse) {
                // If the route was calculated successfully
                if (errorResponse.getErrorCode() == RoutingError.NONE) {
                    // Draw the route on the map
                    drawRoute(routeResults.get(0));
                    // Start navigation
                    //navigationManager.simulate(routeResults.get(0), 2);   // causes crash if attempt to stop
                    navigationManager.start(routeResults.get(0));
                }
                else {
                    MainActivity.speak(activity.getResources().getString(R.string.route_error));
                }
            }
        });
    }

    /**
     * Stops navigation and removes routing overlays from map.
     */
    private void stopRouting() {
        if (navigationManager != null && navigationManager.isActive()) {
            navigationManager.getAudioPlayer().stop();
            navigationManager.stop();
        }
        if (ftcrRoutingTask != null) {
            ftcrRoutingTask.cancel();
        }
        map.removeAllMapObjects();
    }

    /**
     * A HashMap containing key-value pairs for room numbers and their coordinates.
     */
    private final HashMap<String, GeoCoordinate> classrooms = new HashMap<String, GeoCoordinate>() {{
        put("106", new GeoCoordinate(35.615634,-82.565787));
        put("111", new GeoCoordinate(35.615754,-82.565703));
        put("113", new GeoCoordinate(35.615784,-82.565677));
        put("114", new GeoCoordinate(35.615817,-82.56563));
        put("115", new GeoCoordinate(35.615852,-82.565596));
        put("117", new GeoCoordinate(35.61586,-82.565557));

        put("108", new GeoCoordinate(35.615657,-82.56567));
        put("110", new GeoCoordinate(35.615706,-82.56561));
        put("112", new GeoCoordinate(35.615722,-82.56559));
        put("116", new GeoCoordinate(35.615793,-82.565531));

        put("125", new GeoCoordinate(35.615969,-82.565366));
        put("126", new GeoCoordinate(35.61595,-82.565331));
        put("127", new GeoCoordinate(35.615933,-82.565303));
        put("128", new GeoCoordinate(35.615915,-82.565272));

        put("131", new GeoCoordinate(35.615854,-82.565222));
        put("132", new GeoCoordinate(35.615778,-82.565294));
        put("135", new GeoCoordinate(35.615685,-82.565379));
        put("138", new GeoCoordinate(35.615584,-82.565464));
    }};

    /**
     * Callback function for handling speech recognizer results. If result matches
     * a valid destination, begin routing.
     *
     * @param result the string captured by the speech recognizer
     */
    public void speechCallback(String result) {
        // Check if user cancelled input
        Pattern cancel = Pattern.compile("cancel|nevermind|never mind", Pattern.CASE_INSENSITIVE);
        if (result != null && !cancel.matcher(result).find()) {
            // Check if input contains a room number or bathroom
            Pattern p_num = Pattern.compile("\\d+|bathroom");
            Matcher m = p_num.matcher(result);
            String match = "";
            boolean no_dest = true;
            if (m.find()) {
                // Possible destination, check if in room hashmap
                match = m.group();
                System.out.printf("regex match: %s%n", match);
                if (classrooms.containsKey(match)) {
                    MainActivity.speak(activity.getResources().getString(R.string.start_nav));
                    no_dest = false;
                }
            }
            // If there is no destination, tell user
            if (no_dest)
                MainActivity.speak(activity.getResources().getString(R.string.no_destination));
            else
                startRouting(classrooms.get(match));
        }
    }

    /**
     * Contains listener functions for positioning updates.
     */
    private final PositioningManager.OnPositionChangedListener m_onPositionChangedListener = new OnPositionChangedListener() {
        @Override
        public void onPositionUpdated(PositioningManager.LocationMethod locationMethod, @Nullable GeoPosition geoPosition, boolean b) {
            // Set the center of the map when app is in foreground
            if (!paused && geoPosition != null) {
                map.setCenter(geoPosition.getCoordinate(), Map.Animation.BOW);
                if (!foundPos) {
                    foundPos = true;
                }
                if (!speak_welcome) {
                    MainActivity.speak(activity.getResources().getString(R.string.pos_found));
                    speak_welcome = true;
                }
            }
            // Update the location information in the text view
            updateLocationInfo(geoPosition);
        }

        @Override
        public void onPositionFixChanged(PositioningManager.LocationMethod locationMethod, PositioningManager.LocationStatus locationStatus) {

        }
    };

    /**
     * Contains listener functions for input gestures.
     */
    private final OnGestureListener m_onGestureListener = new OnGestureListener() {
        @Override
        public boolean onTapEvent(@NonNull PointF pointF) {
            // GeoCoordinate touchLocation = map.pixelToGeo(tapPoint);
            // Do not accept input unless navigation manager has been initialized
            if (!foundPos || navigationManager == null){
                MainActivity.speak(activity.getResources().getString(R.string.waiting_positioning));
                return false;
            }
            // If not navigating, attempt speech recognition
            if (!navigationManager.isActive()) {
                activity.runOnUiThread(MainActivity::startListening);
            }
            return true;
        }

        @Override
        public boolean onDoubleTapEvent(@NonNull PointF pointF) {
            // Stop routing when user double taps the screen
            stopRouting();
            return true;
        }

        @Override
        public void onPanStart() {        }

        @Override
        public void onPanEnd() {        }

        @Override
        public void onMultiFingerManipulationStart() {        }

        @Override
        public void onMultiFingerManipulationEnd() {        }

        @Override
        public boolean onMapObjectsSelected(@NonNull List<ViewObject> list) {
            return false;
        }

        @Override
        public void onPinchLocked() {        }

        @Override
        public boolean onPinchZoomEvent(float v, @NonNull PointF pointF) {
            return false;
        }

        @Override
        public void onRotateLocked() {        }

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
        public void onLongPressRelease() {        }

        @Override
        public boolean onTwoFingerTapEvent(@NonNull PointF pointF) {
            return false;
        }
    };

    /**
     * Contains listener functions for navigation callbacks.
     */
    private final FTCRNavigationManager.FTCRNavigationManagerListener m_FTCRNavigationListener = new FTCRNavigationManager.FTCRNavigationManagerListener() {
        @Override
        public void onCurrentManeuverChanged(@Nullable FTCRManeuver ftcrManeuver, @Nullable FTCRManeuver ftcrManeuver1) {        }

        @Override
        public void onStopoverReached(int i) {        }

        @Override
        public void onDestinationReached() {
            // Notify the user
            MainActivity.speak(activity.getResources().getString(R.string.arrived));
        }

        @Override
        public void onRerouteBegin() {        }

        @Override
        public void onRerouteEnd(@Nullable FTCRRoute newRoute, @NonNull FTCRRouter.ErrorResponse error) {
            // We must remove the old route from the map and add the new one
            if (error.getErrorCode() == RoutingError.NONE && newRoute != null) {
                MainActivity.speak(activity.getResources().getString(R.string.rerouting));
                drawRoute(newRoute);
            }
        }

        @Override
        public void onLaneInformation(@NonNull List<FTCRLaneInformation> list) {        }
    };

    /**
     * Contains listener functions for venue callbacks.
     */
    private final VenueMapFragment.VenueListener m_venueListener = new VenueMapFragment.VenueListener() {
        @Override
        public void onVenueTapped(Venue venue, float v, float v1) {        }

        @Override
        public void onVenueSelected(Venue venue) {        }

        @Override
        public void onVenueDeselected(Venue venue, DeselectionSource deselectionSource) {        }

        @Override
        public void onSpaceSelected(Venue venue, Space space) {        }

        @Override
        public void onSpaceDeselected(Venue venue, Space space) {        }

        @Override
        public void onFloorChanged(Venue venue, Level level, Level level1) {        }

        @Override
        public void onVenueVisibleInViewport(Venue venue, boolean b) {        }
    };

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

    /**
     * Display a toast on the UI thread.
     * TODO: remove this method
     * @param message the message to display
     */
    private void showToast(final String message) {
        activity.runOnUiThread(() -> Toast.makeText(activity.getApplicationContext(), message, Toast.LENGTH_LONG).show());
    }

    /**
     * Shows an error in an alert dialog on the UI thread.
     *
     * @param errorName the name of the error
     * @param errorDetails the details of the error
     */
    private void showErrorMessage(final String errorName, final String errorDetails) {
        activity.runOnUiThread(() -> new AlertDialog.Builder(activity).setMessage(
                "Error : " + errorName + "\n\n" + errorDetails)
                .setTitle(R.string.engine_init_error)
                .setNegativeButton(android.R.string.ok,
                        (dialog, which) -> activity.finishAffinity()).create().show());
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
            posManager.removeListener(m_onPositionChangedListener);
        }
        if (navigationManager != null) {
            navigationManager.stop();
        }
        map = null;
    }
}
