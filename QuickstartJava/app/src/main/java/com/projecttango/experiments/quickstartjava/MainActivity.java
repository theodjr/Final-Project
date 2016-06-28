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

import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;

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


    private static boolean loop = true;

    private static double[] translation = new double[3];
    private static double[] rotation = new double[4];



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
    public static byte[] incomingMessage;
    private static boolean recieved=false;
    private static Integer N=0;
    private static byte[] listen=null;
    private static byte[] commandByte;
    private static byte[] buffer={0,0};

    private static ByteArrayOutputStream outgoing=new ByteArrayOutputStream();
    static private ByteArrayOutputStream incoming = new ByteArrayOutputStream( );
    static private byte argInt =0x3b;
    static private byte argNInt=0x1b;
    public File file;

    private static TangoPoseData posePass;
    private static TangoPoseData posePrev;
    public static Double et;
    public  ArrayList<TangoCoordinateFramePair> framePairs;


    Runnable f;


    private static double ersum=0;

    public static Double Theta=null;
    public static Double phi=null;
    public static Double x;
    public static Double z;






    private Double control2=0.0;
    private Double control=0.0;

    //To Adjust the starting values of gain or location change the following variables
    private static double k=.4;
    private static double k2=3;
    private static double ki2=0.05;
    private static double dx=-1.0;
    private static Double dz=-1.0;

    //Ids
    static public TextView ktxt;
    static public TextView k2txt;
    static public TextView ki2txt;
    static public TextView dxtxt;
    static public TextView dztxt;
    static public TextView term;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Sets the view as the activity_main.xml file
        setContentView(R.layout.activity_main);

        //Set the textview ids
         ktxt=(TextView)findViewById(R.id.ktxt);
         k2txt=(TextView)findViewById(R.id.k2txt);
         ki2txt=(TextView)findViewById(R.id.ki2txt);
         dxtxt=(TextView)findViewById(R.id.dxtxt);
         dztxt=(TextView)findViewById(R.id.dztxt);
        term=(TextView)findViewById(R.id.terminal);

        //Initialize the start text
        ktxt.setText(Double.toString(k));
        k2txt.setText(Double.toString(k2));
        ki2txt.setText(Double.toString(ki2));
        dxtxt.setText(Double.toString(dx));
        dztxt.setText(Double.toString(dz));

        // Instantiate Tango client
        mTango = new Tango(this);
        // Set up Tango configuration for motion tracking and depth sensing
        mConfig = mTango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Lock the Tango configuration and reconnect to the service each time the app is brought to the foreground.

        // Start listening notifications from UsbService
        setFilters();
        // Start UsbService and Bind it
        startService(UsbService.class, usbConnection, null);

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
    }

    @Override
    protected void onPause() {
        super.onPause();
        // When the app is pushed to the background, unlock the Tango configuration and disconnect from the service so that other apps will behave properly.
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
              //Ignored
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

    public void reset(View view){
        loop=false;
        interupt=true;

        ktxt.setText(Double.toString(k));
        k2txt.setText(Double.toString(k2));
        ki2txt.setText(Double.toString(ki2));
        dxtxt.setText(Double.toString(dx));
        dztxt.setText(Double.toString(dz));
    }
    public void kclick(View view) {
        k=k+.1;

        ktxt.setText(Double.toString(k));
        k2txt.setText(Double.toString(k2));
        ki2txt.setText(Double.toString(ki2));
        dxtxt.setText(Double.toString(dx));
        dztxt.setText(Double.toString(dz));
    }
    public void k2click(View view) {
        k2=k2+1;

        ktxt.setText(Double.toString(k));
        k2txt.setText(Double.toString(k2));
        ki2txt.setText(Double.toString(ki2));
        dxtxt.setText(Double.toString(dx));
        dztxt.setText(Double.toString(dz));
    }

    public void ki2click(View view) {
        ki2=ki2+.01;

        ktxt.setText(Double.toString(k));
        k2txt.setText(Double.toString(k2));
        ki2txt.setText(Double.toString(ki2));
        dxtxt.setText(Double.toString(dx));
        dztxt.setText(Double.toString(dz));
    }

    public void dxclick(View view) {
        dx=dx+1;

        ktxt.setText(Double.toString(k));
        k2txt.setText(Double.toString(k2));
        ki2txt.setText(Double.toString(ki2));
        dxtxt.setText(Double.toString(dx));
        dztxt.setText(Double.toString(dz));
    }

    public void dzclick(View view) {
        dz=dz+1;

        ktxt.setText(Double.toString(k));
        k2txt.setText(Double.toString(k2));
        ki2txt.setText(Double.toString(ki2));
        dxtxt.setText(Double.toString(dx));
        dztxt.setText(Double.toString(dz));
    }

    public void ResetGains(View view){
        k=.4;
        k2=3;
        ki2=0.05;
        dx=-1.0;
        dz=-2.0;

        ktxt.setText(Double.toString(k));
        k2txt.setText(Double.toString(k2));
        ki2txt.setText(Double.toString(ki2));
        dxtxt.setText(Double.toString(dx));
        dztxt.setText(Double.toString(dz));
    }

    public void robotInitialize(View view){

        if (!loop) {
            Toast.makeText(this, "Robot Initiallizing", Toast.LENGTH_SHORT).show();
            incoming.reset();
            //This Code sets up the control on a separate thread
            loop = true;
            recieved = false;
            interupt = false;
            Runnable r = new Runnable() {
                @Override
                public void run() {
                try{
                    //Communication start
                    try {
                        //Ping robot
                        usbService.write(sync0);
                        listen = sync0;
                        recieved = false;
                        Long t0 = System.currentTimeMillis();
                        //Wait for reply
                        while (!recieved & !interupt) {
                            if (System.currentTimeMillis() - t0 > 1000) {
                                interupt = true;
                                //If timeout then reset
                            }
                        }
                        Thread.sleep(200);

                        //Ping robot
                        listen = sync1;
                        recieved = false;
                        t0 = System.currentTimeMillis();
                        usbService.write(sync1);
                        //Wait for reply
                        while (!recieved & !interupt) {
                            if (System.currentTimeMillis() - t0 > 1000) {
                                interupt = true;
                                //If timeout then reset
                            }
                        }
                        Thread.sleep(200);
                        //Last ping. Response ignored
                        usbService.write(sync2);
                        Thread.sleep(200);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    try {
                        byte n;
                        byte commandNumber;
                        byte datatype;
                        byte command2;
                        //Send the "Open" command which is the same as sync1. Delay to allow user to position device as desired.
                        usbService.write(sync1);
                        Thread.sleep(1500);

                        //Send command #4: Enables motors. Amigobot will not move without this command.
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
                        usbService.write(commandByte);
                        outgoing.reset();
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }


                    Integer n2 = 1;
                    try {
                        do {
                            String filename = "Output" + n2.toString() + ".txt";
                            file = new File(Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOWNLOADS), filename);
                            n2 = n2 + 1;
                        } while (file.exists());

                        file.setReadable(true, false);
                        file.setWritable(true, false);

                        out = new BufferedWriter(new OutputStreamWriter(
                                new FileOutputStream(file), "UTF-8"));
                        FileWriter fw = new FileWriter(file);
                        out.write("Time"+ "\t"+"X"+"\t"+"Z");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    count = 0;
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
                            usbService.write(commandByte);
                            outgoing.reset();
                            Thread.sleep(1500);
                            if (count >= 4) {
                                loop = false;
                                mTango.resetMotionTracking();
                                ersum=0;
                            }

                        }catch(Exception e){e.printStackTrace();}
                    }

                    posePrev=mTango.getPoseAtTime(0,framePairs.get(0));
                    loop=true;
                    sendTime=System.currentTimeMillis();
                    while (loop & !interupt) {
                        while (System.currentTimeMillis() < sendTime + 25) {
                        }
                        posePass=mTango.getPoseAtTime(0,framePairs.get(0));
                        double dt=posePass.timestamp-posePrev.timestamp;
                        if (dt>0) {
                            posePrev = posePass;
                            Double time = posePrev.timestamp;

                            //Extract x and z from pose data
                            translation = posePrev.translation;
                             x = translation[0];
                             z = translation[1];

                            //Extract phi from pose data. Calculate theta
                            rotation = posePrev.rotation;
                            double[] q=rotation;
                            phi = Math.atan2(2 * (q[0] * q[1] + q[2] * q[3]), 1 - 2 * (q[1] * q[1] + q[2] * q[2]));
                            Theta = Math.atan2(-x + dx, -z + dz);

                            //Inertial controller
                            double e=(Math.abs(x - dx) + Math.abs(z - dz));
                             et=Theta+phi;
                            if(et<-3.14159265359){et=et+2*3.14159265359;}
                            if(et>3.14159265359){et=et-2*3.14159265359;}
                            if(e<2&e>.15){e=2;}

                            //Check if converged
                            if(e<.05){
                                control=0.0;
                                control2=0.0;
                                interupt=true;
                            }

                            //Control outputs
                            ersum=ersum-(et)*dt;
                            control = k *e;
                            control2 = -k2 *( et)+ki2*ersum;

                            //Calculate independent wheel velocities. Large control2 means more vr turn towards negative x
                            double vr = State.vr(control, control2);
                            double vl = State.vl(control, control2);

                            //Write to robot
                            vlvrControlOutput(vr, vl);
                            sendTime = System.currentTimeMillis();

                            //Update UI
                            f = new Runnable() {
                                @Override
                                public void run() {
                                    term.setText(x.toString()+" , "+z.toString());
                                }
                            };
                            runOnUiThread(f);

                            //Write values to file
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

                    out.close();
                    Intent intent2 = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    intent2.setData(Uri.fromFile(file));
                    sendBroadcast(intent2);

                }catch(Exception e){
                    e.printStackTrace();}
                }
            };
            Thread controlThread = new Thread(r);
            controlThread.start();
        }
    }

    private void vlvrControlOutput(double controlR, double controlL){
        //Take control output and print using serial
        //Takes a right wheel speed and left wheel speed outputs using vel2
        if(controlR<0){
            controlR=0;
        }
        if(controlL<0){
            controlL=0;
        }
        if(controlR>255){
            controlR=255;
        }
        if(controlL>255){
            controlL=255;
        }
        outgoing.reset();

        //Initialize outgoing variables
        byte commandNumber = 32;
        byte datatype=argInt;;

        //Convert left and right wheel velocities to single byte form. Truncate anything over 1 byte.
        byte command2= (byte)(Math.round(Math.abs(controlR)));
        byte command3= (byte)(Math.round(Math.abs(controlL)));
        byte[] commandInt=new byte[2];
        commandInt[0] = (byte) (command2 & 0xFF);
        commandInt[1] = (byte) (command3 & 0xFF);

        //Setup full outgoing byte array. Write to usb
        try {
            outgoing.write(header);
            outgoing.write(6);
            outgoing.write(commandNumber);
            outgoing.write(datatype);
            outgoing.write(commandInt);
            byte[] command=checkSum(outgoing);
            usbService.write(command);
        }catch (Exception e){e.printStackTrace();}
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
                        //Add incoming serial comunication (dataBytes) to a buffered writer (incoming)
                        incoming.write( dataBytes );}catch (Exception e){Toast.makeText(mActivity.get(),"Data Write error",Toast.LENGTH_SHORT).show();}
                    byte[] b=incoming.toByteArray();
                    Integer len=b.length;
                    if(len>0) {
                        if (b[0] == header[0]) {
                            if(len>1) {
                                if (b[1]==header[1]) {
                                    if(len>2){
                                        byte Nb=b[2];
                                        N=(int)Nb;
                                        if(len>=N){
                                            incomingMessage= incoming.toByteArray();
                                            if(Arrays.equals(listen, incomingMessage)){
                                                recieved=true;
                                            }
                                        }
                                    }
                                }else {
                                    Toast.makeText(mActivity.get(),"Header 1 Recieved but not Header 2",Toast.LENGTH_SHORT).show();
                                    incoming.reset();
                                }
                            }
                        }else {
                            incoming.reset();
                        }
                    }
                    if(len>N+2){
                        incoming.reset();
                    }
                case UsbService.CTS_CHANGE:

                    break;
                case UsbService.DSR_CHANGE:
                    Toast.makeText(mActivity.get(), "DSR_CHANGE",Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }


}

final class State {
    static private double R=.05;
    static private double L=.28;

    public static double vr(double v,double omega){
        return (2*v+omega*L)/(2*R);
    }
    public static double vl(double v, double omega){
        return (2*v-omega*L)/(2*R);
    }
}
