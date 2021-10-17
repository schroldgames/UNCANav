/**
 * Helper activity to download and cache map data to the user's device.
 */

// TODO: download radio map data

package com.schrold.uncanav;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.here.android.mpa.common.ApplicationContext;
import com.here.android.mpa.common.LocationDataSourceHERE;
import com.here.android.mpa.common.MapEngine;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.odml.MapLoader;
import com.here.android.mpa.odml.MapPackage;
import com.here.android.positioning.radiomap.RadioMapLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MapDownloadActivity_backup extends AppCompatActivity {

    // The MapLoader object for downloading map data
    private MapLoader m_mapLoader;

    // The RadioMapLoader object for downloading radio map data
    private RadioMapLoader m_radioMapLoader;

    // The TextView object for displaying information below the progress bar
    private TextView textView;

    // The ProgressBar object for displaying download progress
    private ProgressBar progressBar;

    /**
     * Begins the activity by setting the view, initializing variables, and
     * starting the download process.
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load);
        textView = findViewById(R.id.errorText);
        progressBar = findViewById(R.id.progressBar);
        textView.setText(R.string.initializing);
        startDownload();
    }

    /**
     * Wrapper method to finish activity.
     * @param result the result to return to this activity's caller
     */
    private void finishResult(int result) {
        Intent intent = getIntent();
        setResult(result, intent);
        finish();
    }

    /**
     * Starts the download process for map and radio map data.
     */
    private void startDownload() {
        /*
         * HERE Android SDK objects can only be instantiated after the MapEngine has been
         * successfully initialized.
         */
        MapEngine.getInstance().init(new ApplicationContext(this), new OnEngineInitListener() {
            @Override
            public void onEngineInitializationCompleted(Error error) {
                if (error == Error.NONE) {
                    System.out.println("engine initialized for dl");

                    // MapLoader object for downloading map data
                    m_mapLoader = MapLoader.getInstance();

                    // RadioMapLoader object for downloading radio map data
                    m_radioMapLoader = LocationDataSourceHERE.getInstance().getRadioMapLoader();

                    // Adding the listener for the MapLoader
                    m_mapLoader.addListener(getMapLoaderListener());

                    // Begin the MapPackage download process for the MapLoader
                    m_mapLoader.getMapPackages();
                } else {
                    // Error initializing MapEngine, finish
                    Toast.makeText(getApplicationContext(), String.format("Error: %s", error.toString()), Toast.LENGTH_LONG)
                            .show();
                    finishResult(RESULT_CANCELED);
                }
            }
        });
    }

    /**
     * Creates a listener for the MapLoader object.
     * @return the listener for the MapLoader object
     */
    private MapLoader.Listener getMapLoaderListener() {
        return new MapLoader.Listener() {
            /**
             * Callback function for the completion of root MapPackage retrieval.
             * @param mapPackage
             * @param resultCode
             */
            @Override
            public void onGetMapPackagesComplete(@Nullable MapPackage mapPackage, MapLoader.ResultCode resultCode) {
                if (resultCode == MapLoader.ResultCode.OPERATION_SUCCESSFUL) {
                    textView.setText(R.string.map_pack_retrieved);

                    // Create a list of all MapPackages
                    ArrayList<MapPackage> m_currentMapPackageList = new ArrayList<>(mapPackage.getChildren());

                    // Get the MapPackage for North Carolina
                    MapPackage nc_mapPackage = getStateMapPackage("North Carolina", m_currentMapPackageList);

                    if (nc_mapPackage.getInstallationState() == MapPackage.InstallationState.NOT_INSTALLED) {
                        // MapPackage is not installed
                        List<Integer> idList = new ArrayList<>();
                        idList.add(nc_mapPackage.getId());
                        m_mapLoader.installMapPackages(idList);
                    } else {
                        // Check for updates to the MapPackage
                        m_mapLoader.checkForMapDataUpdate();
                    }
                } else {
                    // The map loader is busy, just try again
                    textView.setText(resultCode.toString());
                    m_mapLoader.getMapPackages();
                }
            }

            /**
             * Callback function for progress indication during map data download.
             * @param i
             */
            @Override
            public void onProgress(int i) {
                if (i < 100) {
                    progressBar.setProgress(i);
                    textView.setText(String.format(Locale.US, "%d%%", i));
                } else {
                    textView.setText(R.string.installing);
                }
            }

            /**
             * Callback function for the completion of a map data update check.
             * @param updateAvailable
             * @param current
             * @param update
             * @param resultCode
             */
            @Override
            public void onCheckForUpdateComplete(boolean updateAvailable, String current, String update,
                                                 MapLoader.ResultCode resultCode) {
                if (updateAvailable) {
                    textView.setText(R.string.updates_avail);
                    // Updates are available, perform an update on map data
                    m_mapLoader.performMapDataUpdate();
                }
                else {
                    // No updates are available
                    Toast.makeText(getApplicationContext(), R.string.no_updates, Toast.LENGTH_LONG)
                            .show();
                    // TODO: start downloading radio maps DM_15755
                    finishResult(RESULT_OK);
                }
            }


            /**
             * Callback function for the completion of map data installation.
             * @param rootMapPackage
             * @param resultCode
             */
            @Override
            public void onInstallMapPackagesComplete(MapPackage rootMapPackage,
                                                     MapLoader.ResultCode resultCode) {
                if (resultCode == MapLoader.ResultCode.OPERATION_SUCCESSFUL) {
                    // TODO: start downloading radio maps
                    // Map data has been successfully installed
                    Toast.makeText(getApplicationContext(), R.string.m_data_install_complete, Toast.LENGTH_LONG)
                            .show();
                    finishResult(RESULT_OK);
                } else {
                    // The operation has failed for map data installation
                    Toast.makeText(getApplicationContext(), R.string.m_data_install_fail, Toast.LENGTH_LONG)
                            .show();
                    finishResult(RESULT_CANCELED);
                }
            }

            /**
             * Callback function for the completion of map data updates.
             * @param rootMapPackage
             * @param resultCode
             */
            @Override
            public void onPerformMapDataUpdateComplete(MapPackage rootMapPackage,
                                                       MapLoader.ResultCode resultCode) {
                // TODO: replace
                Toast.makeText(getApplicationContext(), R.string.update_complete, Toast.LENGTH_LONG)
                        .show();
                finishResult(RESULT_OK);
            }

            @Override
            public void onInstallationSize(long l, long l1) {
                // Unused
            }

            @Override
            public void onUninstallMapPackagesComplete(MapPackage rootMapPackage,
                                                       MapLoader.ResultCode resultCode) {
                // Unused
            }
        };
    }

    /**
     * Returns the MapPackage for the given state name within the USA.
     *
     * @param name the name of the state
     * @param m_currentMapPackageList the current map package list
     * @return the MapPackage containing the desired state
     */
    private MapPackage getStateMapPackage (String name, ArrayList<MapPackage> m_currentMapPackageList) {
        MapPackage state = null;
        for (MapPackage continent : m_currentMapPackageList) {
            if (continent.getTitle().equals("North and Central America")) {
                for (MapPackage country : continent.getChildren()) {
                    if (country.getTitle().equals("USA")) {
                        for (MapPackage states : country.getChildren()) {
                            if (states.getTitle().equals(name)) {
                                state = states;
                            }
                        }
                    }
                }
            }
        }
        return state;
    }
}
