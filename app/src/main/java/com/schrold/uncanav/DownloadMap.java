package com.schrold.uncanav;


import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.here.android.mpa.common.ApplicationContext;
import com.here.android.mpa.common.MapEngine;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.odml.MapLoader;
import com.here.android.mpa.odml.MapPackage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class DownloadMap extends AppCompatActivity {

    private MapLoader m_mapLoader;
    private ArrayList<MapPackage> m_currentMapPackageList;
    private MapPackage NCMapPackage;
    private MapLoader.Listener m_listener;
    private boolean checked = false;
    private boolean updateAvailable = false;
    public static ExecutorService executorService;
    private TextView errorText;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen);
        errorText = (TextView) findViewById(R.id.errorText);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        initMapEngine();
    }



    private void initMapEngine() {
        MapEngine.getInstance().init(new ApplicationContext(this), new OnEngineInitListener() {
            @Override
            public void onEngineInitializationCompleted(Error error) {
                if (error == Error.NONE) {
                    /*
                     * Similar to other HERE Android SDK objects, the MapLoader can only be
                     * instantiated after the MapEngine has been initialized successfully.
                     */
                    System.out.println("engine initialized");
                    m_mapLoader = MapLoader.getInstance();
                    initListener();
                    m_mapLoader.addListener(m_listener);
                    // getting packages initially
                    System.out.println("getting map packages");
                    m_mapLoader.getMapPackages();
                } else {
                    // fucked up
                    System.out.println("map engine not init");
                }
            }
        });
    }

    // gets the state mappackage for a given state name
    private MapPackage getState(String name) {
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


    private void initListener() {
        // Listener to monitor all activities of MapLoader.
        m_listener = new MapLoader.Listener() {
            @Override
            public void onProgress(int i) {
                if (i < 100) {
                    progressBar.setProgress(i);
                    errorText.setText(i+"%");
                    System.out.println(i);
                } else {
                    //m_progressTextView.setText("Installing...");
                    errorText.setText("Installing...");
                }
            }

            @Override
            public void onInstallationSize(long l, long l1) {
            }

            @Override
            public void onGetMapPackagesComplete(@Nullable MapPackage mapPackage, MapLoader.ResultCode resultCode) {
                /*
                 * Please note that to get the latest MapPackage status, the application should always
                 * use the rootMapPackage that being returned here. The same applies to other listener
                 * call backs.
                 */
                if (resultCode == MapLoader.ResultCode.OPERATION_SUCCESSFUL) {
                    errorText.setText("Map Packages Retrieved");
                    List<MapPackage> children = mapPackage.getChildren();
                    m_currentMapPackageList = new ArrayList<>(children);
                    // if NC is not installed, install it
                    if (getState("North Carolina").getInstallationState() ==
                            MapPackage.InstallationState.NOT_INSTALLED) {
                        List<Integer> idList = new ArrayList<>();
                        idList.add(getState("North Carolina").getId());
                        m_mapLoader.installMapPackages(idList);
                    } else {
                        // else, check for updates to the map data
                        m_mapLoader.checkForMapDataUpdate();
                    }
                } else {
                    errorText.setText(resultCode.toString());
                    // The map loader is still busy, just try again.
                    m_mapLoader.getMapPackages();
                }
            }


            @Override
            public void onCheckForUpdateComplete(boolean updateAvailable, String current, String update,
                                                 MapLoader.ResultCode resultCode) {
                checked = true;
                if (updateAvailable) {
                    errorText.setText("Updates available");
                    m_mapLoader.performMapDataUpdate();
                }
                else {
                    errorText.setText("No updates available");
                    Intent intent = getIntent();
                    setResult(RESULT_OK, intent);
                    System.out.println("finished");
                    finish();
                }
            }

            @Override
            public void onPerformMapDataUpdateComplete(MapPackage rootMapPackage,
                                                       MapLoader.ResultCode resultCode) {
                errorText.setText("Update completed");
                Intent intent = getIntent();
                setResult(RESULT_OK, intent);
                System.out.println("finished");
                finish();
            }

            @Override
            public void onInstallMapPackagesComplete(MapPackage rootMapPackage,
                                                     MapLoader.ResultCode resultCode) {
                //m_progressTextView.setText("");
                if (resultCode == MapLoader.ResultCode.OPERATION_SUCCESSFUL) {
                    Toast.makeText(getApplicationContext(), "Installation is completed", Toast.LENGTH_LONG).show();
                    Intent intent = getIntent();
                    setResult(RESULT_OK, intent);
                    errorText.setText("Installation completed");
                    finish();
                } else if (resultCode == MapLoader.ResultCode.OPERATION_CANCELLED) {
                    Toast.makeText(getApplicationContext(), "Installation is cancelled...", Toast.LENGTH_LONG)
                            .show();
                }
            }

            @Override
            public void onUninstallMapPackagesComplete(MapPackage rootMapPackage,
                                                       MapLoader.ResultCode resultCode) {
            }
        };
    }
}
