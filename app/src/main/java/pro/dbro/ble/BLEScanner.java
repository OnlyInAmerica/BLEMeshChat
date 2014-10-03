package pro.dbro.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
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

import pro.dbro.ble.util.BleUtil;

/**
 * A basic BLE Scanner hardcoded to scan for Service Name = TEST
 *
 * Created by davidbrodsky on 10/2/14.
 */
public class BLEScanner {
    public static final String TAG = "BLEScanner";

    private Context mContext;
    private BluetoothAdapter mBTAdapter;
    private ScanCallback mScanCallback;
    private BluetoothLeScanner mBTAScanner;

    private boolean mIsScanning = false;

    // <editor-fold desc="Public API">

    public BLEScanner(@NonNull Context context) {
        mContext = context;
        init();
        setScanCallback();
    }

    public void start() {
        startScanning();
    }

    public void stop() {
        stopScanning();
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
            @Override
            public void onAdvertisementUpdate(ScanResult scanResult) {
                String toLog = String.format("Attempting GATT Connection to %s", scanResult.getDevice().getName());
                Log.i(TAG, toLog);
                scanResult.getDevice().connectGatt(mContext, true, new BluetoothGattCallback() {
                    @Override
                    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                        Log.i(TAG, "onConnectionStateChange");
                        super.onConnectionStateChange(gatt, status, newState);
                    }

                    @Override
                    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                        Log.i(TAG, "onServicesDiscovered");
                        super.onServicesDiscovered(gatt, status);
                    }

                    @Override
                    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                        Log.i(TAG, "onCharacteristicRead");
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
            if (mBTAScanner == null) {
                mBTAScanner = mBTAdapter.getBluetoothLeScanner();
            }
            mBTAScanner.startScan(createScanFilters(), createScanSettings(), mScanCallback);
            Toast.makeText(mContext, mContext.getString(R.string.scan_started), Toast.LENGTH_SHORT).show();
        }
    }

    private static List<ScanFilter> createScanFilters() {
        ScanFilter.Builder builder = new ScanFilter.Builder();
        builder.setName("Test");
        ArrayList<ScanFilter> scanFilters = new ArrayList<>();
        scanFilters.add(builder.build());
        return scanFilters;
    }

    private static ScanSettings createScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ON_UPDATE);
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);
        return builder.build();
    }

    private void stopScanning() {
        if (mBTAScanner != null) {
            mBTAScanner.stopScan(mScanCallback);
            Toast.makeText(mContext, mContext.getString(R.string.scan_stopped), Toast.LENGTH_SHORT).show();
        }
        mIsScanning = false;
    }

    //</editor-fold>
}
