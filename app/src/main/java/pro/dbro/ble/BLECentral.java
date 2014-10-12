package pro.dbro.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import pro.dbro.ble.util.BleUtil;
import pro.dbro.ble.util.BleUuid;

/**
 * A basic BLE Central device hardcoded to scan for Service Name = TEST
 *
 * Created by davidbrodsky on 10/2/14.
 */
public class BLECentral {
    public static final String TAG = "BLECentral";
    private static final boolean REPORT_NON_SUCCESSES = false;

    private Context mContext;
    private BluetoothAdapter mBTAdapter;
    private ScanCallback mScanCallback;
    private BluetoothLeScanner mScanner;
    private LogConsumer mLogger;

    private boolean mIsScanning = false;

    // <editor-fold desc="Public API">

    public BLECentral(@NonNull Context context) {
        mContext = context;
        init();
        setScanCallback();
    }

    public void setLogConsumer(LogConsumer consumer) {
        mLogger = consumer;
    }

    public void start() {
        startScanning();
    }

    public void stop() {
        stopScanning();
    }

    public boolean isIsScanning() {
        return mIsScanning;
    }
    // </editor-fold>

    //<editor-fold desc="Private API">

    private void init() {
        // BLE check
        if (!BleUtil.isBLESupported(mContext)) {
            Toast.makeText(mContext, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            return;
        }

        // BT check
        BluetoothManager manager = BleUtil.getManager(mContext);
        if (manager != null) {
            mBTAdapter = manager.getAdapter();
        }
        if (mBTAdapter == null) {
            Toast.makeText(mContext, R.string.bt_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

    }

    private void setScanCallback() {
        mScanCallback = new ScanCallback() {
            final String TAG = "ScanCallback";

            private boolean connected = false;

            @Override
            public void onAdvertisementUpdate(ScanResult scanResult) {
                String toLog = String.format("Scanned %s", scanResult.getDevice().getName());
                Log.i(TAG, toLog);

//                Only attempt one connection at a time
                if (connected) return;
                if (REPORT_NON_SUCCESSES) Log.i(TAG, "Got new advertisement before connection");
                BluetoothGatt bleGatt = scanResult.getDevice().connectGatt(mContext, true, new BluetoothGattCallback() {
                    @Override
                    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
//                        final String TAG = "onConnectionStateChange";

                        if (status == BluetoothGatt.GATT_SUCCESS) {
//                            Log.i(TAG, "status indicates GATT Connection Success!");
                            logEvent("status indicates GATT Connection Success!");
                        } else {
                            if (REPORT_NON_SUCCESSES) mLogger.onLogEvent("status indicates GATT Connection not yet successful");
                        }

                        switch (newState) {
                            case BluetoothProfile.STATE_DISCONNECTED:
                                if (REPORT_NON_SUCCESSES) logEvent("newState indicates GATT disconnected");
                                break;
                            case BluetoothProfile.STATE_CONNECTED:
                                logEvent("newState indicates indicates GATT connected");
                                connected = true;
                                break;
                        }

                        boolean discovering = gatt.discoverServices();
                        if (REPORT_NON_SUCCESSES || discovering)
                            logEvent("Discovering services : " + discovering);
                        UUID characteristicUUID = UUID.fromString(BleUuid.MESH_CHAT_CHARACTERISTIC_UUID);
//                        boolean readResult = gatt.readCharacteristic(new BluetoothGattCharacteristic(characteristicUUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.));
//                        if (REPORT_NON_SUCCESSES || readResult)
//                            logEvent("Attempting to read characteristic " + readResult);
                        super.onConnectionStateChange(gatt, status, newState);
                    }

                    @Override
                    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                        boolean foundExpectedCharacteristic = false;
//
                        List<BluetoothGattService> serviceList = gatt.getServices();
//                        StringBuilder services = new StringBuilder();
                        for (BluetoothGattService service : serviceList) {
//                            services.append("Service:\n ");
                            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                            for (BluetoothGattCharacteristic characteristic : characteristics) {
//                                services.append(service.getUuid().toString());
//                                services.append("\tCharacteristic:\n");
//                                services.append("\t\t");
//                                services.append(characteristic.getUuid());
//                                services.append("\tProperties :\n");
//                                services.append("\t\t");
//                                services.append(characteristic.getProperties());
                                if (characteristic.getUuid().toString().toLowerCase().equals(BleUuid.MESH_CHAT_CHARACTERISTIC_UUID.toLowerCase())) {
                                    foundExpectedCharacteristic = true;
//                                    services.append("\tValue :\n");
//                                    services.append("\t\t");
//                                    services.append(characteristic.getValue());
                                    gatt.readCharacteristic(characteristic);
                                }
                            }
//                            services.append("\n");
                        }
//                        logEvent("Services Discovered " + services.toString());
                        if (foundExpectedCharacteristic)
                            logEvent("Found Characteristic. Requesting Read!");
                        super.onServicesDiscovered(gatt, status);
                    }

                    @Override
                    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                        logEvent("onCharacteristicRead " + characteristic.getStringValue(0));
                        super.onCharacteristicRead(gatt, characteristic, status);
                    }

                    @Override
                    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                        Log.i(TAG, "onCharacteristicWrite");
                        super.onCharacteristicWrite(gatt, characteristic, status);
                    }

                    @Override
                    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                        Log.i(TAG, "onCharacteristicChanged");
                        super.onCharacteristicChanged(gatt, characteristic);
                    }

                    @Override
                    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                        Log.i(TAG, "onDescriptorRead");
                        super.onDescriptorRead(gatt, descriptor, status);
                    }

                    @Override
                    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                        Log.i(TAG, "onDescriptorWrite");
                        super.onDescriptorWrite(gatt, descriptor, status);
                    }

                    @Override
                    public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                        Log.i(TAG, "onReliableWriteCompleted");
                        super.onReliableWriteCompleted(gatt, status);
                    }

                    @Override
                    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                        Log.i(TAG, "onReadRemoteRssi");
                        super.onReadRemoteRssi(gatt, rssi, status);
                    }
                });

//                bleGatt.setCharacteristicNotification(new BluetoothGattCharacteristic(UUID.fromString(BleUuid.MESH_CHAT_CHARACTERISTIC_UUID), 0, 0), true);
            }

            @Override
            public void onScanFailed(int i) {
                String toLog = "Scan failed with code " + i;
                Log.i(TAG, toLog);
            }
        };
    }

    private void startScanning() {
        if ((mBTAdapter != null) && (!mIsScanning)) {
            if (mScanner == null) {
                mScanner = mBTAdapter.getBluetoothLeScanner();
            }
            mScanner.startScan(createScanFilters(), createScanSettings(), mScanCallback);
            Toast.makeText(mContext, mContext.getString(R.string.scan_started), Toast.LENGTH_SHORT).show();
        }
    }

    private static List<ScanFilter> createScanFilters() {
        ScanFilter.Builder builder = new ScanFilter.Builder();
//        builder.setName("Test");
//        builder.setServiceUuid(ParcelUuid.fromString(BleUuid.MESH_CHAT_SERVICE_UUID));
//        builder.setName("BLEMeshChat");
        ArrayList<ScanFilter> scanFilters = new ArrayList<>();
        scanFilters.add(builder.build());
        return scanFilters;
    }

    private static ScanSettings createScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ON_UPDATE);
        builder.setScanMode(ScanSettings.SCAN_MODE_BALANCED);
        return builder.build();
    }

    private void stopScanning() {
        if (mScanner != null) {
            mScanner.stopScan(mScanCallback);
            Toast.makeText(mContext, mContext.getString(R.string.scan_stopped), Toast.LENGTH_SHORT).show();
        }
        mIsScanning = false;
    }

    private void logEvent(String event) {
        if (mLogger != null) {
            mLogger.onLogEvent(event);
        }
    }

    //</editor-fold>
}
