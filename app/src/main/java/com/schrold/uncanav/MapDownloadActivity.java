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

import androidx.appcompat.app.AppCompatActivity;

import com.here.android.mpa.common.ApplicationContext;
import com.here.android.mpa.common.GeoBoundingBox;
import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.common.MapEngine;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.prefetcher.MapDataPrefetcher;

import java.util.Locale;

public class MapDownloadActivity extends AppCompatActivity {

    // The TextView object for displaying information below the progress bar
    private TextView textView;

    // The ProgressBar object for displaying download progress
    private ProgressBar progressBar;

    private MapDataPrefetcher m_mapDataPrefetcher;
    private GeoBoundingBox m_geoBoundingBox;

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
                    System.out.println("ENGINE INIT");

                    if (MapEngine.isInitialized()) {
                        prefetchMap();
                    } else {
                        System.out.println("MapEngine not initialized");
                        finishResult(RESULT_CANCELED);
                    }
                } else {
                    // Error initializing MapEngine, finish
                    Toast.makeText(getApplicationContext(), String.format("Error: %s", error.toString()), Toast.LENGTH_LONG)
                            .show();
                    finishResult(RESULT_CANCELED);
                }
            }
        });
    }

    private class PrefetchMapDataListener extends MapDataPrefetcher.Adapter {
        @Override
        public void onDataSizeEstimated(int requestId, boolean success, long dataSizeKB) {
            super.onDataSizeEstimated(requestId, success, dataSizeKB);
            textView.setText(String.format("%d MB", (int) dataSizeKB/1000));
        }

        @Override
        public void onProgress(int requestId, float progress) {
            super.onProgress(requestId, progress);
            if (progress < 100) {
                progressBar.setProgress((int) progress);
                textView.setText(String.format(Locale.US, "%d%%", (int) progress));
            } else {
                textView.setText(R.string.installing);
            }
        }

        @Override
        public void onStatus(int requestId, PrefetchStatus status) {
            super.onStatus(requestId, status);
            if (status == PrefetchStatus.PREFETCH_SUCCESS) {
                // do something
                finishResult(RESULT_OK);
            } else if (status == PrefetchStatus.PREFETCH_FAILURE || status == PrefetchStatus.PREFETCH_CANCELLED){
                System.out.println("Prefetch error");
                m_mapDataPrefetcher.fetchMapData(m_geoBoundingBox);
            }
        }

        @Override
        public void onCachePurged(boolean b) {
            super.onCachePurged(b);
        }
    }

    private void prefetchMap() {
        // Prefetch map
        m_mapDataPrefetcher = MapDataPrefetcher.getInstance();
        m_mapDataPrefetcher.addListener(new PrefetchMapDataListener());

        GeoCoordinate m_geoCoordinate = new GeoCoordinate(35.614395, -82.566610);
        m_geoBoundingBox = new GeoBoundingBox(m_geoCoordinate, 100, 100);

        MapDataPrefetcher.Request request = m_mapDataPrefetcher.fetchMapData(m_geoBoundingBox);
    }
}
