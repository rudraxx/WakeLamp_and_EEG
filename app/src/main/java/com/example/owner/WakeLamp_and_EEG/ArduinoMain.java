package com.example.owner.WakeLamp_and_EEG;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.neurosky.thinkgear.TGDevice;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.UUID;


public class ArduinoMain extends ActionBarActivity {

//    //TObeimplemented:
// USe NFC for autoenable
// Use gears for stand
    // sync with sunrise time at any location to ensure the lighting starts at that time.

    //Declare buttons & editText
//    Button disconnect;

//    private EditText editText;

    //Memeber Fields
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    public OutputStream outStream = null;

    // UUID service - This is the type of Bluetooth device that the BT module is
    // It is very likely yours will be the same, if not google UUID for your manufacturer
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // MAC-address of Bluetooth module
    public String newAddress = null;

    private static TextView static_textView_Red;
    private static TextView static_textView_Green;
    private static TextView static_textView_Blue;

    TextView showTime,showHours,showMin,showSec;
    Calendar cal;
//    Button buttonTime;
    private int mInterval = 1000; // 5 seconds by default, can be changed later
    private Handler mHandler;

//    Button buttonSound;
    Button buttonStop;
    private MediaPlayer mediaPlayer;

    Button buttonSetHour;
    EditText editsethour;
    Integer rVal = 0;
    Integer gVal = 0;
    Integer bVal = 0;

    Integer intTime = 6;
    Integer intFlag=0;
    private volatile boolean done = false;

    // variables for calculating the sleep hours;
    public long startTime;
    public long difference;
    public long differenceInSeconds;
    public long differenceInMinutes;

// Data for the Neuosky EEG:
    BluetoothAdapter bluetoothAdapter;

    TextView textBlink;
    Button buttonConnect_EEG;

    TGDevice tgDevice;
    final boolean rawEnabled = false;
    int flag=0;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arduino_main);

        //getting the bluetooth adapter value and calling checkBTstate function
        btAdapter = BluetoothAdapter.getDefaultAdapter();
//        Toast.makeText(WakeLamp_and_EEG.this,"Starting ArduinoClass",Toast.LENGTH_SHORT).show();
        // Not sure if I should remove this call to checkBT state or not. Check and validate.
        // The checkBTstate function is being called in original app.
        checkBTState();

        showTime = (TextView) findViewById(R.id.tvTime);

        final Button disconnect = (Button) findViewById(R.id.bDisableBlueTooth);
        //Get MAC address from WakeUpLampActivity
        Intent intent = getIntent();
        newAddress = intent.getStringExtra(WakeLamp_and_EEG.EXTRA_DEVICE_ADDRESS);

        // Set up a pointer to the remote device using its address.
        BluetoothDevice device = btAdapter.getRemoteDevice(newAddress);

        //Attempt to create a bluetooth socket for comms
        try {
            btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);

        } catch (IOException e1) {
            Toast.makeText(getBaseContext(), "ERROR - Could not create Bluetooth socket", Toast.LENGTH_SHORT).show();
        }

        // Establish the connection.
        try {
            btSocket.connect();
        } catch (IOException e) {
            try {
                btSocket.close();        //If IO exception occurs attempt to close socket
            } catch (IOException e2) {
                Toast.makeText(getBaseContext(), "ERROR - Could not close Bluetooth socket", Toast.LENGTH_SHORT).show();
            }
        }

        // Create a data stream so we can talk to the device
        try {
            outStream = btSocket.getOutputStream();
        } catch (IOException e) {
            Toast.makeText(getBaseContext(), "ERROR - Could not create bluetooth outstream", Toast.LENGTH_SHORT).show();
        }
        //When activity is resumed, attempt to send a piece of junk data ('x') so that it will fail if not connected
        // i.e don't wait for a user to press button to recognise connection failure
//        sendData("x");
        mHandler = new Handler();
        startRepeatingTask();

        disconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {

                    sendData("<R" + 0 + "G" + 0 + "B" + 0 + ">" + "\n");
                    done = true;
                    btSocket.close();
//                    btAdapter.disable();
                    Toast.makeText(getBaseContext(), "Done disabling", Toast.LENGTH_SHORT).show();

                } catch (IOException e3) {
                    Toast.makeText(getBaseContext(), "ERROR - Failed to close Bluetooth socket", Toast.LENGTH_SHORT).show();
                }
            }
        });


// EEG Related:
        textBlink = (TextView)findViewById(R.id.tvBlink);
        textBlink.setText("No Blink");
        buttonConnect_EEG = (Button) findViewById(R.id.bConnectEEG);

        buttonConnect_EEG.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if(bluetoothAdapter == null) {
                    // Alert user that Bluetooth is not available
                    Toast.makeText(ArduinoMain.this, "Bluetooth not available", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }else {
        	/* create the TGDevice */
                    tgDevice = new TGDevice(bluetoothAdapter, handler);
                    Toast.makeText(ArduinoMain.this, "Starting Handler", Toast.LENGTH_LONG).show();
                }

                doStuff(buttonConnect_EEG);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

    }

    @Override
    public void onPause() {
        super.onPause();
//        startRepeatingTask();

    }

    @Override
    protected void onStop() {
        super.onStop();
//        startRepeatingTask();

    }

    @Override
    protected void onDestroy() {
        // EEG related

        try {
            tgDevice.close();
            Toast.makeText(this,"tgDevice Close Error",Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }

        super.onDestroy();
        //Pausing can be the end of an app if the device kills it or the user doesn't open it again
        //close all connections so resources are not wasted
        // But I cant close the socket in pause since the lamp needs to keep getting data
        // even when the app is in the background. Hence, closing it in the onDestroy().
        //Close BT socket to device
        try {
            sendData("<R" + 0 + "G" + 0 + "B" + 0 + ">" + "\n");
            done = true;
            btSocket.close();
        } catch (IOException e2) {
            Toast.makeText(getBaseContext(), "ERROR - Failed to close Bluetooth socket", Toast.LENGTH_SHORT).show();
        }
        mHandler.removeCallbacks(mStatusChecker);


    }

    //takes the UUID and creates a comms socket
    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {

        return device.createRfcommSocketToServiceRecord(MY_UUID);
    }

    //same as in device list activity
    private void checkBTState() {
        // Check device has Bluetooth and that it is turned on
        if (btAdapter == null) {
            Toast.makeText(getBaseContext(), "ERROR - Device does not support bluetooth", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            if (btAdapter.isEnabled()) {
            } else {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    // Method to send data
    private void sendData(String message) {
        byte[] msgBuffer = message.getBytes();

        try {
            //attempt to place data on the outstream to the BT device
            outStream.write(msgBuffer);
        } catch (IOException e) {
            //if the sending fails this is most likely because device is no longer there
            Toast.makeText(getBaseContext(), "ERROR - Sending Data error", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    void startRepeatingTask() {
        if(!done) {
            mStatusChecker.run();
        }
    }

    Runnable mStatusChecker = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            mHandler.postDelayed(mStatusChecker,mInterval);
        }
    };


    public void updateStatus(){
        cal = Calendar.getInstance();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showTime.setText("" + cal.getTime());
                // Set the RGB values
                // Red
                static_textView_Red = (TextView) findViewById(R.id.textView_Red);
                static_textView_Red.setText(rVal + "/255");

                // Green
                static_textView_Green = (TextView) findViewById(R.id.textView_Green);
                static_textView_Green.setText(gVal + "/255");

                // Blue
                static_textView_Blue = (TextView) findViewById(R.id.textView_Blue);
                static_textView_Blue.setText(bVal + "/255");

                // Use the value set in edit text editSetHour to set up start time for the LEDs.
            }
        });
    }


    // Function to write to disk.
    // Mode 1 - Record Start Sleep;
    // Mode 2-  Record Wake up ;
    // Mode 3 - Record Difference - Sleep hours;

    public void writeFile(String fileName, String inData, int mode)
    {
        File file;
        FileOutputStream outputStream;

        try {
//            file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),fileName);
            file = new File(Environment.getExternalStorageDirectory()+"/TestData",fileName);

//            outputStream = new FileOutputStream(file);
            outputStream = new FileOutputStream(file, true); // allow appending to the file.

//            outputStream.write(v.getText().toString().getBytes());
            if (mode==1){ // If recording the sleep start time.
                outputStream.write(System.getProperty("line.separator").getBytes()); // Start on new line;
                outputStream.write(("Sleep start time:  " + inData).getBytes()); // Print the current time.
                outputStream.write(("   ").getBytes()); // Spacing of 1 tabs

                Toast.makeText(this,"Start sleep time recorded",Toast.LENGTH_SHORT).show();

            }
            else if(mode==2){
                outputStream.write(("Wake up time:  " + inData).getBytes()); // Print the current time.
                Toast.makeText(this,"Wake time recorded",Toast.LENGTH_SHORT).show();
                outputStream.write(("   ").getBytes()); // Spacing of 1 tabs

            }
            else if(mode==3){
                outputStream.write(("Hours slept:  " + inData).getBytes()); // Print the current time.
                Toast.makeText(this,"Hours slept = "+inData,Toast.LENGTH_SHORT).show();

            }
            outputStream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private class OperationGetRequest extends AsyncTask<String,Void,String > {


        @Override
        protected String doInBackground(String... strings) {
            URL myURL;
            HttpURLConnection urlConnection = null;
            String response = "";
            try {
                myURL = new URL(strings[0]);
                urlConnection = (HttpURLConnection) myURL.openConnection();
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                response = readStream(in);
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                if (urlConnection!=null) {
                    urlConnection.disconnect();
                }
            }

            return response;
        }

//        @Override
//        protected void onPostExecute(String result) {
//            TextView textOutput = (TextView)findViewById(R.id.tvOutput);
//            textOutput.setText(result);
//        }

        private String readStream(InputStream in) {
            BufferedReader reader = null;
            StringBuffer response = new StringBuffer();
            try {
                reader = new BufferedReader(new InputStreamReader(in));
                String line = "";
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return response.toString();
        }
    }

    /**
     * Handles messages from TGDevice
     */
    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TGDevice.MSG_STATE_CHANGE:

                    switch (msg.arg1) {
                        case TGDevice.STATE_IDLE:
                            break;
                        case TGDevice.STATE_CONNECTING:
//                            textInfo.setText("Connecting...\n");
                            break;
                        case TGDevice.STATE_CONNECTED:
//                            textInfo.setText("Connected.\n");
                            tgDevice.start();
                            break;
                        case TGDevice.STATE_NOT_FOUND:
//                            textInfo.setText("Can't find\n");
                            break;
                        case TGDevice.STATE_NOT_PAIRED:
//                            textInfo.setText("not paired\n");
                            break;
                        case TGDevice.STATE_DISCONNECTED:
//                            textInfo.setText("Disconnected mang\n");
                    }

                    break;
                case TGDevice.MSG_POOR_SIGNAL:
                    //signal = msg.arg1;
//                    textPoorSignal.setText("PoorSignal: " + msg.arg1 + "\n");
                    break;
                case TGDevice.MSG_RAW_DATA:
                    //raw1 = msg.arg1;
                    //tv.append("Got raw: " + msg.arg1 + "\n");
                    break;
                case TGDevice.MSG_HEART_RATE:
//                    textHeartRate.setText("Heart rate: " + msg.arg1 + "\n");
                    break;
                case TGDevice.MSG_ATTENTION:
                    //att = msg.arg1;
//                    tv.append("Attention: " + msg.arg1 + "\n");
                    //Log.v("HelloA", "Attention: " + att + "\n");
                    break;
                case TGDevice.MSG_MEDITATION:

                    break;
                case TGDevice.MSG_BLINK:
                    textBlink.setText("Blink: " + msg.arg1 + "\n");
                    if(msg.arg1>60){
                        if (flag==0){
                            flag=1;
                            sendData("<R" + 100 + "G" + 0 + "B" + 0 + ">" + "\n");
                            static_textView_Red.setText("100/255");

                        }
                        else if (flag==1){
                            flag=0;
                            sendData("<R" + 0 + "G" + 0 + "B" + 0 + ">" + "\n");
                            static_textView_Red.setText("0/255");
                            }


                    }
                    break;
                case TGDevice.MSG_RAW_COUNT:
                    //tv.append("Raw Count: " + msg.arg1 + "\n");
                    break;
                case TGDevice.MSG_LOW_BATTERY:
                    Toast.makeText(getApplicationContext(), "Low battery!", Toast.LENGTH_SHORT).show();
                    break;
                case TGDevice.MSG_RAW_MULTI:
                    //TGRawMulti rawM = (TGRawMulti)msg.obj;
                    //tv.append("Raw1: " + rawM.ch1 + "\nRaw2: " + rawM.ch2);
                default:
                    break;
            }
        }
    };

    public void doStuff(View view) {
        if(tgDevice.getState() != TGDevice.STATE_CONNECTING && tgDevice.getState() != TGDevice.STATE_CONNECTED)
            tgDevice.connect(rawEnabled);
        //tgDevice.ena
    }
}

