/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.projecttango.examples.java.hellodepthperception;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Vector;

/**
 * Main activity class for the Depth Perception sample. Handles the connection to the {@link Tango}
 * service and propagation of Tango Point Cloud data to Layout view.
 */
public class HelloDepthPerceptionActivity extends Activity {

    private static final String TAG = HelloDepthPerceptionActivity.class.getSimpleName();

    private Tango mTango;
    private TangoConfig mConfig;

    private TextView mPoints_xView;
    private TextView mPoints_yView;
    private TextView mPoints_zView;
    private TextView mPoints_cView;
    private TextView mTotalPointsView;

    private Vector<Float> pointCloud;

    private Button scanCompleteButton;

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_depth_perception);

        mPoints_xView = (TextView) findViewById(R.id.x_points);
        mPoints_yView = (TextView) findViewById(R.id.y_points);
        mPoints_zView = (TextView) findViewById(R.id.z_points);
        mPoints_cView = (TextView) findViewById(R.id.c_value);
        mTotalPointsView = (TextView) findViewById(R.id.num_points);

        pointCloud = new Vector<>();

        scanCompleteButton = (Button) findViewById(R.id.scan_complete);
        scanCompleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveScan();
            }
        });

    }


    @Override
    protected void onResume() {
        super.onResume();

        verifyStoragePermissions(this);

        // Initialize Tango Service as a normal Android Service. Since we call mTango.disconnect()
        // in onPause, this will unbind Tango Service, so every time onResume gets called we
        // should create a new Tango object.
        mTango = new Tango(HelloDepthPerceptionActivity.this, new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready; this Runnable
            // will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only when there are no
            // UI thread changes involved.
            @Override
            public void run() {
                synchronized (HelloDepthPerceptionActivity.this) {
                    try {
                        mConfig = setupTangoConfig(mTango);
                        mTango.connect(mConfig);
                        startupTango();
                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, getString(R.string.exception_out_of_date), e);
                        showsToastAndFinishOnUiThread(R.string.exception_out_of_date);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                        showsToastAndFinishOnUiThread(R.string.exception_tango_error);
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, getString(R.string.exception_tango_invalid), e);
                        showsToastAndFinishOnUiThread(R.string.exception_tango_invalid);
                    }
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        synchronized (this) {
            try {
                mTango.disconnect();
            } catch (TangoErrorException e) {
                Log.e(TAG, getString(R.string.exception_tango_error), e);
            }
        }
    }

    /**
     * Sets up the Tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setupTangoConfig(Tango tango) {
        // Create a new Tango configuration and enable the Depth Sensing API.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);
        return config;
    }

    /**
     * Set up the callback listeners for the Tango Service and obtain other parameters required
     * after Tango connection.
     * Listen to new Point Cloud data.
     */
    private void startupTango() {
        // Lock configuration and connect to Tango.
        // Select coordinate frame pair.
        final ArrayList<TangoCoordinateFramePair> framePairs =
                new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));

        // Listen for new Tango data.
        mTango.connectListener(framePairs, new OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(final TangoPoseData pose) {
                // We are not using TangoPoseData for this application.
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                // We are not using onXyzIjAvailable for this app.
            }

            @Override
            public void onPointCloudAvailable(final TangoPointCloudData pointCloudData) {
                logPointCloud(pointCloudData);
                processPointCloud(pointCloudData);
            }

            @Override
            public void onTangoEvent(final TangoEvent event) {
                // Ignoring TangoEvents.
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // We are not using onFrameAvailable for this application.
            }
        });
    }

    private void processPointCloud(TangoPointCloudData pointCloudData) {
        // pointCloudBuffer contains float[] of x,y,z,c
        FloatBuffer pointCloudBuffer = pointCloudData.points;

        int numPoints = pointCloudData.numPoints;

        for (int i=0;i<numPoints;i++){
            pointCloud.add(pointCloudBuffer.get(i));
        }



        Log.i("x: ",String.valueOf(pointCloudBuffer.get(0)));
        Log.i("y: ",String.valueOf(pointCloudBuffer.get(1)));
        Log.i("z: ",String.valueOf(pointCloudBuffer.get(2)));
        Log.i("c: ",String.valueOf(pointCloudBuffer.get(3)));

        StringBuilder xStringBuilder = new StringBuilder();
        StringBuilder yStringBuilder = new StringBuilder();
        StringBuilder zStringBuilder = new StringBuilder();
        StringBuilder totalPointsStringBuilder = new StringBuilder();



        for (int i=0; i<numPoints; i=i+4){
            xStringBuilder.append("x point: " + pointCloudBuffer.get(i));
            mPoints_xView.setText(xStringBuilder.toString());
        }


        totalPointsStringBuilder.append("total points: " + numPoints);
        mTotalPointsView.setText(totalPointsStringBuilder.toString());

    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    private void saveScan() {

        if(isExternalStorageWritable()){
            Log.i("Tango Point Cloud: ","can write to memory");
        }

        Vector<Float> xpoints;
        xpoints = new Vector<>();
        Vector<Float> ypoints;
        ypoints = new Vector<>();
        Vector<Float> zpoints;
        zpoints = new Vector<>();

        // get x points
        for (int i=0; i<pointCloud.size(); i=i+4){
            xpoints.add(pointCloud.get(i));
        }



        // get y points
        for(int i=1; i<pointCloud.size(); i=i+4){
            ypoints.add(pointCloud.get(i));
        }

        // get z points
        for(int i=2; i<pointCloud.size(); i=i+4){
            zpoints.add(pointCloud.get(i));
        }

        Log.i("x size: ",String.valueOf(xpoints.size()));
        Log.i("y size: ", String.valueOf(ypoints.size()));
        Log.i("z size: ", String.valueOf(zpoints.size()));
        Log.i("total size: ",String.valueOf(pointCloud.size()));

        File sdcard = Environment.getExternalStorageDirectory();
        File dir = new File(sdcard.getAbsolutePath());
        dir.mkdir();
        File file = new File(dir, "Tango_pointcloud_1.txt");

        try {
            OutputStream os = new FileOutputStream(file);

            for (int i=0; i<xpoints.size(); i++){
                String row = String.valueOf(xpoints.get(i)) + " "
                        + String.valueOf(ypoints.get(i)) + " "
                        + String.valueOf(zpoints.get(i)) + "\r\n";
                os.write(row.getBytes());
            }

            os.close();

            Log.i("Tango Point Cloud: ","Stored Scan");

        } catch (java.io.IOException e) {
            e.printStackTrace();
        }


    }

    /**
     * Log the point count and the average depth of the given PointCloud data
     * in the Logcat as information.
     */
    private void logPointCloud(TangoPointCloudData pointCloudData) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Point count: " + pointCloudData.numPoints);
        stringBuilder.append(". Average depth (m): " +
            calculateAveragedDepth(pointCloudData.points, pointCloudData.numPoints));
        Log.i(TAG, stringBuilder.toString());
    }

    /**
     * Calculates the average depth from a point cloud buffer.
     */
    private float calculateAveragedDepth(FloatBuffer pointCloudBuffer, int numPoints) {
        float totalZ = 0;
        float averageZ = 0;
        if (numPoints != 0) {
            int numFloats = 4 * numPoints;
            for (int i = 2; i < numFloats; i = i + 4) {
                totalZ = totalZ + pointCloudBuffer.get(i);
            }
            averageZ = totalZ / numPoints;
        }
        return averageZ;
    }

    /**
     * Display toast on UI thread.
     *
     * @param resId The resource id of the string resource to use. Can be formatted text.
     */
    private void showsToastAndFinishOnUiThread(final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(HelloDepthPerceptionActivity.this,
                        getString(resId), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }



    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }
}
