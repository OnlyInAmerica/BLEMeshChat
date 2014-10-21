package pro.dbro.ble.transport.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import pro.dbro.ble.R;
import pro.dbro.ble.ui.activities.LogConsumer;

/**
 * A basic BLE Central device that discovers peripherals
 *
 * Created by davidbrodsky on 10/2/14.
 */
public class BLECentral {
    public static final String TAG = "BLECentral";
    private static final boolean REPORT_NON_SUCCESSES = false;

    /** Requests to perform against each discovered peripheral */
    private ArrayDeque<BLECentralRequest> mDefaultRequests = new ArrayDeque<>();
    /** Map of request queues by remote peripheral address */
    private HashMap<String, ArrayDeque<BLECentralRequest>> mRequestsForDevice = new HashMap<>();
    /** Set of connected device addresses */
    private HashSet<String> mConnectedDevices = new HashSet<>();

    public interface BLECentralConnectionListener {
        public void connectedTo(String deviceAddress);
        public void disconnectedTo(String deviceAddress);
    }

    public interface BLECentralConnectionGovernor {
        public boolean shouldConnectToPeripheral(ScanResult potentialPeer);
    }

    private Context mContext;
    private BluetoothAdapter mBTAdapter;
    private ScanCallback mScanCallback;
    private BluetoothLeScanner mScanner;
    private BLECentralConnectionGovernor mConnectionGovernor;
    private BLECentralConnectionListener mConnectionListener;
    private LogConsumer mLogger;

    private boolean mIsScanning = false;

    // <editor-fold desc="Public API">

    public BLECentral(@NonNull Context context) {
        mContext = context;
        init();
    }

    public void setLogConsumer(LogConsumer consumer) {
        mLogger = consumer;
    }

    public void setConnectionGovernor(BLECentralConnectionGovernor governor) {
        mConnectionGovernor = governor;
    }

    public void setConnectionListener(BLECentralConnectionListener listener) {
        mConnectionListener = listener;
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

    public HashSet<String> getConnectedDeviceAddresses() {
        return mConnectedDevices;
    }

    /**
     * Add a {@link pro.dbro.ble.transport.ble.BLECentralRequest} to be performed
     * on each peripheral discovered
     */
    public void addDefaultBLECentralRequest(BLECentralRequest request) {
        mDefaultRequests.add(request);
    }
    // </editor-fold>

    //<editor-fold desc="Private API">

    private void init() {
        // BLE check
        if (!BLEUtil.isBLESupported(mContext)) {
            Toast.makeText(mContext, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            return;
        }

        // BT check
        BluetoothManager manager = BLEUtil.getManager(mContext);
        if (manager != null) {
            mBTAdapter = manager.getAdapter();
        }
        if (mBTAdapter == null) {
            Toast.makeText(mContext, R.string.bt_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

    }

    public void setScanCallback(ScanCallback callback) {
        if (callback != null) {
            mScanCallback = callback;
            return;
        }
        mScanCallback = new ScanCallback() {
            @Override
            public void onAdvertisementUpdate(ScanResult scanResult) {
                if (mConnectedDevices.contains(scanResult.getDevice().getAddress())) {
                    // If we're already connected, forget it
                    return;
                }

                if (mConnectionGovernor != null && !mConnectionGovernor.shouldConnectToPeripheral(scanResult)) {
                    // If the BLEConnectionGovernor says we should not bother connecting to this peer, don't
                    return;
                }
                final BluetoothGatt bleGatt = scanResult.getDevice().connectGatt(mContext, false, new BluetoothGattCallback() {
                    @Override
                    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            logEvent("status indicates GATT Connection Success!");
                        } else {
//                            if (REPORT_NON_SUCCESSES) mLogger.onLogEvent("status indicates GATT Connection not yet successful");
                        }

                        switch (newState) {
                            case BluetoothProfile.STATE_DISCONNECTED:
                                Log.i(TAG, "disconnected from " + gatt.getDevice().getName());
                                if (REPORT_NON_SUCCESSES) logEvent("newState indicates GATT disconnected");
                                if (status == BluetoothGatt.GATT_SUCCESS) {
                                    mConnectedDevices.remove(gatt.getDevice().getAddress());
                                    if (mConnectionListener != null) mConnectionListener.disconnectedTo(gatt.getDevice().getAddress());
                                }
                                break;
                            case BluetoothProfile.STATE_CONNECTED:
                                logEvent("newState indicates indicates GATT connected");
                                mConnectedDevices.add(gatt.getDevice().getAddress());
                                if (mConnectionListener != null) mConnectionListener.connectedTo(gatt.getDevice().getAddress());
                                // TODO: Stop discovering services once we can
                                // TOOD: reliably craft characteristics
                                boolean discovering = gatt.discoverServices();
                                logEvent("Discovering services : " + discovering);
                                beginRequestFlowWithPeripheral(gatt);
                                break;
                        }
                        super.onConnectionStateChange(gatt, status, newState);
                    }

                    @Override
                    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                        // TODO: Keep this here to examine characteristics
                        // eventually we should get rid of the discoverServices step
                        boolean foundExpectedCharacteristic = false;
                        List<BluetoothGattService> serviceList = gatt.getServices();
                        for (BluetoothGattService service : serviceList) {
                            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                            for (BluetoothGattCharacteristic characteristic : characteristics) {
                                if (characteristic.getUuid().equals(GATT.IDENTITY_READ_UUID)) {
                                    logEvent("Queuing identity read");
                                }
                                else if (characteristic.getUuid().equals(GATT.IDENTITY_WRITE_UUID)) {
                                    characteristic.setValue("id-w-really long and whatnot ok why not hello goodbye Bonjour Shalom Hola id-w-really long and whatnot ok why not hello goodbye twerky tweet");
                                    logEvent("Queuing identity write");
                                }
                                if (characteristic.getUuid().equals(GATT.MESSAGES_READ_UUID)) {
                                    logEvent("Queuing message read");
                                }
                                else if (characteristic.getUuid().equals(GATT.MESSAGES_WRITE_UUID)) {
                                    logEvent("Queuing message write");
                                    characteristic.setValue("msg-w-reall long and whatnot ok why not hello goodbye Bonjour Shalom Hola id-w-really long and whatnot ok why not hello goodbye twerky tweet");
                                }

                            }
                        }
                        if (foundExpectedCharacteristic)
                            logEvent("Found Characteristic. Requesting Read!");
                        super.onServicesDiscovered(gatt, status);
                    }

                    @Override
                    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                        logEvent(String.format("onCharacteristicRead %s status: %d", characteristic.getStringValue(0), status));
                        handleResponseForCurrentRequestToPeripheral(gatt, BLECentralRequest.RequestType.READ, characteristic, status);
                        super.onCharacteristicRead(gatt, characteristic, status);
                    }

                    @Override
                    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                        logEvent(String.format("onCharacteristicWrite %s with status %d", characteristic.getUuid(), status));
                        handleResponseForCurrentRequestToPeripheral(gatt, BLECentralRequest.RequestType.WRITE, characteristic, status);
                        super.onCharacteristicWrite(gatt, characteristic, status);
                    }

                    @Override
                    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                        String toLog = null;
                        try {
                            toLog = "onCharacteristicChanged value: " + characteristic.getStringValue(0);
                        } catch (Exception e) {
                            // whoops
                            toLog = "onCharacteristicChanged uuid: " + characteristic.getUuid().toString();
                        }
                        logEvent(toLog);
//                        Log.i(TAG, "onCharacteristicChanged");
                        super.onCharacteristicChanged(gatt, characteristic);
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

    private void beginRequestFlowWithPeripheral(BluetoothGatt remotePeripheral) {
        ArrayDeque<BLECentralRequest> requestsForPeripheral = mDefaultRequests.clone();
        String remotePeripheralAddress = remotePeripheral.getDevice().getAddress();
        mRequestsForDevice.put(remotePeripheralAddress, requestsForPeripheral);

        logEvent(String.format("Added %d requests for device %s", requestsForPeripheral.size(), remotePeripheralAddress));
        performCurrentRequestToPeripheral(remotePeripheral);
    }

    private void performCurrentRequestToPeripheral(BluetoothGatt remotePeripheral) {
        String remotePeripheralAddress = remotePeripheral.getDevice().getAddress();
        ArrayDeque<BLECentralRequest> requestsForPeripheral = mRequestsForDevice.get(remotePeripheralAddress);

        if (requestsForPeripheral != null) {
            requestsForPeripheral.peek().doRequest(remotePeripheral);
        } else {
            logEvent(String.format("performCurrentRequestToPeripheral found no requests available for device %s", remotePeripheralAddress));
        }
    }

    private void handleResponseForCurrentRequestToPeripheral(BluetoothGatt remotePeripheral, BLECentralRequest.RequestType type, BluetoothGattCharacteristic characteristic, int status) {
        String remotePeripheralAddress = remotePeripheral.getDevice().getAddress();
        ArrayDeque<BLECentralRequest> requestsForPeripheral = mRequestsForDevice.get(remotePeripheralAddress);

        if (requestsForPeripheral != null) {
            // Check that this response is for the expected characteristic, and is expected type (READ, WRITE etc)
            if (!requestsForPeripheral.peek().mCharacteristic.getUuid().equals(characteristic.getUuid()) ||
                    requestsForPeripheral.peek().mRequestType != type) {
                logEvent(String.format("handleResponseForCurrentRequestToPeripheral expected characteristic %s but got %s !",
                        requestsForPeripheral.peek().mCharacteristic.getUuid().toString(),
                        characteristic.getUuid()));
                logEvent("Request chain stopping");
                return;
            }

            // Handle response
            boolean complete = requestsForPeripheral.peek().handleResponse(remotePeripheral, characteristic, status);
            if (complete) {
                // Request is complete
                requestsForPeripheral.pop();
                logEvent("Request complete!");
            } else {
                // Request should be re-issued
                performCurrentRequestToPeripheral(remotePeripheral);
            }
        } else {
            logEvent(String.format("handleCurrentResponseForPeripheral found no requests available for device %s", remotePeripheralAddress));
        }
    }

    private void startScanning() {
        if ((mBTAdapter != null) && (!mIsScanning)) {
            if (mScanner == null) {
                mScanner = mBTAdapter.getBluetoothLeScanner();
            }
            if (mScanCallback == null) setScanCallback(null);

            mScanner.startScan(createScanFilters(), createScanSettings(), mScanCallback);
            Toast.makeText(mContext, mContext.getString(R.string.scan_started), Toast.LENGTH_SHORT).show();
        }
    }

    private static List<ScanFilter> createScanFilters() {
        ScanFilter.Builder builder = new ScanFilter.Builder();
//        builder.setServiceUuid(new ParcelUuid(GATT.SERVICE_UUID));
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
        } else {
            Log.i(TAG, event);
        }
    }

    //</editor-fold>
}
