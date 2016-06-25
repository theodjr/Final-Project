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

package com.projecttango.experiments.quickstartjava;


import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import com.projecttango.quickstartjava.R;


/**
 * Main Activity for the Tango Java Quickstart. Demonstrates establishing a
 * connection to the {@link Tango} service and printing the {@link //TangoPose}
 * data to the LogCat. Also demonstrates Tango lifecycle management through
 * {@link TangoConfig}.
 */
public class MainActivity extends Activity{
    private static final String TAG = MainActivity.class.getSimpleName();

    private static boolean loop = true;

    private static double[] translation = new double[3];
    private static double[] rotation = new double[4];
    private static TangoXyzIjData xyzIjpass = new TangoXyzIjData();
    private static TangoXyzIjData xyzIjprev = new TangoXyzIjData();

    BufferedWriter out = null;
    private static  long sendTime = 0;

    private static boolean interupt=false;
    private Tango mTango;
    private TangoConfig mConfig;
    private boolean mIsTangoServiceConnected;
    private UsbService usbService;

    private MyHandler mHandler;
    private static byte[] header={(byte)0xfa,(byte)0xfb};
    private static byte[] sync0={header[0],header[1],3,0,0,0};
    private static byte[] sync1={header[0],header[1],3,1,0,1};
    private static byte[] sync2={header[0],header[1],3,2,0,2};

    private int count;
    public static byte[] incommingMessage;
    private static boolean recieved=false;
    private static Integer N=0;

    private static byte[] listen=null;
    private static byte[] commandByte;
    private static byte[] buffer={0,0};
    public Context context;
    private static ByteArrayOutputStream outgoing=new ByteArrayOutputStream();
    static private ByteArrayOutputStream incomming = new ByteArrayOutputStream( );
    static private byte argInt =0x3b;
    static private byte argNInt=0x1b;

    static public TextView term;
    public  ArrayList<TangoCoordinateFramePair> framePairs;

    private static double xclose=0;
    private static double yclose=0;
    private static double zclose=0;
    private static double xsum=0;
    private static double esum=0;
    private static double ersum=0;
    private static double zsum=0;
    public static Double Theta=null;
    public static Double phi=null;
    public static Double x;
    public static Double z;
    private static Double p=0.0;
    private static double pd=0;
    private static double pprev=0;
    private static Double p2=0.0;
    private static double pd2=0;
    private static double pprev2=0;
    private static double pi=0;
    private static double pi2=0;
    private static double d=1;
    private Double control2=0.0;
    private Double control=0.0;
    public File file;
    private static boolean switcher=false;
    private static TangoPoseData posePass;
    private static TangoPoseData posePrev;
    public static Double et;

    //To Adjust the starting values of gain or location change the following variables
    private static double k=.4;
    private static double kd=0;
    private static double ki=0.0;
    private static double k2=3;
    private static double kd2=0;
    private static double ki2=0.05;
    private static double dx=-1.0;
    private static Double dz=-1.0;


    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case UsbService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                    Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_NO_USB: // NO USB CONNECTED
                    Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                    Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };
    private static class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;
        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    //When message recieved from serial port run this code.
                    byte[] dataBytes =  (byte[]) msg.obj;
                    try{
                    incomming.write( dataBytes );}catch (Exception e){Toast.makeText(mActivity.get(),"Data Write error",Toast.LENGTH_SHORT).show();}
                    byte[] b=incomming.toByteArray();
                    Integer len=b.length;
                    if(len>0) {
                        if (b[0] == header[0]) {
                            if(len>1) {
                                if (b[1]==header[1]) {
                                    if(len>2){
                                        byte Nb=b[2];
                                        N=(int)Nb;
                                        if(len>=N){
                                           incommingMessage= incomming.toByteArray();
                                            if(Arrays.equals(listen, incommingMessage)){
                                                recieved=true;
                                            }
                                        }
                                    }
                                }else {
                                    Toast.makeText(mActivity.get(),"Header 1 Recieved but not Header 2",Toast.LENGTH_SHORT).show();
                                    incomming.reset();
                                }
                            }
                        }else {
                            incomming.reset();
                        }
                    }
                    if(len>N+2){
                        incomming.reset();
                    }
                case UsbService.CTS_CHANGE:

                    break;
                case UsbService.DSR_CHANGE:
                    Toast.makeText(mActivity.get(), "DSR_CHANGE",Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }

    //Usb service
    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(mHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };
    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        term=(TextView)findViewById(R.id.terminal);
        mHandler = new MyHandler(this);

        TextView ktxt=(TextView)findViewById(R.id.ktxt);
        TextView kdtxt=(TextView)findViewById(R.id.kdtxt);
        TextView kitxt=(TextView)findViewById(R.id.kitxt);
        TextView k2txt=(TextView)findViewById(R.id.k2txt);
        TextView kd2txt=(TextView)findViewById(R.id.kd2txt);
        TextView ki2txt=(TextView)findViewById(R.id.ki2txt);

        ktxt.setText(Double.toString(k));
        k2txt.setText(Double.toString(k2));
        kdtxt.setText(Double.toString(ki2));
        kd2txt.setText(Double.toString(dx));
        kitxt.setText(Double.toString(ki));
        ki2txt.setText(Double.toString(dz));

        // Instantiate Tango client
        mTango = new Tango(this);

        // Set up Tango configuration for motion tracking
        // If you want to use other APIs, add more appropriate to the config
        // like: mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true)
        mConfig = mTango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);


        Log.i(TAG, "OnCreate");



    }

    @Override
    protected void onResume() {
        super.onResume();
        // Lock the Tango configuration and reconnect to the service each time
        // the app
        // is brought to the foreground.
        setFilters();  // Start listening notifications from UsbService
        startService(UsbService.class, usbConnection, null); // Start UsbService(if it was not started before) and Bind it

        if (!mIsTangoServiceConnected) {
            try {
                setTangoListeners();
            } catch (TangoErrorException e) {
                Toast.makeText(this, "Tango Error! Restart the app!",
                        Toast.LENGTH_SHORT).show();
            }
            try {
                mTango.connect(mConfig);
                mIsTangoServiceConnected = true;
            } catch (TangoOutOfDateException e) {
                Toast.makeText(getApplicationContext(),
                        "Tango Service out of date!", Toast.LENGTH_SHORT)
                        .show();
            } catch (TangoErrorException e) {
                Toast.makeText(getApplicationContext(),
                        "Tango Error! Restart the app!", Toast.LENGTH_SHORT)
                        .show();
            }
        }
        Log.i(TAG, "onResume");

    }

    @Override
    protected void onPause() {
        super.onPause();
        // When the app is pushed to the background, unlock the Tango
        // configuration and disconnect
        // from the service so that other apps will behave properly.

        try {
            mTango.disconnect();
            mIsTangoServiceConnected = false;
            loop = false;
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), "Tango Error!",
                    Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        if(UsbService.SERVICE_CONNECTED){
            try{
                unregisterReceiver(mUsbReceiver);
                stopService(new Intent(this, UsbService.class));}catch (Exception e){e.printStackTrace();}
            unbindService(usbConnection);}
    }

    private void setTangoListeners() {
        // Select coordinate frame pairs
        framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));

        // Add a listener for Tango pose data
        mTango.connectListener(framePairs, new OnTangoUpdateListener() {

            @SuppressLint("DefaultLocale")
            @Override
            public void onPoseAvailable(TangoPoseData pose) {
            }

            @Override
            public void onXyzIjAvailable(final TangoXyzIjData xyzIj) {
                xyzIjpass=xyzIj;
            }

            @Override
            public void onTangoEvent(TangoEvent arg0) {
                // Ignoring TangoEvents
            }

            @Override
            public void onFrameAvailable(int arg0) {
                // Ignoring onFrameAvailable Events
            }
        });
    }

//Setup Buttons to switch to other programs
    public void button3dclick(View view) {
        Intent intent = new Intent(this, PointCloudActivity.class);
        loop=false;
        startActivity(intent);
    }
    public void buttonCameraclick(View view) {
        Intent intent = new Intent(this, CameraDisplay.class);
        loop=false;
        startActivity(intent);
    }
    public void switchMode(View view) {
        switcher=!switcher;
                if(switcher){
       term.setText("3d data");}
        else {
                    term.setText("Inertial");
                }
    }

    public void reset(View view){
        loop=false;
        interupt=true;

        TextView ktxt=(TextView)findViewById(R.id.ktxt);
        TextView kdtxt=(TextView)findViewById(R.id.kdtxt);
        TextView kitxt=(TextView)findViewById(R.id.kitxt);
        TextView k2txt=(TextView)findViewById(R.id.k2txt);
        TextView kd2txt=(TextView)findViewById(R.id.kd2txt);
        TextView ki2txt=(TextView)findViewById(R.id.ki2txt);

        ktxt.setText(Double.toString(k));
        k2txt.setText(Double.toString(k2));
        kdtxt.setText(Double.toString(ki2));
        kd2txt.setText(Double.toString(dx));
        kitxt.setText(Double.toString(ki));
        ki2txt.setText(Double.toString(dz));
    }

    public void robotInitialize(View view){
        if(switcher) {
            if (!loop) {
                Toast.makeText(this, "Robot Initiallizing", Toast.LENGTH_SHORT).show();
                incomming.reset();
                //This Code sets up the control on a separate thread
                loop = true;
                recieved = false;
                interupt = false;
                Runnable r = new Runnable() {
                    @Override
                    public void run() {

                        //Control
                        try {
                            //Ping robot
                            write(sync0);

                            listen = sync0;
                            recieved = false;
                            Long t0 = System.currentTimeMillis();
                            while (!recieved & !interupt) {
                                if (System.currentTimeMillis() - t0 > 1000) {
                                    interupt = true;
                                }
                            }
                            Thread.sleep(200);
                            listen = sync1;
                            recieved = false;
                            t0 = System.currentTimeMillis();
                            write(sync1);


                            while (!recieved & !interupt) {
                                if (System.currentTimeMillis() - t0 > 1000) {
                                    interupt = true;
                                }
                            }
                            Thread.sleep(200);
                            write(sync2);

                            Thread.sleep(200);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        try {
                            byte n;
                            byte commandNumber;
                            byte datatype;
                            byte command2;
                            write(sync1);
                            Thread.sleep(1000);
                            n = 6;
                            commandNumber = 4;
                            datatype = argInt;
                            command2 = 1;
                            outgoing.reset();
                            outgoing.write(header);
                            outgoing.write(n);
                            outgoing.write(commandNumber);

                            outgoing.write(datatype);
                            outgoing.write(command2);
                            outgoing.write(0);
                            commandByte = checkSum(outgoing);
                            write(commandByte);
                            outgoing.reset();
                            Thread.sleep(1000);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        count = 0;
                        Integer n2 = 1;
                        if (switcher) {

                            Double Kalxsto;
                            Double Kalxdsto;
                            Double Kalzsto ;
                            Double Kalzdsto;


                            try {
                                do {
                                    String filename = "Output" + n2.toString() + ".txt";
                                    file = new File(Environment.getExternalStoragePublicDirectory(
                                            Environment.DIRECTORY_DOWNLOADS), filename);
                                    n2 = n2 + 1;
                                } while (file.exists());

                                file.setReadable(true, false);
                                file.setWritable(true, false);

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            try {
                                out = new BufferedWriter(new OutputStreamWriter(
                                        new FileOutputStream(file), "UTF-8"));
                                FileWriter fw = new FileWriter(file);
                                out.write("Time\tx\tz");
                            } catch (Exception e) {
                                Toast.makeText(context, "ERROR", Toast.LENGTH_SHORT).show();
                            }


                            while (loop & !interupt) {
                                try {


                                    count++;
                                    byte datatype;
                                    byte n = 6;
                                    byte commandNumber = 13;
                                    if (count % 2 == 1) {
                                        datatype = argInt;
                                    } else {
                                        datatype = argNInt;
                                    }
                                    byte command2 = (byte) (4);
                                    outgoing.reset();
                                    outgoing.write(header);
                                    outgoing.write(n);
                                    outgoing.write(commandNumber);
                                    outgoing.write(datatype);
                                    outgoing.write(command2);
                                    outgoing.write(0);
                                    commandByte = checkSum(outgoing);

                                    write(commandByte);

                                    outgoing.reset();
                                    Thread.sleep(1500);


                                    if (count >= 3) {
                                        loop = false;
                                    }


                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            loop = true;


                            while (loop & !interupt) {
                                //Control Code

                                while (System.currentTimeMillis() < sendTime + 20) {
                                }
                                if (xyzIjpass.timestamp > xyzIjprev.timestamp) {
                                    double dt = xyzIjpass.timestamp - xyzIjprev.timestamp;
                                    xyzIjprev = xyzIjpass;

                                    FloatBuffer xyz = xyzIjprev.xyz;
                                    pprev = p;
                                    pprev2 = p2;
                                    Long t = System.currentTimeMillis();

                                    for (int i = 0; i < xyzIjprev.xyzCount; i += 3) {
                                        p = 10000.0;
                                        float x = xyz.get(i);
                                        float y = xyz.get(i + 1);
                                        float z = xyz.get(i + 2);
                                        if (z < p & Math.abs(y) < 1) {
                                            p = (double) z;
                                            xclose = x;
                                            yclose = y;
                                            zclose = z;
                                        }
                                    }
                                    if (p <= 10000) {
                                        count = 0;
                                        xsum = 0;
                                        zsum = 0;
                                        for (int i = 0; i < xyzIjprev.xyzCount; i += 3) {
                                            float x = xyz.get(i);
                                            float y = xyz.get(i + 1);
                                            float z = xyz.get(i + 2);
                                            if ((x - xclose) * (x - xclose) + (y - yclose) * (y - yclose) + (z - zclose) * (z - zclose) < .5) {
                                                xsum = xsum + x;
                                                zsum = zsum + z;
                                                count++;
                                            }
                                        }
                                        Double xsto = xsum / count;
                                        Double zsto = zsum / count;

                                        d = 1.5 * 1.5;
                                        p = (xsto * xsto) + (zsto) * (zsto) - d;
                                        p2 = -1 * Math.atan2(xsum, zsum);


                                        xyzIjprev = xyzIjpass;

                                        pd = (p - pprev) / dt;
                                        pi = pi + p;
                                        control = k * p + kd * pd + ki * pi;
                                        double sum = 0;
                                        pd2 = (p2 - pprev2) / dt;
                                        pi2 = pi2 + p2 * dt;
                                        control2 = k2 * p2 + kd2 * pd2 + ki2 * pi2;

                                        try {
                                            double vr = State.vr(control, control2);
                                            double vl = State.vl(control, control2);
                                            Double time = xyzIjpass.timestamp;
                                            t = System.currentTimeMillis() - t;
                                            Log.i(TAG, "Count: " + t.toString());
                                            Integer t2 = xyzIjprev.xyzCount;
                                            Log.i(TAG, "Time: " + t2.toString());
                                            try {
                                                String s1 = time.toString();
                                                String s2 = xsto.toString();
                                                String s3 = zsto.toString();


                                                out.write(s1 + "\t" + s2 + "\t" + s3 + "\t" );
                                                out.newLine();

                                            } catch (Exception e) {
                                                Log.e(TAG, Log.getStackTraceString(e));
                                                ;
                                            }
                                            while (System.currentTimeMillis() - sendTime < 30) {
                                            }
                                            if (vl > 0 && vr > 0) {
                                                vlvrControlOutput(vr, vl);
                                                sendTime = System.currentTimeMillis();
                                            } else {

                                                rControlOutput(control2);
                                                sendTime = System.currentTimeMillis();


                                                while (System.currentTimeMillis() - sendTime < 30) {
                                                }
                                                zControlOutput(control);
                                            }
                                            sendTime = System.currentTimeMillis();

                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                    } else {
                                        Runnable r = new Runnable() {
                                            @Override
                                            public void run() {
                                                term.setText("Invalid p");
                                            }
                                        };
                                        runOnUiThread(r);
                                    }

                                } else {
                                    write(sync0);
                                    try {
                                        Thread.sleep(2);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }

                            }
                            try {
                                Log.d(TAG, "Output close");
                                try {
                                    out.close();
                                } catch (Exception e) {
                                    Toast.makeText(context, "ERROR", Toast.LENGTH_SHORT).show();
                                }
                                Intent intent =
                                        new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                                intent.setData(Uri.fromFile(file));
                                sendBroadcast(intent);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {

                        }
                        interupt = false;
                        Log.d(TAG, "Output close");
                        try {
                            out.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Intent intent =
                                new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        intent.setData(Uri.fromFile(file));
                        sendBroadcast(intent);
                    }
                };
                Thread controlThread = new Thread(r);
                controlThread.start();
            } else {
                Toast.makeText(this, "Robot already initiallized", Toast.LENGTH_SHORT).show();
            }
        }else {
            if (loop == false) {
                Toast.makeText(this, "Robot Initiallizing", Toast.LENGTH_SHORT).show();
                incomming.reset();
                //This Code sets up the control on a separate thread
                loop = true;
                recieved = false;
                interupt = false;
                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                    try{
                        //Control
                        try {
                            //Ping robot
                            write(sync0);

                            listen = sync0;
                            recieved = false;
                            Long t0 = System.currentTimeMillis();
                            while (!recieved & !interupt) {
                                if (System.currentTimeMillis() - t0 > 1000) {
                                    interupt = true;
                                }
                            }
                            Thread.sleep(200);
                            listen = sync1;
                            recieved = false;
                            t0 = System.currentTimeMillis();
                            write(sync1);


                            while (!recieved & !interupt) {
                                if (System.currentTimeMillis() - t0 > 1000) {
                                    interupt = true;
                                }
                            }
                            Thread.sleep(200);
                            write(sync2);

                            Thread.sleep(200);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        try {
                            byte n;
                            byte commandNumber;
                            byte datatype;
                            byte command2;
                            write(sync1);
                            Thread.sleep(1000);
                            n = 6;
                            commandNumber = 4;
                            datatype = argInt;
                            command2 = 1;
                            outgoing.reset();
                            outgoing.write(header);
                            outgoing.write(n);
                            outgoing.write(commandNumber);

                            outgoing.write(datatype);
                            outgoing.write(command2);
                            outgoing.write(0);
                            commandByte = checkSum(outgoing);
                            write(commandByte);
                            outgoing.reset();
                            Thread.sleep(1000);
                            Integer a = 1;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        count = 0;
                        BufferedWriter output = null;
                        Integer n2 = 1;
                        if (switcher) {

                        } else {
                            //This is the Inertial code
                            loop = true;
                            try {
                                do {
                                    String filename = "Output" + n2.toString() + ".txt";
                                    file = new File(Environment.getExternalStoragePublicDirectory(
                                            Environment.DIRECTORY_DOWNLOADS), filename);
                                    n2 = n2 + 1;
                                } while (file.exists());

                                file.setReadable(true, false);
                                file.setWritable(true, false);

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            try {
                                out = new BufferedWriter(new OutputStreamWriter(
                                        new FileOutputStream(file), "UTF-8"));
                                FileWriter fw = new FileWriter(file);
                                out.write("Time"+ "\t"+"X"+"\t"+"Z");

                            } catch (Exception e) {
                                e.printStackTrace();
                            }


                            while (loop & !interupt) {
                                try {


                                    count++;
                                    byte datatype;
                                    byte n = 6;
                                    byte commandNumber = 13;
                                    if (count % 2 == 1) {
                                        datatype = argInt;
                                    } else {
                                        datatype = argNInt;
                                    }
                                    byte command2 = (byte) (4);
                                    outgoing.reset();
                                    outgoing.write(header);
                                    outgoing.write(n);
                                    outgoing.write(commandNumber);
                                    outgoing.write(datatype);
                                    outgoing.write(command2);
                                    outgoing.write(0);
                                    commandByte = checkSum(outgoing);

                                    write(commandByte);

                                    outgoing.reset();
                                    Thread.sleep(1500);
                                    if (count >= 3) {
                                        loop = false;
                                        mTango.resetMotionTracking();
                                        esum=0;
                                        ersum=0;

                                    }

                                }catch(Exception e){e.printStackTrace();}}
                            posePrev=mTango.getPoseAtTime(0,framePairs.get(0));
                            loop=true;
                            Runnable f = new Runnable() {
                                @Override
                                public void run() {
                                    term.setText("Looping"+loop+interupt);
                                }
                            };
                            runOnUiThread(f);
                            sendTime=System.currentTimeMillis();
                            while (loop & !interupt) {

                                posePass=mTango.getPoseAtTime(0,framePairs.get(0));
                                while (System.currentTimeMillis() < sendTime + 25) {
                                }
                                double dt=posePass.timestamp-posePrev.timestamp;
                                if (dt>0) {
                                    posePrev = posePass;
                                    translation = posePrev.translation;
                                    rotation = posePrev.rotation;
                                    Double time = posePrev.timestamp;
                                     x = translation[0];
                                     z = translation[1];
                                    double[] q=rotation;
                                    d=1.5;
                                    phi = Math.atan2(2 * (q[0] * q[1] + q[2] * q[3]), 1 - 2 * (q[1] * q[1] + q[2] * q[2]));
                                    Theta = Math.atan2(-x + dx, -z + dz);


                                    //Inertial control
                                    double e=(Math.abs(x - dx) + Math.abs(z - dz));
                                     et=Theta+phi;
                                    if(et<-3.14159265359){et=et+2*3.14159265359;}
                                    if(et>3.14159265359){et=et-2*3.14159265359;}
                                    if(e<2&e>.3){e=2;}
                                    esum=0;
                                    ersum=ersum-(et)*dt;
                                    control = k *e+ki*esum ;
                                    if(e<.05){
                                        control=0.0;
                                        control2=0.0;
                                        interupt=true;
                                    }
                                    control2 = -k2 *( et)+ki2*ersum;

                                    double vr = State.vr(control, control2);//Large control2 means more vr Turn towards negative x
                                    double vl = State.vl(control, control2);

                                    if (true) {
                                        vlvrControlOutput(vr, vl);
                                        sendTime = System.currentTimeMillis();
                                        f = new Runnable() {
                                            @Override
                                            public void run() {
                                                term.setText(x.toString()+" , "+z.toString());
                                            }
                                        };
                                        runOnUiThread(f);
                                    } else {

                                        zControlOutput(control);

                                        sendTime = System.currentTimeMillis();
                                        f = new Runnable() {
                                            @Override
                                            public void run() {
                                                term.setText("v Theta control");
                                            }
                                        };
                                        runOnUiThread(f);

                                        while (System.currentTimeMillis() - sendTime < 35) {
                                        }
                                        rControlOutput(control2);
                                    }
                                    sendTime = System.currentTimeMillis();


                                    try {
                                        out.write(time.toString() + "\t" + x.toString() + "\t" + z.toString() + "\t" + control2.toString());
                                        out.newLine();
                                    } catch (Exception aljs) {
                                        aljs.printStackTrace();
                                    }
                                }else{ f = new Runnable() {
                                    @Override
                                    public void run() {
                                        term.setText("No pose update");
                                    }
                                };
                                    runOnUiThread(f);
                                }
                            }
                            interupt = false;
                        }
                        interupt = false;

                        try {
                            out.close();
                        } catch (Exception e) {
                           e.printStackTrace();
                        }
                        Intent intent2 =
                                new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        intent2.setData(Uri.fromFile(file));
                        sendBroadcast(intent2);


                    }catch(Exception e){e.printStackTrace();}
                    }
                };
                Thread controlThread = new Thread(r);
                controlThread.start();
            }
        }
    }

    public void kclick(View view) {
      k=k+.1;
        TextView ktxt=(TextView)findViewById(R.id.ktxt);
        TextView kdtxt=(TextView)findViewById(R.id.kdtxt);
        TextView kitxt=(TextView)findViewById(R.id.kitxt);
        TextView k2txt=(TextView)findViewById(R.id.k2txt);
        TextView kd2txt=(TextView)findViewById(R.id.kd2txt);
        TextView ki2txt=(TextView)findViewById(R.id.ki2txt);

        ktxt.setText(Double.toString(k));
        k2txt.setText(Double.toString(k2));
        kdtxt.setText(Double.toString(ki2));
        kd2txt.setText(Double.toString(dx));
        kitxt.setText(Double.toString(ki));
        ki2txt.setText(Double.toString(dz));
    }
    public void k2click(View view) {
        k2=k2+1;
        TextView ktxt=(TextView)findViewById(R.id.ktxt);
        TextView kdtxt=(TextView)findViewById(R.id.kdtxt);
        TextView kitxt=(TextView)findViewById(R.id.kitxt);
        TextView k2txt=(TextView)findViewById(R.id.k2txt);
        TextView kd2txt=(TextView)findViewById(R.id.kd2txt);
        TextView ki2txt=(TextView)findViewById(R.id.ki2txt);

        ktxt.setText(Double.toString(k));
        k2txt.setText(Double.toString(k2));
        kdtxt.setText(Double.toString(ki2));
        kd2txt.setText(Double.toString(dx));
        kitxt.setText(Double.toString(ki));
        ki2txt.setText(Double.toString(dz));
    }

    public void kdclick(View view) {
        ki2=ki2+.01;
        TextView ktxt=(TextView)findViewById(R.id.ktxt);
        TextView kdtxt=(TextView)findViewById(R.id.kdtxt);
        TextView kitxt=(TextView)findViewById(R.id.kitxt);
        TextView k2txt=(TextView)findViewById(R.id.k2txt);
        TextView kd2txt=(TextView)findViewById(R.id.kd2txt);
        TextView ki2txt=(TextView)findViewById(R.id.ki2txt);

        ktxt.setText(Double.toString(k));
        k2txt.setText(Double.toString(k2));
        kdtxt.setText(Double.toString(ki2));
        kd2txt.setText(Double.toString(dx));
        kitxt.setText(Double.toString(ki));
        ki2txt.setText(Double.toString(dz));
    }

    public void kd2click(View view) {
        dx=dx+1;
        TextView ktxt=(TextView)findViewById(R.id.ktxt);
        TextView kdtxt=(TextView)findViewById(R.id.kdtxt);
        TextView kitxt=(TextView)findViewById(R.id.kitxt);
        TextView k2txt=(TextView)findViewById(R.id.k2txt);
        TextView kd2txt=(TextView)findViewById(R.id.kd2txt);
        TextView ki2txt=(TextView)findViewById(R.id.ki2txt);

        ktxt.setText(Double.toString(k));
        k2txt.setText(Double.toString(k2));
        kdtxt.setText(Double.toString(ki2));
        kd2txt.setText(Double.toString(dx));
        kitxt.setText(Double.toString(ki));
        ki2txt.setText(Double.toString(dz));
    }

    public void kiclick(View view) {
        ki=ki+.01;
        TextView ktxt=(TextView)findViewById(R.id.ktxt);
        TextView kdtxt=(TextView)findViewById(R.id.kdtxt);
        TextView kitxt=(TextView)findViewById(R.id.kitxt);
        TextView k2txt=(TextView)findViewById(R.id.k2txt);
        TextView kd2txt=(TextView)findViewById(R.id.kd2txt);
        TextView ki2txt=(TextView)findViewById(R.id.ki2txt);

        ktxt.setText(Double.toString(k));
        k2txt.setText(Double.toString(k2));
        kdtxt.setText(Double.toString(ki2));
        kd2txt.setText(Double.toString(dx));
        kitxt.setText(Double.toString(ki));
        ki2txt.setText(Double.toString(dz));
    }

    public void ki2click(View view) {
        dz=dz+1;
        TextView ktxt=(TextView)findViewById(R.id.ktxt);
        TextView kdtxt=(TextView)findViewById(R.id.kdtxt);
        TextView kitxt=(TextView)findViewById(R.id.kitxt);
        TextView k2txt=(TextView)findViewById(R.id.k2txt);
        TextView kd2txt=(TextView)findViewById(R.id.kd2txt);
        TextView ki2txt=(TextView)findViewById(R.id.ki2txt);

        ktxt.setText(Double.toString(k));
        k2txt.setText(Double.toString(k2));
        kdtxt.setText(Double.toString(ki2));
        kd2txt.setText(Double.toString(dx));
        kitxt.setText(Double.toString(ki));
        ki2txt.setText(Double.toString(dz));
    }

    public void ResetGains(View view){
        k=.4;
        kd=0;
        ki=0.0;
        k2=3;
        dx=-1.0;
        kd2=0;
        ki2=0.05;
        dz=-2.0;

        TextView ktxt=(TextView)findViewById(R.id.ktxt);
        TextView kdtxt=(TextView)findViewById(R.id.kdtxt);
        TextView kitxt=(TextView)findViewById(R.id.kitxt);
        TextView k2txt=(TextView)findViewById(R.id.k2txt);
        TextView kd2txt=(TextView)findViewById(R.id.kd2txt);
        TextView ki2txt=(TextView)findViewById(R.id.ki2txt);

        ktxt.setText(Double.toString(k));
        k2txt.setText(Double.toString(k2));
        kdtxt.setText(Double.toString(ki2));
        kd2txt.setText(Double.toString(kd2));
        kitxt.setText(Double.toString(ki));
        ki2txt.setText(Double.toString(dz));
    }

    public static byte[] checkSum(ByteArrayOutputStream in){
         byte[] out;
        try{
        in.write(buffer);}catch (Exception e){e.printStackTrace();}
        out=in.toByteArray();
        int c=calculateCheckSum(in.toByteArray());
        out[out.length-2] = (byte)((c >>> 8) & 0xff);
        out[out.length-1] = (byte)(c & 0xff);
        return out;

    }

    public static int calculateCheckSum(byte[] packet) {
        int n = packet.length-5;
        int i = 3;
        int c = 0;
        while(n > 1) {
            c += ((packet[i] & 0xff)<<8 | (packet[i+1] & 0xff));
            c = (c & 0xffff);
            n -= 2;
            i += 2;
        }
        if(n > 0)
            c = (c ^ (packet[i] & 0xff));
        return c;
    }
    private void write(final byte[] serial){
       usbService.write(serial);
    }

    private void rControlOutput(double control){
//Take control output and print using serial
        outgoing.reset();
        byte[] command;
        byte[] commandInt=new byte[2];
        byte commandNumber = 21;
        byte datatype;
        if(control<0){
            datatype=argNInt;}
        else{ datatype=argInt;}
        int command2= (int)(Math.round(Math.abs(control)));
        commandInt[0] = (byte) (command2 & 0xFF);
        commandInt[1] = (byte) ((command2 >> 8) & 0xFF);
        try {
            outgoing.write(header);
            outgoing.write(6);
            outgoing.write(commandNumber);
            outgoing.write(datatype);
            outgoing.write(commandInt);
            command=checkSum(outgoing);
            write(command);
        }catch (Exception e){e.printStackTrace();}
    }
    private void zControlOutput(double control){
        //Take control output and print using serial
        //Controls forward velocity
        outgoing.reset();
        byte[] command;
        byte[] commandInt=new byte[2];
        byte commandNumber = 11;
        byte datatype;
        if(control<0){
         datatype=argNInt;}
        else{ datatype=argInt;}
        int command2= (int)(Math.round(Math.abs(control)));
        commandInt[0] = (byte) (command2 & 0xFF);
        commandInt[1] = (byte) ((command2 >> 8) & 0xFF);
        try {
            outgoing.write(header);
            outgoing.write(6);
            outgoing.write(commandNumber);
            outgoing.write(datatype);
            outgoing.write(commandInt);

            command=checkSum(outgoing);
            write(command);
        }catch (Exception e){e.printStackTrace();}
    }
    private void vlvrControlOutput(double controlR, double controlL){
        //Take control output and print using serial
        //Takes a right wheel speed and left wheel speed outputs using vel2
        outgoing.reset();
        byte[] command;
        byte[] commandInt=new byte[2];
        byte commandNumber = 32;
        byte datatype;
        if(controlR<0){
            controlR=0;
        }
        if(controlL<0){
            controlL=0;
        }
        if(controlR>255){controlR=255;}
        if(controlL>255){controlL=255;}
            datatype=argInt;
        byte command2= (byte)(Math.round(Math.abs(controlR)));
        byte command3= (byte)(Math.round(Math.abs(controlL)));
        commandInt[0] = (byte) (command2 & 0xFF);
        commandInt[1] = (byte) (command3 & 0xFF);

        try {
            outgoing.write(header);
            outgoing.write(6);
            outgoing.write(commandNumber);
            outgoing.write(datatype);
            outgoing.write(command2);
            outgoing.write(command3);

            command=checkSum(outgoing);
            write(command);
        }catch (Exception e){e.printStackTrace();}
    }
}
