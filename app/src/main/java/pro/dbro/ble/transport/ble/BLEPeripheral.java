package pro.dbro.ble.transport.ble;

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
import android.util.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import pro.dbro.ble.data.model.DataUtil;
import pro.dbro.ble.protocol.BLEProtocol;
import pro.dbro.ble.transport.ConnectionGovernor;
import pro.dbro.ble.transport.ConnectionListener;
import pro.dbro.ble.ui.activities.LogConsumer;

/**
 * A basic BLE Peripheral device discovered by centrals
 *
 * Created by davidbrodsky on 10/11/14.
 */
public class BLEPeripheral {
    public static final String TAG = "BLEPeripheral";

    /** Map of Responses to perform keyed by request characteristic & type pair */
    private HashMap<Pair<UUID, BLEPeripheralResponse.RequestType>, BLEPeripheralResponse> mResponses = new HashMap<>();
    /** Set of connected device addresses */
    private HashSet<String> mConnectedDevices = new HashSet<>();
    /** Map of cached response payloads keyed by request characteristic & type pair.
     * Used to respond to repeat requests provided by the framework when packetization is necessary
     * TODO We need to create a Key object that is specific to remote central, e.g: What happens when we're doing this with multiple centrals at once?
     */
    private HashMap<Pair<UUID, BLEPeripheralResponse.RequestType>, byte[]> mCachedResponsePayloads = new HashMap<>();

    /** Set this value on each call to onCharacteristicWrite. If onExecuteWrite() is called before
     * all data is collected via calls to onCharacteristicWriteRequest,
     * use this key to resume the write request at the appropriate offset.
     */
    private Pair<UUID, BLEPeripheralResponse.RequestType> mLastRequestKey;

    public interface BLEPeripheralConnectionGovernor {
        public boolean shouldConnectToCentral(BluetoothDevice potentialPeer);
    }

    private Context mContext;
    private BluetoothAdapter mBTAdapter;
    private BluetoothLeAdvertiser mAdvertiser;
    private BluetoothGattServer mGattServer;
    private BluetoothGattServerCallback mGattCallback;
    private ConnectionGovernor mConnectionGovernor;
    private ConnectionListener mConnectionListener;
    private LogConsumer mLogger;

    private boolean mIsAdvertising = false;

    /** Advertise Callback */
    private AdvertiseCallback mAdvCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
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
            logEvent("Advertising failed with code " + errorCode);
        }
    };

    // <editor-fold desc="Public API">

    public BLEPeripheral(@NonNull Context context) {
        mContext = context;
        init();
    }

    public void setConnectionListener(ConnectionListener listener) {
        mConnectionListener = listener;
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

    public void addDefaultBLEPeripheralResponse(BLEPeripheralResponse response) {
        Pair<UUID, BLEPeripheralResponse.RequestType> requestFilter = new Pair<>(response.mCharacteristic.getUuid(), response.mRequestType);
        mResponses.put(requestFilter, response);
        logEvent(String.format("Registered %s response for %s", requestFilter.second, requestFilter.first));
    }

    public HashSet<String> getConnectedDeviceAddresses() {
        return mConnectedDevices;
    }

    public void setConnectionGovernor(ConnectionGovernor governor) {
        mConnectionGovernor = governor;
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
            if (mAdvertiser != null) {
                logEvent("Starting GATT server");
                startGattServer();
                mAdvertiser.startAdvertising(createAdvSettings(), createAdvData(), mAdvCallback);
            } else {
                logEvent("Unable to access Bluetooth LE Advertiser. Device not supported");
            }
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
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (mConnectedDevices.contains(device.getAddress())) {
                        // We're already connected (should never happen). Cancel connection
                        logEvent("Denied connection. Already connected to " + device.getAddress());
                        mGattServer.cancelConnection(device);
                        return;
                    }

                    if (mConnectionGovernor != null && !mConnectionGovernor.shouldConnectToAddress(device.getAddress())) {
                        // The ConnectionGovernor denied the connection. Cancel connection
                        logEvent("Denied connection. ConnectionGovernor denied " + device.getAddress());
                        mGattServer.cancelConnection(device);
                        return;
                    } else {
                        // Allow connection to proceed. Mark device connected
                        logEvent("Accepted connection to " + device.getAddress());
                        mConnectedDevices.add(device.getAddress());
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // We've disconnected
                    logEvent("Disconnected from " + device.getAddress());
                    mConnectedDevices.remove(device.getAddress());
                    if (mConnectionListener != null)
                        mConnectionListener.disconnectedFrom(device.getAddress());
                }
                super.onConnectionStateChange(device, status, newState);
            }

            @Override
            public void onServiceAdded(int status, BluetoothGattService service) {
                Log.i("onServiceAdded", service.toString());
                super.onServiceAdded(status, service);
            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice remoteCentral, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
                logEvent(String.format("onCharacteristicReadRequest for request %d on characteristic %s with offset %d", requestId, characteristic.getUuid().toString().substring(0,3), offset));

                BluetoothGattCharacteristic localCharacteristic = mGattServer.getService(GATT.SERVICE_UUID).getCharacteristic(characteristic.getUuid());
                Pair<UUID, BLEPeripheralResponse.RequestType> requestKey = new Pair<>(characteristic.getUuid(), BLEPeripheralResponse.RequestType.READ);

                if (localCharacteristic != null || !mResponses.containsKey(requestKey)) {
                    byte[] cachedResponse = null;
                    if (offset > 0) {
                        // This is a framework-generated follow-up request for another section of data
                        cachedResponse = mCachedResponsePayloads.get(requestKey);
                    } else if (mResponses.containsKey(requestKey)) {
                        // This is a fresh request with a registered response
                        cachedResponse = mResponses.get(requestKey).respondToRequest(mGattServer, remoteCentral, requestId, characteristic, false, true, null);
                        if (cachedResponse != null) {
                            // If a request was necessary, cache the result here
                            mCachedResponsePayloads.put(requestKey, cachedResponse);
                        } else {
                            // No data was available for peer.
                            mGattServer.sendResponse(remoteCentral, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null);
                            return;
                        }
                    }

                    if (cachedResponse == null) {
                        // A request with nonzero offset came through before the initial zero offset request
                        Log.w(TAG, "Invalid request order! Did a nonzero offset request come first?");
                        mGattServer.sendResponse(remoteCentral, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null);
                        return;
                    }

                    byte[] toSend = new byte[cachedResponse.length - offset];
                    System.arraycopy(cachedResponse, offset, toSend, 0, toSend.length);
                    logEvent(String.format("Sending extended response chunk for offset %d : %s", offset, DataUtil.bytesToHex(toSend)));
                    try {
                        boolean success = mGattServer.sendResponse(remoteCentral, requestId, BluetoothGatt.GATT_SUCCESS, offset, toSend);
                        Log.w("SendResponse", "oncharacteristicread follow-up success: " + success);
                    } catch (NullPointerException e) {
                        // On Nexus 5 possibly an issue in the Broadcom IBluetoothGatt implementation
                        Log.w("SendResponse", "NPE on oncharacteristicread follow-up");
                    }

                } else {
                    logEvent("CharacteristicReadRequest. Unrecognized characteristic " + characteristic.getUuid().toString());
                    // Request for unrecognized characteristic. Send GATT_FAILURE
                    try {
                        boolean success = mGattServer.sendResponse(remoteCentral, requestId, BluetoothGatt.GATT_FAILURE, 0, new byte[] { 0x00 });
                        Log.w("SendResponse", "oncharacteristicread failure. success: " + success);
                    } catch (NullPointerException e) {
                        // On Nexus 5 possibly an issue in the Broadcom IBluetoothGatt implementation
                        Log.w("SendResponse", "NPE oncharacteristicread failure.");
                    }
                }
                super.onCharacteristicReadRequest(remoteCentral, requestId, offset, characteristic);
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice remoteCentral, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                logEvent(String.format("onCharacteristicWriteRequest for request %d on characteristic %s with offset %d", requestId, characteristic.getUuid().toString().substring(0,3), offset));

                BluetoothGattCharacteristic localCharacteristic = mGattServer.getService(GATT.SERVICE_UUID).getCharacteristic(characteristic.getUuid());
                if (localCharacteristic != null) {
                    byte[] updatedData;
                    Pair<UUID, BLEPeripheralResponse.RequestType> requestKey = new Pair<>(characteristic.getUuid(), BLEPeripheralResponse.RequestType.WRITE);
                    if (!mResponses.containsKey(requestKey)) {
                        Log.e(TAG, "onCharacteristicWrite for request (" + GATT.getNameForCharacteristic(characteristic) + " without registered response! Ignoring");
                        return;
                    }
                    if (offset == 0) {
                        // This is a fresh write request so start recording data.
                        updatedData = value;
                        mCachedResponsePayloads.put(requestKey, updatedData); // Cache the payload data in case more is coming
                        logEvent(String.format("onCharacteristicWriteRequest had %d bytes, offset : %d", updatedData == null ? 0 : updatedData.length, offset));

                        if (responseNeeded) {
                            // Signal we received the write
                            try {
                                boolean success = mGattServer.sendResponse(remoteCentral, requestId, BluetoothGatt.GATT_SUCCESS, offset, updatedData);
                                Log.w("SendResponse", "oncharwrite success: " + success);
                            } catch (NullPointerException e) {
                                // On Nexus 5 possibly an issue in the Broadcom IBluetoothGatt implementation
                                Log.w("SendResponse", "NPE oncharwrite");
                            }
                            logEvent("Notifying central we received data");
                        }
                    } else {
                        // this is a subsequent request with more data. Append to what we've received so far
                        byte[] cachedResponse = mCachedResponsePayloads.get(requestKey);
                        int cachedResponseLength = (cachedResponse == null ? 0 : cachedResponse.length);
                        //if (cachedResponse.length != offset) logEvent(String.format("Got more data. Original payload len %d. offset %d (should be equal)", cachedResponse.length, offset));
                        updatedData = new byte[cachedResponseLength + value.length];
                        if (cachedResponseLength > 0)
                            System.arraycopy(cachedResponse, 0, updatedData, 0, cachedResponseLength);

                        System.arraycopy(value, 0, updatedData, cachedResponseLength, value.length);
                        logEvent(String.format("Got %d bytes for write request. New bytes: %s", updatedData.length, DataUtil.bytesToHex(value)));
                        logEvent(String.format("Accumulated bytes: %s", DataUtil.bytesToHex(updatedData)));
                    }

                    BLEPeripheralResponse response = mResponses.get(requestKey);
                    if (response.getExpectedPayloadLength() == updatedData.length) {
                        // We've accumulated all the data we need!
                        logEvent(String.format("Accumulated all data for %s request ", GATT.getNameForCharacteristic(response.mCharacteristic)));
                        response.respondToRequest(mGattServer, remoteCentral, requestId, characteristic, preparedWrite, responseNeeded, updatedData);
                        mCachedResponsePayloads.remove(requestKey);
                    } else if (responseNeeded) {
                        // Signal we received the partial write and are ready for more data
                        try {
                            boolean success = mGattServer.sendResponse(remoteCentral, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);

                            Log.w("SendResponse", "oncharwrite follow-up success: " + success);
                        } catch (NullPointerException e) {
                            // On Nexus 5 possibly an issue in the Broadcom IBluetoothGatt implementation
                            Log.w("SendResponse", "NPE oncharwrite follow-up");
                        }
                        logEvent("Notifying central we received data");
                        mCachedResponsePayloads.put(requestKey, updatedData);
                        mLastRequestKey = requestKey;
                    }

                } else {
                    logEvent("CharacteristicWriteRequest. Unrecognized characteristic " + characteristic.getUuid().toString());
                    // Request for unrecognized characteristic. Send GATT_FAILURE
                    try {
                        boolean success = mGattServer.sendResponse(remoteCentral, requestId, BluetoothGatt.GATT_FAILURE, 0, null);

                        Log.w("SendResponse", "write request gatt failure success " + success);
                    } catch (NullPointerException e) {
                        // On Nexus 5 possibly an issue in the Broadcom IBluetoothGatt implementation
                        Log.w("SendResponse", "NPE on write request gatt failure");
                    }
                }
                super.onCharacteristicWriteRequest(remoteCentral, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
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
                logEvent("onExecuteWrite " + device.toString() + " requestId " + requestId + " Last request key " + mLastRequestKey);
                if (mCachedResponsePayloads.containsKey(mLastRequestKey)) {
                    logEvent("onExecuteWrite called before request finished for " + mLastRequestKey.first);
                    // TODO : What is the purpose of this method? I can't call sendRespone without BluetoothGattCharacteristic
                    // and other parameters from onCharacteristicWrite
                }
                super.onExecuteWrite(device, requestId, execute);
            }
        };

        mGattServer = manager.openGattServer(mContext, mGattCallback);
        setupGattServer();
    }

    private void setupGattServer() {
        if (mGattServer != null) {
            BluetoothGattService chatService = new BluetoothGattService(GATT.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

            chatService.addCharacteristic(GATT.IDENTITY_READ);
            chatService.addCharacteristic(GATT.IDENTITY_WRITE);

            chatService.addCharacteristic(GATT.MESSAGES_READ);
            chatService.addCharacteristic(GATT.MESSAGES_WRITE);
            mGattServer.addService(chatService);
        }
    }

    private static AdvertiseData createAdvData() {
        AdvertiseData.Builder builder = new AdvertiseData.Builder();
        builder.addServiceUuid(new ParcelUuid(GATT.SERVICE_UUID));
        builder.setIncludeTxPowerLevel(false);
//        builder.setManufacturerData(0x1234578, manufacturerData);
        return builder.build();
    }

    private static AdvertiseSettings createAdvSettings() {
        AdvertiseSettings.Builder builder = new AdvertiseSettings.Builder();
        builder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
        builder.setConnectable(true);
        builder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
        return builder.build();
    }

    private void stopAdvertising() {
        if (mAdvertiser != null) {
            mAdvertiser.stopAdvertising(mAdvCallback);
        }
        mIsAdvertising = false;
        mGattServer.close();
    }

    private void logEvent(String event) {
        if (mLogger != null) {
            mLogger.onLogEvent(event);
        } else {
            Log.i(TAG, event);
        }
    }

    // </editor-fold>
}
