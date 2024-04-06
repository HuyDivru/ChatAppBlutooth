package com.example.chatappblutooth;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.Manifest;
import android.widget.ArrayAdapter;

import com.example.chatappblutooth.databinding.ActivityMainBinding;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice[] bluetoothDevices;
    SendRevieve sendRevieve;
    private ActivityMainBinding binding;
    //26d72dea-0c3f-4657-8546-346e2ab3bd68
    public static final int STATE_LISTENING=1;
    public static final int STATE_CONNECTING=2;
    public static final int STATE_CONNECTED=3;
    public static final int STATE_CONNECTION_FAILED=4;
    public static final int STATE_MESSAGE_RECEIVED=5;

    int REQUEST_ENABLE_BLUETOOTH=1;

    public static final UUID MY_UUID=UUID.fromString("26d72dea-0c3f-4657-8546-346e2ab3bd68");
    public static final String APP_NAME="ChatAppBluetooth";
    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding=ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        bluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
        if(!bluetoothAdapter.isEnabled()){
            Intent enableIntent=new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BLUETOOTH);
        }
        enableListener();

        binding.call.setOnClickListener( v -> startActivity(new Intent(this,CallActivity.class)));
    }

    private void enableListener() {
        binding.listDeviecs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(ActivityCompat.checkSelfPermission(getApplicationContext(),Manifest.permission.BLUETOOTH_CONNECT)== PackageManager.PERMISSION_GRANTED){
                    Set<BluetoothDevice> devices=bluetoothAdapter.getBondedDevices();
                    String[] listString=new String[devices.size()];

                    bluetoothDevices=new BluetoothDevice[devices.size()];
                    int index=0;
                    if(devices.size()>0){
                        for(BluetoothDevice device:devices){
                            bluetoothDevices[index]=device;
                            listString[index]=device.getName();
                            index++;
                        }
                        ArrayAdapter<String> arrayAdapter=new ArrayAdapter<String>(getApplicationContext(),android.R.layout.simple_list_item_1,listString);

                        binding.listview.setAdapter(arrayAdapter);
                    }
                    binding.btnListener.setOnClickListener(view -> {
                        ServerProcess serverClass= new ServerProcess();
                        serverClass.start();
                    });

                    binding.listview.setOnItemClickListener(
                            (parent,view,i,id)-> {
                                ClientProcess client=new ClientProcess(bluetoothDevices[i]);
                                client.start();
                                binding.status.setText(R.string.connecting);
                            }
                    );
                    binding.btnSend.setOnClickListener(
                            viewSend ->{
                                String s=String.valueOf(binding.tvMsg.getText());
                                sendRevieve.write(s.getBytes());
                            }
                    );
                }
            }
        });
    }
    Handler handler=new Handler(new Handler.Callback() {
        @SuppressLint("SetTextI18n")
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if(ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
                switch(msg.what){
                    case STATE_LISTENING:
                        binding.status.setText(R.string.listening);
                        break;
                    case STATE_CONNECTING:
                        binding.status.setText(R.string.connecting);
                        break;
                    case STATE_CONNECTED:
                        binding.status.setText(R.string.connected);
                        break;
                    case STATE_CONNECTION_FAILED:
                        binding.status.setText(R.string.failed);
                        break;
                    case STATE_MESSAGE_RECEIVED:
                        byte[] readBuff=(byte[]) msg.obj;
                        String tempMessage=new String(readBuff,0,msg.arg1);
                        binding.tvMsg.setText(tempMessage);
                        break;
                }
            }
            return true;
        }
    });

    public class SendRevieve extends Thread{
        public final InputStream inputStream;
        public final OutputStream outputStream;

        public SendRevieve(BluetoothSocket socket){
            InputStream tempInput=null;
            OutputStream tempOutput=null;

            try {
                tempInput=socket.getInputStream();
                tempOutput=socket.getOutputStream();
            }
            catch (IOException e){
                e.printStackTrace();
            }
            inputStream=tempInput;
            outputStream=tempOutput;
        }

        @Override
        public void run() {
            super.run();

            byte[] buffer=new byte[1024];
            int bytes;

            while(true){
                try {
                    bytes=inputStream.read(buffer);
                    handler.obtainMessage(STATE_MESSAGE_RECEIVED,bytes,-1,buffer).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        public void write(byte[] bytes){
            try {
                outputStream.write(bytes);
            }
            catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    private class ServerProcess extends  Thread{
        private BluetoothServerSocket serverSocket;
        private BluetoothSocket bluetoothSocket;
        @SuppressLint("MissingPermission")
        public ServerProcess(){
            try {
                serverSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, MY_UUID);
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }
        @Override
        public void run() {
            super.run();
            bluetoothSocket=null;
            while(true){
                try {
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTING;
                    handler.sendMessage(message);
//                    sendRevieve=new SendRevieve()
                    bluetoothSocket=serverSocket.accept();
                }catch(IOException e){
                    e.printStackTrace();
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTION_FAILED;
                    handler.sendMessage(message);
                }
                if(bluetoothSocket!=null){
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTED;
                    handler.sendMessage(message);
                    sendRevieve=new SendRevieve(bluetoothSocket);
                    sendRevieve.start();
                    break;
                }
            }

        }
    }
    private class ClientProcess extends Thread{
        private BluetoothDevice bluetoothDevice;
        private BluetoothSocket bluetoothSocket;

        public ClientProcess(BluetoothDevice device){
            bluetoothDevice=device;

            try {
               if(ActivityCompat.checkSelfPermission(getApplicationContext(),Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
                   bluetoothSocket=bluetoothDevice.createRfcommSocketToServiceRecord(MY_UUID);
               }
            }catch (IOException e){
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            super.run();

            try {
                if(ActivityCompat.checkSelfPermission(getApplicationContext(),Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
                    bluetoothSocket.connect();
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTED;
                    handler.sendMessage(message);
                    sendRevieve=new SendRevieve(bluetoothSocket);
                    sendRevieve.start();
                }
            }
            catch(IOException e){
                e.printStackTrace();
                Message message=Message.obtain();
                message.what=STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
            }
        }
    }
    //create activity call between two devices using bluetooth

}