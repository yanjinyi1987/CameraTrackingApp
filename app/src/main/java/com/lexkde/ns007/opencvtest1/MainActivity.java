package com.lexkde.ns007.opencvtest1;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.lexkde.ns007.opencvtest1.fragment.BluetoothConnection;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;

/*
https://docs.opencv.org/2.4/doc/tutorials/introduction/android_binary_package/dev_with_OCV_on_Android.html
参考这个英文页面做配置
https://stackoverflow.com/questions/16626343/what-is-the-difference-between-opencv-android-javacameraview-and-opencv-andro
JavaCameraView 与 NativeCameraView的区别

OpenCV Manager的使用与NDK build的方式
http://blog.csdn.net/linshuhe1/article/details/51199744
获取ARM CPU的信息
http://blog.csdn.net/mengweiqi33/article/details/22796619

 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener,CameraBridgeViewBase.CvCameraViewListener2{

    public static final int DEVICE_CONNECTED = 1;
    public static final int DEVICE_BLE_CONNECTING=3;
    public static final int DEVICE_BLE_CONNECTED = 2;
    public static final int DEVICE_CONNECTION_FAILED=0;
    public static final int MESSAGE_READ=0;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean noBluetooth;
    private boolean bluetoothDisable;
    private static final int REQUEST_ENABLE_BT=1;
    private ArrayList<String> mConnectedBTDevices = new ArrayList<>();
    private boolean rwReady=false;
    private BluetoothDevice mBluetoothDevice;

    public static String TAG = "MainActivity";
    //opencv
    private JavaCameraView mOpenCvCameraView;
    //Servo
    public static final byte SERVO_ID_YAW=0x06;
    public static final byte SERVO_ID_PITCH=0x03;
    public static final byte CMD_SERVO_MOVE = 0x03;
    public static final short default_position=1500;

    public short position_yaw=1500;
    public short position_pitch=1500;

    private BaseLoaderCallback mBaseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    Log.i(TAG,"OpenCV loaded successfully!");
                    mOpenCvCameraView.enableView();
                    break;
                default:
                    Log.i(TAG,"OpenCV loaded failed!");
                    super.onManagerConnected(status);
                    break;
            }
        }
    };
    boolean isOver=false;
    //与fragmentDialog通信的Handler
    private Handler mMainActivityHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            mBluetoothDevice = null;
            BluetoothSocket mBluetoothSocket=null;
            switch(msg.what) {
                case DEVICE_CONNECTED:
                    mBluetoothDevice = (BluetoothDevice) (((ArrayList<Object>) msg.obj).get(0));
                    mBluetoothSocket = (BluetoothSocket) (((ArrayList<Object>) msg.obj).get(1));
                    //连接成功
                    Toast.makeText(MainActivity.this,"已连接到蓝牙设备"+mBluetoothDevice.getName(),Toast.LENGTH_LONG).show();

                    //关闭ProgressDialog
                    if(btDialog.mProgressDialog!=null) {
                        btDialog.mProgressDialog.dismiss();
                    }
                    btDialog.dismiss();

                    //建立读写通道哦！UI主线程与读写线程的交互
                    mSPPRWThread = new SPPRWThread(mBluetoothSocket);
                    mSPPRWThread.start();
                    //将功能区使能
                    rwReady=true;
                    enableViews();

                    //开始读取Power
                    Log.i("Power Reading","Start to read voltage");
                    getVoltageDataHandler.postDelayed(runnable_classic,10*1000); //10s
                    break;
                case DEVICE_BLE_CONNECTING:
                    mBluetoothDevice = (BluetoothDevice) (((ArrayList<Object>) msg.obj).get(0));
                    mBluetoothGatt = mBluetoothDevice.connectGatt(MainActivity.this,false,mBluetoothGattCallback);
                    break;
                case DEVICE_BLE_CONNECTED:
                    mBluetoothDevice = (BluetoothDevice) (((ArrayList<Object>) msg.obj).get(0));
                    //BluetoothGatt mBluetoothGatt = (BluetoothGatt) (((ArrayList<Object>) msg.obj).get(1));
                    UUID mServiceUUID = (UUID) (((ArrayList<Object>) msg.obj).get(2));
                    UUID mCharacterUUID = (UUID) (((ArrayList<Object>) msg.obj).get(3));
                    BluetoothGattService  gattService = mBluetoothGatt.getService(mServiceUUID);
                    gattSSPCharacteristic = gattService.getCharacteristic(mCharacterUUID); //现在我们获得了mBluetoothGatt与gattCharacteristic，这样就可以操作串口了。
                    Log.i("Power Reading", mServiceUUID.toString() + "\n"+ mCharacterUUID.toString());
                    //连接成功
                    Toast.makeText(MainActivity.this,"已连接到蓝牙设备"+mBluetoothDevice.getName(),Toast.LENGTH_LONG).show();
                    //关闭ProgressDialog
                    if(btDialog.mProgressDialog!=null) {
                        btDialog.mProgressDialog.dismiss();
                    }
                    btDialog.dismiss();
                    //
                    enableViews();
                    //开始读取power
                    Log.i("Power Reading","Start to read voltage");
                    getVoltageDataHandler.postDelayed(runnable,10*1000); //10s
                    break;
                default:
                    break;
            }
        }
    };
    //BLE
    public static final String TAG_BLUETOOTH_CONNECTION = "Bluetooth connection";
    private  BluetoothGatt mBluetoothGatt;
    private BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Message msg = new Message();
            if (newState == STATE_CONNECTED) {
                Log.i(TAG_BLUETOOTH_CONNECTION,"connect successfully!");
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG_BLUETOOTH_CONNECTION,"connect failed!");
                msg.what=MainActivity.DEVICE_CONNECTION_FAILED; //连接失败
                msg.obj = mBluetoothGatt.getDevice();
                btDialog.mBluetoothConnectionHandler.sendMessage(msg);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG_BLUETOOTH_CONNECTION, "成功发现服务");
                List<BluetoothGattService> supportedBluetoothGattServices = gatt.getServices();
                for(int i=0;i<supportedBluetoothGattServices.size();i++) {
                    /*
                    Log.e(TAG_BLUETOOTH_CONNECTION,"\t"+supportedBluetoothGattServices.get(i).getUuid().toString());

                    for(BluetoothGattCharacteristic supportedBluetoothGattCharacter:supportedBluetoothGattServices.get(i).getCharacteristics()) {
                        int charaProp = supportedBluetoothGattCharacter.getProperties();
                        Log.e(TAG_BLUETOOTH_CONNECTION,"\t\t"+String.format("0x%x\t",charaProp)+supportedBluetoothGattCharacter.getUuid().toString());
                        if((charaProp & BluetoothGattCharacteristic.PROPERTY_READ)>0 ) {
                            Log.i(TAG_BLUETOOTH_CONNECTION,"\t\tRead"+supportedBluetoothGattCharacter.getUuid().toString());
                        }
                        if((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE)>0) {
                            Log.i(TAG_BLUETOOTH_CONNECTION,"\t\tWrite"+supportedBluetoothGattCharacter.getUuid().toString());

                        }
                    }
                    */
                    UUID serviceUUID = supportedBluetoothGattServices.get(i).getUuid();
                    Log.e(TAG_BLUETOOTH_CONNECTION, "Service UUID"+serviceUUID.toString());
                    List<BluetoothGattCharacteristic> supportedBluetoothGattCharacteristics = supportedBluetoothGattServices.get(i).getCharacteristics();
                    for (int j = 0; j < supportedBluetoothGattCharacteristics.size(); j++) {
                        UUID characterUUID = supportedBluetoothGattCharacteristics.get(j).getUuid();
                        Log.e(TAG_BLUETOOTH_CONNECTION, "\t\tCharacter UUID"+characterUUID.toString());
                        int charaProp = supportedBluetoothGattCharacteristics.get(j).getProperties();
                        if((charaProp & BluetoothGattCharacteristic.PROPERTY_READ)>0 && (charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE)>0) {
                            Log.i(TAG_BLUETOOTH_CONNECTION, "\t\t\t发现蓝牙串口特征"+characterUUID.toString());
                            Message msg = new Message();
                            msg.what = MainActivity.DEVICE_BLE_CONNECTED; //连接成功
                            ArrayList<Object> btDeviceAndConnectionInfo = new ArrayList<>();
                            btDeviceAndConnectionInfo.add(0, gatt.getDevice());
                            btDeviceAndConnectionInfo.add(1, gatt);
                            btDeviceAndConnectionInfo.add(2, serviceUUID);
                            btDeviceAndConnectionInfo.add(3, characterUUID);
                            msg.obj = btDeviceAndConnectionInfo;
                            mMainActivityHandler.sendMessage(msg);
                            return;
                        }
                    }
                }
            }
            else {
                Log.e(TAG_BLUETOOTH_CONNECTION,"服务发现失败，错误码为"+status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.i("Power Reading","onCharacteristicWrite: "+byteArrayToString(characteristic.getValue()));
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.i("Power Reading","Callback");
            String voltage = extractBatteryPower(characteristic.getValue());
            if(voltage!=null) {
                Log.i("Battery Power", voltage);
                mTextViewBatteryPower.setText("Battery Power: "+voltage+" mV"); //Battery Power: 0000 mV
            }
            else {
                Log.i("Battery Power", "Reading voltage error!");
            }
        }
    };

    //循环读取
    Handler getVoltageDataHandler = new Handler();
    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            Log.i("Power Reading","A");
            isOver=false;
            mBluetoothGatt.setCharacteristicNotification(gattSSPCharacteristic,true);
            gattSSPCharacteristic.setValue(command_ReadBatteryPower());
            gattSSPCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            mBluetoothGatt.writeCharacteristic(gattSSPCharacteristic);
            getVoltageDataHandler.postDelayed(this,10*1000); //10s
        }
    };

    Runnable runnable_classic = new Runnable() {
        @Override
        public void run() {
            Log.i("Power Reading","A");
            isOver=false;
            mSPPRWThread.write(command_ReadBatteryPower());
            getVoltageDataHandler.postDelayed(this,10*1000); //10s
        }
    };

    private TextView mTextViewBatteryPower;
    private BluetoothConnection btDialog;
    private Button mButtonServoCalibration;
    private Button mButtonServoTracking;
    private BluetoothGattCharacteristic gattSSPCharacteristic;


    void getSensorData(SPPRWThread mSPPRWThread) {
        Log.i("MainActivity","send command");
        mSPPRWThread.write("s".getBytes());
    }
    void getControlData(SPPRWThread mSPPRWThread) {
        mSPPRWThread.write("c".getBytes());
    }

    //与读写线程通信的Handler
    private Handler mReadSPPHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MESSAGE_READ:
                    int length = msg.arg1;
                    byte[] result = (byte[])msg.obj;
                    String voltage = extractBatteryPower(result);
                    if(voltage!=null) {
                        mTextViewBatteryPower.setText("Battery Power: "+voltage+" mV"); //Battery Power: 0000 mV
                    }
                    break;
                default:
                    break;
            }
        }
    };

    //处理蓝牙接收器的状态变化
    /**
     * https://developer.android.com/reference/android/bluetooth/BluetoothAdapter.html#STATE_TURNING_ON
     * int STATE_OFF : Indicates the local Bluetooth adapter is off.
     * int STATE_ON  : Indicates the local Bluetooth adapter is on, and ready for use.
     * int STATE_TURNING_OFF : Indicates the local Bluetooth adapter is turning off.
     *                         Local clients should immediately attempt graceful disconnection of any remote links.
     *int STATE_TURNING_ON: Indicates the local Bluetooth adapter is turning on.
     *                      However local clients should wait for STATE_ON before attempting to use the adapter.
     */
    BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1);
                int state_previous = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE,-1);
                switch (state) {
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        mConnectedBTDevices.clear(); //清除连接列表
                        //关闭读写通道
                        if(rwReady==true) {
                            mSPPRWThread.cancel();
                        }
                        //disable功能区
                        rwReady=false;
                        break;
                    case BluetoothAdapter.STATE_ON:

                        break;
                    default:
                        break;
                }
            }
        }
    };
    private Button mButtonConnectionButton;
    private TextView mConnectionStatus;
    private SPPRWThread mSPPRWThread;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case REQUEST_ENABLE_BT:
                if(resultCode==RESULT_OK) {
                    //蓝牙设备已经被使能了，then do the job，paired or discovery and then connecting，看sample我们
                    //需要做一个listview来实现这一点。
                    /*
                    mButtonConnectionButton.setEnabled(false);
                    //先打开系统自带的蓝牙设置界面来配对和连接蓝牙，有时间再自己写一个DialogFragment的例子
                    Intent settingsIntent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                    //但是打开的这个Activity好像只有显示配对和查找配对设备的功能，没有连接的功能哦。
                    startActivity(settingsIntent);
                    */
                    if(noBluetooth==false) {
                        callBtConnectionDialog();
                    }
                    else {
                        try {
                            throw(new Exception("程序不可能运行到这里"));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                else if(resultCode == RESULT_CANCELED) {
                    //蓝牙设备没有被使能
                }
                else {
                    //不可能到这里来
                    Toast.makeText(this,"Error！",Toast.LENGTH_LONG).show();
                }
                break;
            default:
                break;
        }
    }

    void callBtConnectionDialog() {
        btDialog = BluetoothConnection.newInstance(mBluetoothAdapter,mConnectedBTDevices,mMainActivityHandler);
        btDialog.show(getFragmentManager(), "蓝牙设置");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        //添加IntentFilter来监听Bluetooth的状态变化
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        //注册接受器
        registerReceiver(mBroadcastReceiver,intentFilter);

        mOpenCvCameraView = findViewById(R.id.OpenCvView1);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    protected void onResume() {
        Log.i(TAG,"OnResume");
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mBaseLoaderCallback);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(mOpenCvCameraView!=null) {
            mOpenCvCameraView.disableView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mOpenCvCameraView!=null) {
            mOpenCvCameraView.disableView();
        }

        getVoltageDataHandler.removeCallbacks(runnable);
        mButtonConnectionButton.setEnabled(true);
        unregisterReceiver(mBroadcastReceiver);
        if(rwReady==true) {
            mConnectedBTDevices.clear();
            mSPPRWThread.cancel();
        }

    }

    /*
        Bluetooth init
         */
    void initBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null) {
            //设备不支持蓝牙
            Toast.makeText(this,"您的设备不支持蓝牙",Toast.LENGTH_LONG).show();
            noBluetooth = true;
            return;
        }
        //蓝牙设备是存在的
        if(!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT); //对应onActivityResult
        }
        else {
            Log.e(TAG,"callBtConnectionDialog");
            callBtConnectionDialog();
        }
    }

    void initViews()
    {
        mButtonConnectionButton = (Button) findViewById(R.id.connect_bt_device);
        mConnectionStatus = (TextView) findViewById(R.id.connection_status);
        mButtonServoCalibration = (Button) findViewById(R.id.servo_calibration);
        mButtonServoTracking = (Button) findViewById(R.id.servo_tracking);
        mTextViewBatteryPower = (TextView) findViewById(R.id.battery_power_value);
        mButtonConnectionButton.setOnClickListener(this);
        mButtonServoCalibration.setOnClickListener(this);
        mButtonServoTracking.setOnClickListener(this);
        disableViews();
    }

    void enableViews() {
        Log.i("MainActivity:","enableViews");
        mConnectionStatus.setText("远程设备"+mBluetoothDevice.getName()+"已经连接！");
        mConnectionStatus.setTextColor(Color.rgb(0,0,0)); //设置为黑色
        mButtonConnectionButton.setEnabled(false);
        mButtonServoCalibration.setEnabled(true);
        mButtonServoTracking.setEnabled(true);
    }

    void disableViews() {
        mButtonServoCalibration.setEnabled(false);
        mButtonServoTracking.setEnabled(false);
        mButtonConnectionButton.setEnabled(true);
    }

    byte getLowByte(short t) {
        return (byte)(t&((short) 0x00FF));
    }

    byte getHighByte(short t) {
        return (byte)((t>>>16)&((short) 0xFF00));
    }

    int recoverLowHighByte(byte low,byte high) {
        int lowInt = (int) low;
        int highInt = (int) high;
        lowInt = lowInt&(0x000000FF);
        highInt = highInt&(0x000000FF);
        //Log.e("MainActivity",Integer.toString(lowInt));
        //Log.e("MainActivity",Integer.toString(highInt));
        return lowInt+(highInt<<8);
    }

    byte[] buildControlCommand1(short time,byte servoID,short position) {
        return new byte[]{0x55,
                0x55,
                (byte)(1*3+5),
                CMD_SERVO_MOVE,
                getLowByte(time),
                getHighByte(time),
                servoID,
                getLowByte(position),
                getHighByte(position)};
    }
    //Yaw 1500 ID = 6
    byte[] buildYawControlCommand(short time,short position) {
        return buildControlCommand1(time,SERVO_ID_YAW,position);
    }
    //Pitch 1500 ID = 3
    byte[] buildPitchControlCommand(short time,short position) {
        return buildControlCommand1(time,SERVO_ID_PITCH,position);
    }

    byte[] buildControlCommand2(short time,byte servoID_1,short position_1,byte servoID_2,short position_2) {
        return new byte[]{0x55,0x55,
                (byte)(2*3+5),
                CMD_SERVO_MOVE,
                getLowByte(time),
                getHighByte(time),
                servoID_1,
                getLowByte(position_1),
                getHighByte(position_1),
                servoID_2,
                getLowByte(position_2),
                getHighByte(position_2)};
    }

    //Yaw ID=6; Pitch ID=3
    byte[] buildYawAndPitchControlCommand(short time,short position_yaw,short position_pitch) {
        return buildControlCommand2(time,SERVO_ID_YAW,position_yaw,SERVO_ID_PITCH,position_pitch);
    }

    //Read battery power
    byte[] command_ReadBatteryPower() {
        return new byte[]{0x55,0x55,0x02,0x0F};
    }

    String extractBatteryPower(byte[] result) {
        Log.i("Power Reading",byteArrayToString(result));
        if(result.length==6 && result[0]==0x55 && result[1]==0x55 && result[2]==0x04 && result[3]==0x0F) {
            Log.i("Voltage Reading","Got correct voltage "+Integer.toString(recoverLowHighByte(result[4],result[5])));
            return Integer.toString(recoverLowHighByte(result[4],result[5]));
        }
        else {
            return null;
        }
    }

    String byteArrayToString(byte[] value) {
        StringBuilder stringBuilder = new StringBuilder();
        for(byte i:value) {
            stringBuilder.append(String.format("0x%02x ",i));
        }
        return stringBuilder.toString();
    }

    String byteArrayToString(byte[] value,int length) {
        StringBuilder stringBuilder = new StringBuilder();
        for(int i=0;i<length;i++) {
            stringBuilder.append(String.format("0x%02x ",value[i]));
        }
        return stringBuilder.toString();
    }

    private class SPPRWThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public SPPRWThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[20];  // buffer store for the stream
            byte[] buffer1= new byte[1];
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    if(mmInStream.read()==0x55 && mmInStream.read()==0x55) {
                        int length = mmInStream.read();
                        if(length!=-1 && length>=2) {
                            buffer[0]=0x55;buffer[1]=0x55;buffer[2]=(byte)length;
                            //Log.i("MainActivity length", Integer.toString(length));
                            for (int i = 0; i < length; i++) {
                                buffer[i+3] = (byte) mmInStream.read();
                            }
                            byte[] deepCopy = new byte[length+2];
                            System.arraycopy(buffer, 0, deepCopy, 0, length+2);
                            //Log.i("MainActivity Thread", byteArrayToString(buffer, length+2));
                            mReadSPPHandler.obtainMessage(MESSAGE_READ, length+2, -1, deepCopy).sendToTarget();
                        }
                    }
                    /*
                    bytes = mmInStream.read(buffer); //这种问题应该怎么处理呢？
                    if(bytes!=-1) {
                        Log.i("MainActivitylength", Integer.toString(bytes));
                        buffer[bytes] = 0;
                        byte[] deepCopy = new byte[bytes];
                        System.arraycopy(buffer, 0, deepCopy, 0, bytes);
                        // Send the obtained bytes to the UI activity
                        Log.i("MainActivity Thread", byteArrayToString(buffer, bytes));
                        mReadSPPHandler.obtainMessage(MESSAGE_READ, bytes, -1, deepCopy).sendToTarget();
                    }
                    */
                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.connect_bt_device:
                initBluetooth();
                break;
            case R.id.servo_calibration:
                Log.i("Power Reading",buildYawControlCommand((short)100,(short)1400).toString()+" "+buildYawControlCommand((short)100,(short)1400).length);
                gattSSPCharacteristic.setValue(buildYawControlCommand((short)100,(short)1400));
                gattSSPCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                mBluetoothGatt.writeCharacteristic(gattSSPCharacteristic);
                break;
            case R.id.servo_tracking:
                break;
            default:
                break;
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        Mat dst = inputFrame.gray();
        Mat image = inputFrame.rgba();

        Mat circles = new Mat();
        //高斯滤波
        org.opencv.imgproc.Imgproc.blur(dst,dst,new Size(7,7),new Point(2,0),2);
        //霍夫圆
        //http://blog.csdn.net/hbl_for_android/article/details/52083831
    /*
     * CV_EXPORTS_W void HoughCircles( InputArray image, OutputArray circles,
                               int method, double dp, double minDist,
                               double param1 = 100, double param2 = 100,
                               int minRadius = 0, int maxRadius = 0 );
     * image为输入图像，要求是灰度图像
     * circles为输出圆向量，每个向量包括三个浮点型的元素——（x,y,radius）
     * method为使用霍夫变换圆检测的算法,它的参数是CV_HOUGH_GRADIENT
     * dp为第一阶段所使用的霍夫空间的分辨率，dp=1时表示霍夫空间与输入图像空间的大小一致，
     * dp=2时霍夫空间是输入图像空间的一半
     * minDist为圆心之间的最小距离，如果检测到的两个圆心之间距离小于该值，则认为它们是同一个圆心
     * param1有默认值100。它是第三个参数method设置的检测方法的对应的参数。
     * 对当前唯一的方法霍夫梯度法CV_HOUGH_GRADIENT，它表示传递给canny边缘检测算子的高阈值，
     * 而低阈值为高阈值的一半。
     * param2也有默认值100。它是第三个参数method设置的检测方法的对应的参数。
     * 对当前唯一的方法霍夫梯度法CV_HOUGH_GRADIENT，它表示在检测阶段圆心的累加器阈值。
     * 它越小的话，就可以检测到更多根本不存在的圆，而它越大的话，能通过检测的圆就更加接近完美的圆形了。
     * minRadius和maxRadius为所检测到的圆半径的最小值和最大值
     */
        //HoughCircle
        org.opencv.imgproc.Imgproc.HoughCircles(dst,circles,
                Imgproc.CV_HOUGH_GRADIENT,
                1,10,150,70,0,0);

        for(int i=0;i<circles.cols();i++) {
            for(int j=0;j<circles.rows();j++){
                double[] c = circles.get(j,i);
                Point center = new Point(Math.round(c[0]), Math.round(c[1]));
                long radius = Math.round(c[2]);
                //绘制圆心
                Imgproc.circle(image, center, 3, new Scalar(255, 0, 255), -1, 8, 0);
                //绘制圆轮廓
                Imgproc.circle(image, center, (int) radius, new Scalar(155, 255, 50), 3, 8, 0);
            }
        }
        drawCrossLine(image,new Point(dst.cols()/2,dst.rows()/2),new Scalar(255,0,0),30,2);
        return image;
    }

    public void drawCrossLine(Mat img, Point point, Scalar color, int size, int thickness) {
        //绘制横线
        org.opencv.imgproc.Imgproc.line(img,
                new Point(point.x-size/2,point.y),
                new Point(point.x+size/2,point.y),
                color,thickness,8,0);
        //绘制竖线
        org.opencv.imgproc.Imgproc.line(img,
                new Point(point.x,point.y-size/2),
                new Point(point.x,point.y+size/2),
                color,thickness,8,0);
    }
}
