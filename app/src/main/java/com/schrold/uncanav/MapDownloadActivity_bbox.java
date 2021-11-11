// TODO: download radio map data

package com.schrold.uncanav;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.here.android.mpa.common.ApplicationContext;
import com.here.android.mpa.common.GeoBoundingBox;
import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.common.MapEngine;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.odml.MapLoader;
import com.here.android.mpa.odml.MapPackage;
import com.here.android.mpa.prefetcher.MapDataPrefetcher;

import java.util.Locale;

/**
 * Helper activity to download and cache map data to the user's device.
 */
public class MapDownloadActivity_bbox extends AppCompatActivity
        implements MapLoader.MapPackageAtCoordinateListener {

    // The MapLoader object for downloading map data
    private MapLoader m_mapLoader;

    // The TextView object for displaying information below the progress bar
    private TextView textView;

    // The ProgressBar object for displaying download progress
    private ProgressBar progressBar;

    private long diskSize;
    private MapDataPrefetcher m_mapDataPrefetcher;
    private GeoBoundingBox m_geoBoundingBox;

    /**
     * Begins the activity by setting the view, initializing variables, and
     * starting the download process.
     *
     * @param savedInstanceState saved instance state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize layout
        setContentView(R.layout.activity_load);
        textView = findViewById(R.id.errorText);
        progressBar = findViewById(R.id.progressBar);
        textView.setText(getResources().getString(R.string.initializing));

        startDownload();
    }

    /**
     * Wrapper method to finish the activity.
     *
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
                    System.out.println("Map engine initialized for download.");

                    // MapLoader object for downloading map data
                    m_mapLoader = MapLoader.getInstance();

                    // Begin the MapPackage download process for the MapLoader
                    m_mapLoader.addMapPackageAtCoordinateListener(MapDownloadActivity_bbox.this);

                    // Wait for the device to be connected to the internet.
                    while (!isNetworkAvailable()) {
                        System.out.println("ERROR: Network unavailable.");
                    }

                    m_mapLoader.getMapPackageAtCoordinate(new GeoCoordinate(35.615330, -82.5659220));
                } else {
                    // Error initializing MapEngine, finish
                    Toast.makeText(getApplicationContext(), String.format("Error: %s", error.toString()), Toast.LENGTH_LONG)
                            .show();
                    finishResult(RESULT_CANCELED);
                }
            }
        });
    }

    @Override
    public void onGetMapPackageAtCoordinateComplete(@Nullable MapPackage mapPackage, @Nullable GeoCoordinate geoCoordinate, MapLoader.ResultCode resultCode) {
        if (resultCode == MapLoader.ResultCode.OPERATION_SUCCESSFUL) {
            textView.setText(R.string.map_pack_retrieved);
            // Prefetch map using bounding box

            m_mapDataPrefetcher = MapDataPrefetcher.getInstance();

            m_mapDataPrefetcher.addListener(new PrefetchMapDataListener());

            GeoCoordinate m_geoCoordinate = new GeoCoordinate(35.614395, -82.566610);
            m_geoBoundingBox = new GeoBoundingBox(m_geoCoordinate, 20000, 20000);

            m_mapDataPrefetcher.clearMapDataCache();

            m_mapDataPrefetcher.estimateMapDataSize(m_geoBoundingBox);
        } else {
            // The map loader is busy, just try again
            textView.setText(resultCode.toString());
            m_mapLoader.cancelCurrentOperation();
            m_mapLoader.getMapPackageAtCoordinate(new GeoCoordinate(35.615330, -82.5659220));
        }
    }

    /**
     * Contains callback functions for the MapDataPrefetcher.
     */
    private class PrefetchMapDataListener extends MapDataPrefetcher.Adapter {
        @Override
        public void onDataSizeEstimated(int requestId, boolean success, long dataSizeKB) {
            if (success) {
                diskSize = dataSizeKB;
                textView.setText(String.format(Locale.US, "0%% [%.1f MB]", diskSize/1000.0));
                m_mapDataPrefetcher.fetchMapData(m_geoBoundingBox);
            } else {
                m_mapDataPrefetcher.estimateMapDataSize(m_geoBoundingBox);
            }
        }

        @Override
        public void onProgress(int requestId, float progress) {
            System.out.println("progress" + progress);
            if ((int) progress >= 100) {
                Toast.makeText(getApplicationContext(), R.string.m_data_install_complete, Toast.LENGTH_SHORT).show();
            } else {
                progressBar.setProgress((int) progress);
                textView.setText(String.format(Locale.US, "%d%% [%.1f MB]", (int) progress, diskSize/1000.0));
            }
        }

        @Override
        public void onStatus(int requestId, PrefetchStatus status) {
            if (status == PrefetchStatus.PREFETCH_SUCCESS) {
                // Fetched map package
                finishResult(RESULT_OK);
            } else if (status == PrefetchStatus.PREFETCH_FAILURE || status == PrefetchStatus.PREFETCH_CANCELLED){
                // Failure, retry
                m_mapDataPrefetcher.cancelAllRequests();
                m_mapDataPrefetcher.fetchMapData(m_geoBoundingBox);
            }
            System.out.println(status.toString());
        }

        @Override
        public void onCachePurged(boolean b) {
            // Unused
        }
    }

    /**
     * Determine if the user's device is connected to the internet.
     *
     * @return true if network is available
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}
