package pro.dbro.ble.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import pro.dbro.ble.activities.LogConsumer;

/**
 * A basic BLE Peripheral device discovered by centrals
 *
 * Created by davidbrodsky on 10/11/14.
 */
public class BLEPeripheral {
    public static final String TAG = "BLEPeripheral";

    private Context mContext;
    private BluetoothAdapter mBTAdapter;
    private BluetoothLeAdvertiser mAdvertiser;
    private BluetoothGattServer mGattServer;
    private BluetoothGattServerCallback mGattCallback;
    private LogConsumer mLogger;

    private boolean mIsAdvertising = false;

    /** Advertise Callback */
    private AdvertiseCallback mAdvCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            if (settingsInEffect != null) {
                logEvent("Advertise success TxPowerLv="
                        + settingsInEffect.getTxPowerLevel()
                        + " mode=" + settingsInEffect.getMode());
            } else {
                logEvent("Advertise success" );
            }
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            logEvent("Advertising failed with code " + errorCode);
        }
    };

    // <editor-fold desc="Public API">

    public BLEPeripheral(@NonNull Context context) {
        mContext = context;
        init();
    }

    public void setLogConsumer(LogConsumer consumer) {
        mLogger = consumer;
    }

    public void setGattCallback(BluetoothGattServerCallback callback) {
        mGattCallback = callback;
    }

    public void start() {
        startAdvertising();
    }

    public void stop() {
        stopAdvertising();
    }

    public boolean isAdvertising() {
        return mIsAdvertising;
    }

    public BluetoothGattServer getGattServer() {
        return mGattServer;
    }

    // </editor-fold>

    //<editor-fold desc="Private API">

    private void init() {
        // BLE check
        if (!BLEUtil.isBLESupported(mContext)) {
            logEvent("Bluetooth not supported.");
            return;
        }
        BluetoothManager manager = BLEUtil.getManager(mContext);
        if (manager != null) {
            mBTAdapter = manager.getAdapter();
        }
        if (mBTAdapter == null) {
            logEvent("Bluetooth unavailble.");
            return;
        }

    }

    private void startAdvertising() {
        if ((mBTAdapter != null) && (!mIsAdvertising)) {
            if (mAdvertiser == null) {
                mAdvertiser = mBTAdapter.getBluetoothLeAdvertiser();
            }
            logEvent("Starting GATT server");
            startGattServer();
            mAdvertiser.startAdvertising(createAdvSettings(), createAdvData(), mAdvCallback);
        } else {
            if (mIsAdvertising)
                logEvent("Start Advertising called while advertising already in progress");
            else
                logEvent("Start Advertising WTF error");
        }
    }

    private void startGattServer() {
        BluetoothManager manager = BLEUtil.getManager(mContext);
        if (mGattCallback == null)
            mGattCallback = new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                StringBuilder event = new StringBuilder();
                if (newState == BluetoothProfile.STATE_DISCONNECTED)
                    event.append("Disconnected");
                else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    event.append("Connected");
                }

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    event.append(" Successfully to ");
                    event.append(device.getAddress());
                    logEvent("Peripheral " + event.toString());
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                    }
                }

//                Log.i("onConnectionStateChange", event.toString());
                super.onConnectionStateChange(device, status, newState);
            }

            @Override
            public void onServiceAdded(int status, BluetoothGattService service) {
                Log.i("onServiceAdded", service.toString());
                super.onServiceAdded(status, service);
            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
                BluetoothGattCharacteristic localCharacteristic = mGattServer.getService(GATT.SERVICE_UUID).getCharacteristic(characteristic.getUuid());
                if (localCharacteristic != null) {
                    String responseData = "";
                    if (characteristic.getUuid().equals(GATT.MESSAGES_READ_UUID)) {
                        responseData = "msg-r-ack";
                    }
                    else if (characteristic.getUuid().equals(GATT.IDENTITY_READ_UUID)) {
                        responseData = "id-r-ack";
                    }
                    try {
                        logEvent("Recognized CharacteristicReadRequest. Sending response " + responseData);
                        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, responseData.getBytes("UTF-8"));
                    } catch (UnsupportedEncodingException e) {
                        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
                        e.printStackTrace();
                    }
                } else {
                    logEvent("CharacteristicReadRequest. Unrecognized characteristic " + characteristic.getUuid().toString());
                }
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                try {
                    String responseData = "";
                    if (characteristic.getUuid().equals(GATT.MESSAGES_WRITE_UUID)) {
                        responseData = "msg-w-ack";
                    }
                    else if (characteristic.getUuid().equals(GATT.IDENTITY_WRITE_UUID)) {
                        responseData = "id-w-ack";
                    }
                    mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, responseData.getBytes("UTF-8"));
                    logEvent(String.format("Received Write to %s with data: %s ", characteristic.getUuid().toString(), responseData));
                } catch (UnsupportedEncodingException e) {
                    logEvent("CharacteristicWriteRequest. Unable to decode data" );
                    e.printStackTrace();
                }
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            }

            @Override
            public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
                Log.i("onDescriptorReadRequest", descriptor.toString());
                super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            }

            @Override
            public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                Log.i("onDescriptorWriteRequest", descriptor.toString());
                super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            }

            @Override
            public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
                logEvent("onExecuteWrite" + device.toString());
//                Log.i("onExecuteWrite", device.toString());
                super.onExecuteWrite(device, requestId, execute);
            }
        };

        mGattServer = manager.openGattServer(mContext, mGattCallback);

        BluetoothGattService chatService = new BluetoothGattService(GATT.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic identityRead = new BluetoothGattCharacteristic(GATT.IDENTITY_READ_UUID, BluetoothGattCharacteristic.PROPERTY_INDICATE | BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattCharacteristic identityWrite = new BluetoothGattCharacteristic(GATT.IDENTITY_WRITE_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        chatService.addCharacteristic(identityRead);
        chatService.addCharacteristic(identityWrite);

        BluetoothGattCharacteristic messagesRead = new BluetoothGattCharacteristic(GATT.MESSAGES_READ_UUID, BluetoothGattCharacteristic.PROPERTY_INDICATE | BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattCharacteristic messagesWrite = new BluetoothGattCharacteristic(GATT.IDENTITY_WRITE_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        chatService.addCharacteristic(messagesRead);
        chatService.addCharacteristic(messagesWrite);

        mGattServer.addService(chatService);
    }

    private static AdvertiseData createAdvData() {
        // iPad:
        // 4c 00 01 00 00000000 00000010 00000000 000000
        final byte[] manufacturerData = new byte[] {
                //     4c           00           01           00
                (byte) 0x4c, (byte) 0x00, (byte) 0x01, (byte) 0x00, // fix
                //     00           00           00           00
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, // uuid
               //      00           00           00           10
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x10, // uuid
                //     00           00           00           00
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, // uuid
                //     00           00           00
                (byte) 0x00, (byte) 0x00, (byte) 0x00
        };
        AdvertiseData.Builder builder = new AdvertiseData.Builder();
        // Service UUIDS
        // Generic Access
        // Generic Attribute
        // Battery Service
        // Current Time Service
        // D0611E78-BBB4-4591-A5F8-487910AE4366
        //      8667556C-9A37-4C91-84ED-54EE27D90049
        // 7905F431-B5CE-4E99-A40F-4B1E122D00D0
        //      69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9
        //      9FBF120D-6301-42D9-8C58-25E699A21DBD
        //      22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB
        // 217D750E-7B58-4152-A1EB-F2711BB38350
        //      DD4ED52E-58D3-448A-8B9B-44DF52784B5B
        // 89D3502B-0F36-433A-8EF4-C502AD55F8DC
        //      9B3C81D8-57B1-4A8A-B8DF-0E56F7CA51C2
        //      2F7CABCE-808D-411F-9A0C-BB92BA96C102
        //      C6B2F38C-23AB-46D8-A6AB-A3A870BBD5D7

//        uuidList.add(ParcelUuid.fromString("D0611E78-BBB4-4591-A5F8-487910AE4366"));
//        uuidList.add(ParcelUuid.fromString("7905F431-B5CE-4E99-A40F-4B1E122D00D0"));
//        uuidList.add(ParcelUuid.fromString("217D750E-7B58-4152-A1EB-F2711BB38350"));
//        uuidList.add(ParcelUuid.fromString("89D3502B-0F36-433A-8EF4-C502AD55F8DC"));
        builder.addServiceUuid(new ParcelUuid(GATT.SERVICE_UUID));
        builder.setIncludeTxPowerLevel(false);
//        builder.setManufacturerData(0x1234578, manufacturerData);
        return builder.build();
    }

    private static AdvertiseSettings createAdvSettings() {
        AdvertiseSettings.Builder builder = new AdvertiseSettings.Builder();
        builder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
        builder.setConnectable(true);
        //builder.setType(AdvertiseSettings.ADVERTISE_TYPE_CONNECTABLE);
        builder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
        return builder.build();
    }

    private void stopAdvertising() {
        if (mAdvertiser != null) {
            mAdvertiser.stopAdvertising(mAdvCallback);
        }
        mIsAdvertising = false;
    }

    private void logEvent(String event) {
        if (mLogger != null) {
            mLogger.onLogEvent(event);
        }
    }

    // </editor-fold>
}
