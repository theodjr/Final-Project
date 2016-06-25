package com.projecttango.experiments.quickstartjava;

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


        import java.util.ArrayList;

        import android.app.Activity;
        import android.content.Context;
        import android.content.Intent;
        import android.opengl.GLSurfaceView;
        import android.os.Bundle;
        import android.util.Log;
        import android.view.SurfaceView;
        import android.view.SurfaceHolder;
        import android.view.View;
        import android.view.View.OnClickListener;

        import android.widget.Button;
        import android.widget.LinearLayout.LayoutParams;
        import android.widget.Toast;
        import android.view.ViewGroup;
        import android.hardware.Camera;
        import android.widget.LinearLayout;

        import com.google.atap.tangoservice.Tango;
        import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
        import com.google.atap.tangoservice.TangoCameraIntrinsics;
        import com.google.atap.tangoservice.TangoCameraPreview;
        import com.google.atap.tangoservice.TangoConfig;
        import com.google.atap.tangoservice.TangoCoordinateFramePair;
        import com.google.atap.tangoservice.TangoEvent;
        import com.google.atap.tangoservice.TangoPoseData;
        import com.google.atap.tangoservice.TangoXyzIjData;
        import com.projecttango.quickstartjava.R;

/**
 * An example showing the usage of TangoCameraPreview class
 * Usage of TangoCameraPreviewClass:
 * To use this class, we first need initialize the TangoCameraPreview class with the activity's
 * context and connect to the camera we want by using connectToTangoCamera class.Once the connection
 * is established we need to manually update the TangoCameraPreview's texture by using the
 * onFrameAvailable callbacks.
 * Note:
 * To use TangoCameraPreview class we need to ask the user permissions for MotionTracking
 * at the minimum level. This is because in Java all the call backs such as
 * onPoseAvailable,onXyzIjAvailable, onTangoEvents, onFrameAvailable are set together at once.
 */
public class CameraDisplay extends Activity  implements SurfaceHolder.Callback {
    private TangoCameraPreview tangoCameraPreview;
    private Tango mTango;
    private int camid;
    private boolean mIsConnected;
    private boolean mIsPermissionGranted;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cameradata);
        tangoCameraPreview = new TangoCameraPreview(this);
        mTango = new Tango(this);
        startActivityForResult(
                Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_MOTION_TRACKING),
                Tango.TANGO_INTENT_ACTIVITYCODE);






    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == Tango.TANGO_INTENT_ACTIVITYCODE) {
            // Make sure the request was successful
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Motion Tracking Permissions Required!",
                        Toast.LENGTH_SHORT).show();
                finish();
            } else {
                startCameraPreview();
                mIsPermissionGranted = true;
            }
        }
    }

    // Camera Preview
    private void startCameraPreview() {
        // Connect to color camera
        tangoCameraPreview.connectToTangoCamera(mTango,
                TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
        // Use default configuration for Tango Service.
        TangoConfig config = mTango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        mTango.connect(config);
        mIsConnected = true;
        Intent intent=new Intent(this,MainActivity.class);
        addContentView(tangoCameraPreview, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        Button b = new Button(this);
        b.setText("Back");

        this.addContentView(b,
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        b.setOnClickListener(new OnClickListener(){
            public void onClick(View v)
            {

                try{
                    Context t=getBaseContext();
                    Intent intent=new Intent(t,MainActivity.class);

                startActivity(intent);}catch (Exception e){}
            }

        });

        // No need to add any coordinate frame pairs since we are not using
        // pose data. So just initialize.
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        mTango.connectListener(framePairs, new OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(TangoPoseData pose) {
                // We are not using OnPoseAvailable for this app
            }

            @Override
            public void onFrameAvailable(int cameraId) {

                // Check if the frame available is for the camera we want and
                // update its frame on the camera preview.
                if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
                    tangoCameraPreview.onFrameAvailable();


                }

            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                // We are not using OnPoseAvailable for this app
            }

            @Override
            public void onTangoEvent(TangoEvent event) {
                // We are not using OnPoseAvailable for this app
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mIsConnected) {
            mTango.disconnect();
            tangoCameraPreview.disconnectFromTangoCamera();
            mIsConnected = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mIsConnected && mIsPermissionGranted) {
            startCameraPreview();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }
}