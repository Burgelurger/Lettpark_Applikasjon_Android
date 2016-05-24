package com.example.hallv.LettPark;

/**
 * Created by hallv on 25.02.2016.
 */
/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.Set;


import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends Activity implements RadioGroup.OnCheckedChangeListener {
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int UART_PROFILE_READY = 10;
    public static final String TAG = "Lettpark";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private static final int STATE_OFF = 10;
    public static int check = 1, checkfinal = 1;
    public static PlaySound sinus = new PlaySound();//17.03.16

    TextView mRemoteRssiVal;
    RadioGroup mRg;
    private int mState = UART_PROFILE_DISCONNECTED;
    private UartService mService = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter = null;
   // private ListView messageListView; //bytter lista ut med et textfelt
    private TextView messageTextView;
    private ArrayAdapter<String> listAdapter;
    private Button btnConnectDisconnect,btnSend, button_unpair;
    private static String UnpairEnhetsnavn1 = "LettPark";
    private static String UnpairEnhetsnavn2 = "ParkLett";
    private boolean batteriflagg = false;


    private void unpairDevice(BluetoothDevice device) {
        try {
            if(device!=null){ //if mDevice is set up.
                Method m = device.getClass()
                        .getMethod("removeBond", (Class[]) null);
                m.invoke(device, (Object[]) null);
                showMessage(device.getName()+" er slettet fra listen over bondede enheter.");
            }else{
                Set<BluetoothDevice> pairedDeviceSet  = mBtAdapter.getBondedDevices();
                for(BluetoothDevice device2 : pairedDeviceSet){
                    if (device2.getType() == BluetoothDevice.DEVICE_TYPE_LE && (Objects.equals(device2.getName(), UnpairEnhetsnavn1)||Objects.equals(device2.getName(), UnpairEnhetsnavn2))){
                    Method m = device2.getClass()
                                .getMethod("removeBond", (Class[]) null);
                        m.invoke(device2, (Object[]) null);

                        showMessage(device2.getName()+" er slettet fra listen over bondede enheter.");

                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        VisAnim(0); //skjuler sensoranimasjon
        setVolumeControlStream(AudioManager.STREAM_MUSIC); // Setter fokus  for type media volumknapp på telefon demper eller øker


        // messageListView = (ListView) findViewById(R.id.listMessage);
        messageTextView= (TextView) findViewById(R.id.text_message);
        //listAdapter = new ArrayAdapter<String>(this, R.layout.message_detail);
       // messageListView.setAdapter(listAdapter);
       // messageListView.setDivider(null);
       // messageTextView.setAdapter(listAdapter);
        btnConnectDisconnect=(Button) findViewById(R.id.btn_select);
        service_init();
        button_unpair = (Button) findViewById(R.id.button_unpair);

        Set<BluetoothDevice> pairedDeviceSet  = mBtAdapter.getBondedDevices();

        if (pairedDeviceSet.size() > 0){
            for(BluetoothDevice device : pairedDeviceSet){
                    if (device.getType() == BluetoothDevice.DEVICE_TYPE_LE && (Objects.equals(device.getName(), UnpairEnhetsnavn1)||Objects.equals(device.getName(), UnpairEnhetsnavn2))){
                        button_unpair.setEnabled(true);
                        button_unpair.setTextColor(getResources().getColor(R.color.white));//070416 Aktiverer knappen dersom vi er bonda med enheter
                    }else{
                        button_unpair.setEnabled(false);
                        button_unpair.setTextColor(getResources().getColor(R.color.darkgray));
                    }
            }
        }else{
            button_unpair.setEnabled(false);
            button_unpair.setTextColor(getResources().getColor(R.color.darkgray));//070416 KAN ERSTATTES MED  button_unpair.setTextColor(Color.GRAY);//070416
        }


        // Handle Disconnect & Connect button
        btnConnectDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mBtAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                } else {
                    if (btnConnectDisconnect.getText().equals("KOPLE TIL")) {

                        //Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices

                        Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                    } else {
                        //Disconnect button pressed
                        if (mDevice != null) {
                            mService.disconnect();

                        }
                    }
                }


            }
        });
        button_unpair.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                unpairDevice(mDevice);
                button_unpair.setEnabled(false);
                button_unpair.setTextColor(getResources().getColor(R.color.darkgray));//070416
            }
        }); //Knappen kjører funksjonen unpairDevice og printer en beskjed på skjermen


        // Set initial UI state

    }


    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

        }

        public void onServiceDisconnected(ComponentName classname) {
            ////     mService.disconnect(mDevice);
            mService = null;
        }
    };

   /* private Handler mHandler = new Handler() {
        @Override

        //Handler events that received from UART service
        public void handleMessage(Message msg) {

        }
    };*/




    protected final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(final Context context, Intent intent) {
            final String action = intent.getAction();

            final Intent mIntent = intent;



            //*********************//

            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {


                runOnUiThread(new Runnable() {
                    public void run() {
                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "UART_CONNECT_MSG");
                        btnConnectDisconnect.setText("KOPLE FRA");
                        //button_unpair.setVisibility(View.INVISIBLE);
                        // edtMessage.setEnabled(true);
                        // btnSend.setEnabled(true);

                        button_unpair.setEnabled(false);
                        button_unpair.setTextColor(getResources().getColor(R.color.darkgray));//070416 Deaktiver knappen i tilkoplet modus


                        ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName() + " - klar");
                        // listAdapter.add("["+currentDateTimeString+"] Connected to: "+ mDevice.getName());
                        //messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                        messageTextView.setText("[" + currentDateTimeString + "] Koplet til: " + mDevice.getName());
                        mState = UART_PROFILE_CONNECTED;

                    }
                });
            }

            //*********************//
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "UART_DISCONNECT_MSG");
                        btnConnectDisconnect.setText("KOPLE TIL");
                        //edtMessage.setEnabled(false);
                        //  btnSend.setEnabled(false);
                        if(mDevice.getBondState() == BluetoothDevice.BOND_BONDED){
                            button_unpair.setEnabled(true);
                            button_unpair.setTextColor(getResources().getColor(R.color.white));//070416
                        }//Aktiver knapp dersom vi har BONDEDE enheter




                        ((TextView) findViewById(R.id.deviceName)).setText("Frakoplet");
                        messageTextView.setText("[" + currentDateTimeString + "] Frakoplet: " + mDevice.getName()); //Lagt tilav hallvard
                        // listAdapter.add("["+currentDateTimeString+"] Disconnected to: "+ mDevice.getName());

                        mState = UART_PROFILE_DISCONNECTED;
                        mService.close();
                        //setUiState();

                        VisAnim(0); //Skjuler sensoranimasjon
                        //01.04.16
                        checkfinal=1;
                        check=1;// Sørger for at dersom disconnect trykkes mid i en tilstand, så settes flaggene riktig, et must for at lyden skal fungere.

                    }
                });
            }

            //*********************//
            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                batteriflagg = true;
               if(mDevice.getBondState() == BluetoothDevice.BOND_BONDED){
                   mService.enableTXNotification();

               }else{
                   boolean bonded = false;
                   bonded = mDevice.createBond();
                   if (bonded){
                       mService.enableTXNotification();
                   }else{
                       showMessage("Pairing med denne enheten var mislykket.");
                       Log.e(TAG, "PAIRING FAILED");
                       mService.close();
                   }
               }
            }
            //*********************//

            final lyd sps = new lyd(1000,500);
            final lyd mps = new lyd(500,200);//pauselengde + tonelengde
            final lyd2 fps = new lyd2();



            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {

                VisAnim(1); //Viser parkeringsanimasjoner


                final byte[] txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);



                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            //int text = txValue[0]; //Endret: String text = new String(txValue, "UTF-8");
                            if(batteriflagg){ //Sjekker om første forsendelse indikerer lavt batteri.
                                char batteri = (char)txValue[0];
                                if(batteri == 'E'){
                                    showMessage("Batteriet er snart tomt og må lades.");
                                }//else{
                                    //showMessage("ALT VEL");
                               // }
                                batteriflagg=false;
                            }

                            int text = txValue[0];
                            byte b = (byte) text;
                            int i2 = b & 0xFF;          //snutten konverterer int til uint
                            System.out.println(i2);



                            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                            //listAdapter.add("["+currentDateTimeString+"] RX: "+i2);
                            //messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);
                            messageTextView.setText("[" + currentDateTimeString + "] RX: " + i2); //Lagt til av hallvard


                            if (i2 <= 255 & i2 > 100) {
                                AvstandAnim(1);
                                if (MainActivity.check != 0 & MainActivity.checkfinal != 0) {
                                    fps.start();
                                    MainActivity.check = 0;
                                    MainActivity.checkfinal = 0;
                                }
                            } else if (i2 <= 100 & i2 > 70) {
                                AvstandAnim(2);
                                MainActivity.checkfinal = 1;
                                if (MainActivity.check != 0) {
                                    mps.start();
                                    MainActivity.check = 0;
                                }
                            } else if (i2 <= 70 & i2 > 15) {
                                AvstandAnim(3);
                                MainActivity.checkfinal = 1;
                                if (MainActivity.check != 0) {
                                    sps.start();
                                    MainActivity.check = 0;
                                }
                           }
                            else {
                                checkfinal = 1;
                                AvstandAnim(0);
                            }


                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                });

            }


            //*********************//
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
                showMessage("Ennheten er ikke kjent. Kopler fra.");
                mService.disconnect();
            }




        }
    };

    public void AvstandAnim (int a){
        final ImageView imageView = (ImageView) findViewById(R.id.sensorroed); //29.02
        final ImageView imageView1 = (ImageView) findViewById(R.id.sensorgul);
        final ImageView imageView2 = (ImageView) findViewById(R.id.sensorgronn); //Fra animasjon erik
        final ImageView imageView3 = (ImageView) findViewById(R.id.advarsel);
        if (a==1){
            imageView.animate().alpha(0).setDuration(250);
            imageView1.animate().alpha(0).setDuration(250);
            imageView2.animate().alpha(0).setDuration(250);
            imageView3.animate().alpha(1).setDuration(250);
        }
        else if (a==2) {
            imageView.animate().alpha(1).setDuration(250);
            imageView1.animate().alpha(0).setDuration(250);
            imageView2.animate().alpha(0).setDuration(250);
            imageView3.animate().alpha(0).setDuration(250);
        }
        else if (a==3){
            imageView.animate().alpha(1).setDuration(250);
            imageView1.animate().alpha(1).setDuration(250);
            imageView2.animate().alpha(0).setDuration(250);
            imageView3.animate().alpha(0).setDuration(250);

        }
        else{
            imageView.animate().alpha(1).setDuration(250);
            imageView1.animate().alpha(1).setDuration(250);
            imageView2.animate().alpha(1).setDuration(250);//29.02.16
            imageView3.animate().alpha(0).setDuration(250);
        }
    }// kode for hvilket "felt" vi befinner oss i

    public void VisAnim (int a){
        final ImageView imageView = (ImageView) findViewById(R.id.sensorroed); //29.02
        final ImageView imageView1 = (ImageView) findViewById(R.id.sensorgul);
        final ImageView imageView2 = (ImageView) findViewById(R.id.sensorgronn); //Fra animasjon erik
        final ImageView imageView3 = (ImageView) findViewById(R.id.sensorhvit);
        final ImageView imageView4 = (ImageView) findViewById(R.id.sensorhvit2);
        final ImageView imageView5 = (ImageView) findViewById(R.id.sensorhvit3);
        final ImageView imageView6 = (ImageView) findViewById(R.id.advarsel);
        if (a==1){


            imageView.setVisibility(View.VISIBLE);
            imageView1.setVisibility(View.VISIBLE);
            imageView2.setVisibility(View.VISIBLE);
            imageView3.setVisibility(View.VISIBLE);
            imageView4.setVisibility(View.VISIBLE);
            imageView5.setVisibility(View.VISIBLE);
            imageView6.setVisibility(View.VISIBLE);
        }
        else {
            imageView.setVisibility(View.INVISIBLE);
            imageView1.setVisibility(View.INVISIBLE);
            imageView2.setVisibility(View.INVISIBLE);
            imageView3.setVisibility(View.INVISIBLE);
            imageView4.setVisibility(View.INVISIBLE);
            imageView5.setVisibility(View.INVISIBLE);
            imageView6.setVisibility(View.INVISIBLE);
        }
    } //Har samlet skjul/vis animasjon i en funskjon.

    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());//04.04.15
    }
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }
    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService= null;
        sinus.audioTrack.release();

    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        sinus = new PlaySound();

        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {

            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

                    Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                    ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - connecting");
                    mService.connect(deviceAddress);


                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth er aktivert ", Toast.LENGTH_SHORT).show();

                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Bluetooth ble ikke aktivert ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

    }


    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onBackPressed() {
        if (mState == UART_PROFILE_CONNECTED) {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
            showMessage("Lettpark kjører i bakgrunnen.\n             Kople fra for å avslutte");
        }
        else {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.popup_title)
                    .setMessage(R.string.popup_message)
                    .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.popup_no, null)
                    .show();
        }
    }



}


class lyd extends Thread {
    int pauseLength = 0;
    int toneLength = 0;

    public lyd(int pauseLength, int toneLength) {
        this.pauseLength = pauseLength;
        this.toneLength = toneLength;
    }

    public void run() {

        try {
            MainActivity.sinus.genTone(1000);

            synchronized (MainActivity.sinus) {

                MainActivity.sinus.playSound(true);
                MainActivity.sinus.wait(toneLength);
                MainActivity.sinus.playSound(false);
                MainActivity.sinus.wait(pauseLength);
            }

            MainActivity.check = 1;

        } catch (Exception e) {
                    //tom, vi vil ikke gjøre noe ved unntak.
        }

    }
}

class lyd2 extends Thread {
    public void run() {

        try {
            MainActivity.sinus.genTone(1000);
            MainActivity.sinus.playSound(true);

            while(MainActivity.checkfinal != 1){
                //Sløyfa venter til vi faller ut av dette området i tilstandsmaskinen og checkfinal settes til 1.
            }
            MainActivity.sinus.playSound(false);
            MainActivity.check = 1;

        } catch (Exception e) {
            //tom, vi vil ikke gjøre noe ved unntak.
        }

    }
}

class PlaySound{
    private final int sampleRate = 8000;
    private final int numSamples = sampleRate;
    private final double samples[] = new double[numSamples];
    private final byte generatedSnd[] = new byte[2*(numSamples)];
    //CHANNEL_CONFIGURATION_MONO foreldret har erstattet med CHANNEL_OUT_MONO
    AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, numSamples, AudioTrack.MODE_STATIC);

    void genTone(double freqOfTone){
        //fyll ut tabellen
        for(int i = 0; i<numSamples; i++){
            //skaler til maks amplitude
            samples[i] = Math.sin(2*Math.PI*i/(sampleRate/freqOfTone));
        }
        int idx = 0;
        for (double dVerd : samples){
            short Verd = (short) ((dVerd*32767));
            generatedSnd[idx++] = (byte) (Verd & 0x00ff);
            generatedSnd[idx++] = (byte) ((Verd & 0x00ff)>>>8);
        }

        audioTrack.write(generatedSnd, 0, numSamples * 2);
        audioTrack.setLoopPoints(0, numSamples / 2, -1);
    }

    void playSound(boolean on){
        if (on){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                audioTrack.setVolume(audioTrack.getMaxVolume()); //Henter frem maksimalt volum for audiotrack,og setter volum til dette.
            }
            audioTrack.play();
        }
        else{
            audioTrack.pause();
        }
    }
}



