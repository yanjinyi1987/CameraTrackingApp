package com.lexkde.ns007.opencvtest1.fragment;

import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.lexkde.ns007.opencvtest1.MainActivity;
import com.lexkde.ns007.opencvtest1.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Created by lexkde on 16-10-22.
 */

public class BluetoothConnection extends DialogFragment implements View.OnClickListener {

    private Button mRefreshBtButton,mCancleButton;
    private TextView mDiscoveredBtText;
    private ListView mBtPairedListView;
    private ListView mBtDiscoveredListView;
    private ArrayList<BTDevice> mBtPairedDevices;
    private PairedBtAdapter mPairedBtAdapter;
    private ArrayList<BTDevice> mBtDiscoveredDevices;
    private PairedBtAdapter mDiscoveredBtAdapter;
    private static BluetoothAdapter mBluetoothAdapter;
    private static ArrayList<String> mConnectedDeviceMACs;
    private static Handler mMainActivityHandler;
    public final static String NAME = "JGCX";
    public final static UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");


    public Handler mBluetoothConnectionHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            //BluetoothDevice有Parcelable接口额
            BluetoothDevice mBluetoothDevice = null;
            Message toMainActivityMsg = new Message();
            switch(msg.what) {
                case MainActivity.DEVICE_CONNECTION_FAILED:
                    mBluetoothDevice = (BluetoothDevice) msg.obj;
                    Toast.makeText(getActivity(),"连接到蓝牙设备"+mBluetoothDevice.getName()+"失败",Toast.LENGTH_LONG).show();
                    //关闭ProgressDialog
                    if(mProgressDialog!=null) {
                        mProgressDialog.dismiss();
                    }
                    //重新开启startRecovery
                    mDiscoveredBtAdapter.clear();
                    discoverBtDevices();
                    mRefreshBtButton.setEnabled(false);
                    break;
                default:
                    break;
            }
        }
    };

    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //when discovery finds a device
            if(BluetoothDevice.ACTION_FOUND.equals(action)) {
                //Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //Add the name and address to an array adapter to show in a ListView
                mDiscoveredBtAdapter.add(new BTDevice(device,device.getType(),device.getName(),null,false));
            }
        }
    };
    private boolean discovered=false;
    public static int timeout = 120; //搜索超时120秒
    public static String frameText0 = "发现设备";
    public static String frameText1 = "发现设备.";
    public static String frameText2 = "发现设备..";
    public static String frameText3 = "发现设备...";
    public static String frameText4 = "发现设备....";
    public static int flipflop = 0;
    public static int count = 5;

    Handler animatorHandle =  new Handler();
    Handler timeoutHandle = new Handler();
    private BluetoothSocket mBluetoothSocket;
    public ProgressDialog mProgressDialog;
    private ConnectThread mBtConnectThread;

    public static BluetoothConnection newInstance(BluetoothAdapter bluetoothAdapter,
                                                  ArrayList<String> connectedDeviceMACs,
                                                  Handler mainActivityHandler) {
        Log.e("BluetoothConnection","newInstance");
        mBluetoothAdapter = bluetoothAdapter;
        mConnectedDeviceMACs = connectedDeviceMACs;
        mMainActivityHandler = mainActivityHandler;
        return new BluetoothConnection();
    }

    @Override
    public void onClick(View v) {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setCancelable(false); //保持在最前
        int style = DialogFragment.STYLE_NORMAL;
        int theme = android.R.style.Theme_DeviceDefault_Light_Dialog;
        setStyle(style,theme);
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.getActivity().registerReceiver(mReceiver,filter); //这里不需要进行改动，Fragment不能接收信号，但这只是注册一下
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.getActivity().unregisterReceiver(mReceiver);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getDialog().setTitle("蓝牙设置");
        View v = inflater.inflate(R.layout.bluetooth_connection_dialog,container,false);
        initViews(v);
        return v;
    }

    private void initViews(View v) {
        //mEnableBtButton = (Button) v.findViewById(R.id.bt_enable_button);
        mRefreshBtButton = (Button) v.findViewById(R.id.refresh_action_button);
        mCancleButton = (Button) v.findViewById(R.id.cancle_action_button);
        mDiscoveredBtText = (TextView) v.findViewById(R.id.bt_discovered_title);

        mRefreshBtButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //刷新列表
                if(discovered==true) {
                    mDiscoveredBtAdapter.clear();
                    discoverBtDevices();
                }
            }
        });

        mCancleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mBluetoothAdapter!=null) {
                    if(discovered==true) {
                        mBluetoothAdapter.cancelDiscovery();
                    }
                }
                //退出界面
                dismiss();
            }
        });
        initListViews(v);
    }



    private void initListViews(View v) {
        mBtPairedListView = (ListView) v.findViewById(R.id.bt_paried_list);
        mBtDiscoveredListView = (ListView) v.findViewById(R.id.bt_discovered_list);
        mBtPairedDevices = new ArrayList<>();
        mPairedBtAdapter = new PairedBtAdapter(this.getActivity(),
                R.layout.bt_device_list,
                mBtPairedDevices);

        mBtDiscoveredDevices = new ArrayList<>();
        mDiscoveredBtAdapter = new PairedBtAdapter(this.getActivity(),
                R.layout.bt_device_list,
                mBtDiscoveredDevices);

        mBtPairedListView.setAdapter(mPairedBtAdapter);
        mBtDiscoveredListView.setAdapter(mDiscoveredBtAdapter);

        mBtPairedListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //connectBtClient(((BTDevice)mBtDiscoveredListView.getItemAtPosition(position)).mBtDevice);
                BluetoothDevice mmBtDevice = mPairedBtAdapter.getItem(position).mBtDevice;
                connectToBTDevice(mmBtDevice);
            }
        });

        mBtDiscoveredListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //connectBtClient(((BTDevice)mBtDiscoveredListView.getItemAtPosition(position)).mBtDevice);
                BluetoothDevice mmBtDevice = mDiscoveredBtAdapter.getItem(position).mBtDevice;
                connectToBTDevice(mmBtDevice);
            }
        });

        //填充两个ListView
        queryPairedBtDevices(mPairedBtAdapter);
        discoverBtDevices(); //异步程序
    }

    void connectToBTDevice(BluetoothDevice mmBtDevice) {
        if(mConnectedDeviceMACs.contains(mmBtDevice.getAddress())!=true) {
            Log.i("MainActivity",mmBtDevice.getName()+" "+mmBtDevice.getAddress());
            int deviceType = mmBtDevice.getType();
            if(deviceType== BluetoothDevice.DEVICE_TYPE_LE) {
                //BLE device
                Log.i("BLE device","Found BLE device");
                //发送消息，让他在MainActivity里面连接
                Message msg = new Message();
                msg.what = MainActivity.DEVICE_BLE_CONNECTING;
                ArrayList<Object> btDeviceAndConnectionInfo = new ArrayList<>();
                btDeviceAndConnectionInfo.add(0,mmBtDevice);
                msg.obj = btDeviceAndConnectionInfo;
                mMainActivityHandler.sendMessage(msg);
            }
            else if(deviceType !=BluetoothDevice.DEVICE_TYPE_UNKNOWN) {
                //classic bluetooth device or dual mode device
                Log.i("BLE device","Found classic or dual mode bluetooth device");
                mBtConnectThread = new ConnectThread(mmBtDevice, mBluetoothConnectionHandler);
                mBtConnectThread.start(); //线程开始运行
            }
            else {
                Log.i("Bluetooth Connection","Unknown bluetooth device type!");
                Toast.makeText(getActivity(),"未知类型的蓝牙设备",Toast.LENGTH_LONG).show();
                return; //不要打开ProgressDialog，因为会无法关闭
            }
            //展示ProgressDialog
            openProgressDialog(mmBtDevice.getName());
        }
        else {
            Toast.makeText(getActivity(),"蓝牙设备"+mmBtDevice.getName()+"已经处于连接状态",Toast.LENGTH_LONG).show();
        }
    }

    //当连接完成之后就关闭这个ProgressDialog与DialogFragment
    void openProgressDialog(String bt_device_name) {
        Log.i("BluetoothConnection","ProgressDialog打开了");
        mProgressDialog = new ProgressDialog(this.getActivity());
        mProgressDialog.setTitle("连接到设备"+bt_device_name+"中....");
        mProgressDialog.setMessage("请稍后！");
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setCancelable(false); //保持在最前
        mProgressDialog.show();
    }

    void queryPairedBtDevices(PairedBtAdapter tempPairedBtAdapter) {
        //mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter(); //这里应该使用MainActivity传递进来的数据
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to an array adapter to show in a ListView
                tempPairedBtAdapter.add(new BTDevice(device,device.getType(),device.getName(),null,false));
            }
        }
    }
    //设置搜寻动画，当搜索超时后，停止搜索，并使能refresh button
    void discoverBtDevices() {
        if(!mBluetoothAdapter.startDiscovery()) {
            Toast.makeText(this.getActivity(),"启动设备发现失败",Toast.LENGTH_LONG).show();
        }
        else {
            discovered =true;
            mRefreshBtButton.setEnabled(false); //程序自动搜索咯，不要自己按refresh

            final Runnable discoverBtDeviceThread = new Runnable() {
                @Override
                public void run() {
                    switch(flipflop%count) {
                        case 0:
                            mDiscoveredBtText.setText(frameText0);
                            break;
                        case 1:
                            mDiscoveredBtText.setText(frameText1);
                            break;
                        case 2:
                            mDiscoveredBtText.setText(frameText2);
                            break;
                        case 3:
                            mDiscoveredBtText.setText(frameText3);
                            break;
                        case 4:
                            mDiscoveredBtText.setText(frameText4);
                            break;
                        default:
                            break;
                    }
                    flipflop++;
                    animatorHandle.postDelayed(this,500);
                }
            };

            animatorHandle.postDelayed(discoverBtDeviceThread,500);

            timeoutHandle.postDelayed(new Runnable() {
                @Override
                public void run() {
                    animatorHandle.removeCallbacks(discoverBtDeviceThread);
                    mBluetoothAdapter.cancelDiscovery();
                    mRefreshBtButton.setEnabled(true);
                }
            },120*1000);
        }
    }

    //这个函数必须是阻塞的，因为连不上对应的设备，这个APP并没有鸟用。
    //点击之后，出现一个进度条并且其余设备不能被继续点击。如果连接设备成功，那么应该自动的退出这个dialog并展示Toast
    //将获得的mBluetoothSocket传递到MainActivity来进行后续I/O操作，因为控制逻辑都在那里。
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private Handler mBluetoothConnectionHandler;

        public ConnectThread(BluetoothDevice device,Handler mBluetoothConnectionHandler) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;
            this.mBluetoothConnectionHandler = mBluetoothConnectionHandler;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // 这个UUID应该从哪里来呢？
                //我们设备的蓝牙版本号为2.0，那么应该使用
                //http://www.cnblogs.com/CharlesGrant/p/4924169.html
                /**
                 * 注意：对于UUID，
                 * 必须使用Android的SSP（协议栈默认）的UUID：00001101-0000-1000-8000-00805F9B34FB才能正常和外部的，
                 * 也是SSP串口的蓝牙设备去连接。
                 */
                tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID); //LC-06为Bluetooth 2.0设备
                //tmp = device.createRfcommSocketToServiceRecord(MY_UUID); //Bluetooth 2.1及以上设备使用
            } catch (IOException e) { }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();
            Log.i("MainActivity","start to connect");
            Message msg = new Message();
            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                //mBluetoothConnectionHandler.sendEmptyMessage(0); //连接失败
                msg.what=MainActivity.DEVICE_CONNECTION_FAILED; //连接失败
                msg.obj = mmDevice;
                mMainActivityHandler.sendMessage(msg);
                return;
            }

            //将mmSocket传递给MainActivity
            //manageConnectedSocket(mmSocket);
            //mBluetoothConnectionHandler.sendEmptyMessage(1); //连接成功
            msg.what=MainActivity.DEVICE_CONNECTED; //连接成功
            ArrayList<Object> btDeviceAndConnectionInfo = new ArrayList<>();
            btDeviceAndConnectionInfo.add(0,mmDevice);
            btDeviceAndConnectionInfo.add(1,mmSocket);
            msg.obj = btDeviceAndConnectionInfo;
            mMainActivityHandler.sendMessage(msg);
            mConnectedDeviceMACs.add(mmDevice.getAddress());
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

}


class BTDevice {
    int deviceType;
    String deviceName;
    UUID uuid;
    boolean devicePaired;
    BluetoothDevice mBtDevice;

    BTDevice(BluetoothDevice btDevice,int deviceType,String deviceName,UUID uuid) {
        mBtDevice = btDevice;
        this.deviceType=deviceType;
        this.deviceName=deviceName;
        this.uuid=uuid;
        devicePaired=true;
    }

    BTDevice(BluetoothDevice btDevice,int deviceType,String deviceName,UUID uuid,boolean devicePaired) {
        mBtDevice = btDevice;
        this.deviceType=deviceType;
        this.deviceName=deviceName;
        this.uuid=uuid;
        this.devicePaired=false;
    }
}

class PairedBtAdapter extends ArrayAdapter<BTDevice> {
    int resourceId;
    Context context;
    List<BTDevice> btDevices;
    public PairedBtAdapter(Context context, int resource, List<BTDevice> objects) {
        super(context, resource, objects);
        resourceId = resource;
        this.context = context;
        btDevices = objects;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        BTDevice btDevice = getItem(position);
        View view;
        ParentViewHolder parentViewHolder;
        if(convertView==null) {
            view = LayoutInflater.from(context).inflate(resourceId,null);
            parentViewHolder = new ParentViewHolder();
            parentViewHolder.typeImage = (ImageView) view.findViewById(R.id.bt_type_image);
            parentViewHolder.name = (TextView) view.findViewById(R.id.bt_name);
            parentViewHolder.infoImage = (ImageButton) view.findViewById(R.id.bt_paried_info_button);
            view.setTag(parentViewHolder);
            if(btDevice.devicePaired==true) {
                parentViewHolder.infoImage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                    }
                });
            }
        }
        else {
            view = convertView;
            parentViewHolder = (ParentViewHolder) view.getTag();
        }
        if(btDevice.devicePaired==true) {
            parentViewHolder.infoImage.setImageResource(R.drawable.bt_info);
            parentViewHolder.infoImage.setVisibility(View.VISIBLE);
        }
        else {
            parentViewHolder.infoImage.setVisibility(View.INVISIBLE);
        }
        parentViewHolder.typeImage.setImageResource(R.drawable.bt_type_image);
        parentViewHolder.name.setText(btDevice.deviceName);
        return view;
    }

    class ParentViewHolder {
        ImageView typeImage;
        TextView name;
        ImageView infoImage;
    }
}
