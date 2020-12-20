package com.example.bt_clk_sync;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private ImageButton btn_toggle;
    private ImageButton btn_sync;
    private ImageButton btn_exit;
    private Button  btn_scan;
    private ListView device_list;
    String address = null;
    private ProgressDialog progress;

    // Bluetooth
    private BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private Set<BluetoothDevice> pairedDevices;
    private boolean isBtConnected = false;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public static final String msg_ack = "A";
    public static final String msg_light_toggle = "B";
    public static final String msg_set_time = "C";
    public static final String msg_get_time = "D";
    public static final String msg_get_osc = "E";
    public static final String msg_set_osc = "F";
    public static final String msg_undefined = "G";
    public static final String msg_end = "H";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn_exit = (ImageButton)findViewById(R.id.imageButton_exit);
        btn_toggle = (ImageButton)findViewById(R.id.imageButton_on);
        btn_sync = (ImageButton)findViewById(R.id.imageButton_sync);
        btn_scan = (Button)findViewById(R.id.button_paired);
        device_list = (ListView)findViewById(R.id.listView);

        myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device

        if(myBluetooth == null){
            //Show a mesage that the device has no bluetooth adapter
            msg("Bluetooth Device Not Available");
            //finish apk
            finish();
        }else if(!myBluetooth.isEnabled()){
            //Ask to the user turn the bluetooth on
            Intent turnBTon = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(turnBTon, 1);
        }

        btn_exit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Disconnect();
                myBluetooth = null;
                finish();
            }
        });

        btn_toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLed();
            }
        });

        btn_sync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                syncTime();
            }
        });

        btn_scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pairedDevicesList();
            }
        });

        LoadDeviceAddr();

        if (address == null){
            // choose device
            pairedDevicesList();
        }else{
            new ConnectBT().execute(); //Call the class to connect
        }

    }

    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }

    public void SaveDeviceAddress(){
        SharedPreferences prefs = getSharedPreferences("my_prefs", MainActivity.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString("device_address", address);
        editor.commit();
    }

    private void LoadDeviceAddr(){
        SharedPreferences prefs = getSharedPreferences("my_prefs", MainActivity.MODE_PRIVATE);
        address = prefs.getString("device_address", null);
    }

    private void pairedDevicesList()
    {
        pairedDevices = myBluetooth.getBondedDevices();
        ArrayList list = new ArrayList();

        if (pairedDevices.size()>0)
        {
            for(BluetoothDevice bt : pairedDevices)
            {
                list.add(bt.getName() + "\n" + bt.getAddress()); //Get the device's name and the address
            }
        }
        else
        {
            msg("No Paired Bluetooth Devices Found.");
        }

        final ArrayAdapter adapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1, list);
        device_list.setAdapter(adapter);
        device_list.setOnItemClickListener(myListClickListener); //Method called when the device from the list is clicked

    }

    private AdapterView.OnItemClickListener myListClickListener = new AdapterView.OnItemClickListener()
    {
        public void onItemClick (AdapterView<?> av, View v, int arg2, long arg3)
        {
            // Get the device MAC address, the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            address = info.substring(info.length() - 17);
            SaveDeviceAddress();
            new ConnectBT().execute(); //Call the class to reconnect
        }
    };


    private class ConnectBT extends AsyncTask<Void, Void, Void>  // UI thread
    {
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute()
        {
            progress = ProgressDialog.show(MainActivity.this, "Connecting...", "Please wait!!!");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try
            {
                if ((btSocket == null) || !isBtConnected)
                {
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();//start connection
                }
            }
            catch (IOException e)
            {
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);
            progress.dismiss();

            if (!ConnectSuccess)
            {
                msg("Connection Failed. Try again.");
            }
            else
            {
                msg("Connected.");
                isBtConnected = true;
            }
        }
    }

    private void Disconnect()
    {
        if (btSocket!=null) //If the btSocket is busy
        {
            try
            {
                btSocket.close(); //close connection
            }
            catch (IOException e)
            { msg("Error");}
        }

    }

    private void toggleLed()
    {
        if (btSocket!=null)
        {
            try
            {
                btSocket.getOutputStream().write(msg_light_toggle.concat(msg_end).getBytes());
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }

    private void syncTime()
    {
        if (btSocket!=null)
        {
            Calendar c = Calendar.getInstance();
            int hours = c.get(Calendar.HOUR);
            int minutes = c.get(Calendar.MINUTE);
            int seconds = c.get(Calendar.SECOND);

            try
            {
                btSocket.getOutputStream().write(msg_set_time.getBytes());
                btSocket.getOutputStream().write(hours);
                btSocket.getOutputStream().write(minutes);
                btSocket.getOutputStream().write(seconds);
                btSocket.getOutputStream().write(msg_end.getBytes());
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }
}
