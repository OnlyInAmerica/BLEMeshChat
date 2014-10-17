package pro.dbro.ble.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.ConcurrentHashMap;

import pro.dbro.ble.activities.LogConsumer;
import pro.dbro.ble.ChatApp;
import pro.dbro.ble.model.Message;
import pro.dbro.ble.model.MessageTable;
import pro.dbro.ble.model.Peer;

/**
 * A higher level class that manages advertising and scanning for
 * other BLE devices capable of mesh chat.
 *
 * Created by davidbrodsky on 10/12/14.
 */
public class BLEMeshManager {

    private Context mContext;
    private Peer mUser;
    private BLECentral mCentral;
    private BLEPeripheral mPeripheral;
    private BLEManagerCallback mClientCallback;
    private final Object mStateGuard = new Object();
    private BLEConnectionState mState;
    private LogConsumer mLogger;

    private boolean mConnectedToCentral;
    private boolean mConnectedToPeripheral;

    private enum BLEConnectionState {

        DISABLED,

        SEARCHING,

        CONNECTED,

        SYNCED,
    }

    // <editor-fold desc="Public API">

    public BLEMeshManager(@NonNull Context context, @NonNull Peer user) {
        mContext = context;
        mUser = user;
        init();
        startBleServices();
    }

    public void setLogConsumer(LogConsumer logConsumer) {
        mLogger = logConsumer;
    }

    public void setMeshCallback(BLEManagerCallback cb) {
        mClientCallback = cb;
    }

    public void sendMessage(Peer peer, Message outgoingMsg) {
        // Establish connection with peer
        // Send message to peer
    }

    public void stop() {
        stopBleServices();
    }

    // </editor-fold>

    // <editor-fold desc="Private API">

    private void init() {
        mConnectedToCentral = false;
        mConnectedToPeripheral = false;

        mCentral = new BLECentral(mContext);
        mCentral.setScanCallback(mCentralScanCallback);
        mPeripheral = new BLEPeripheral(mContext);
        mPeripheral.setGattCallback(mPeripheralGattCallback);

        changeState(BLEConnectionState.DISABLED);
    }

    private void startBleServices() {
        if (mState != BLEConnectionState.DISABLED) {
            throw new IllegalStateException("startBleServices called in invalid state");
        }
        mCentral.start();
        mPeripheral.start();
        changeState(BLEConnectionState.SEARCHING);
        // TODO: Monitor central , peripheral success, advance to searching
        // only if all's well
    }

    private void stopBleServices() {
        if (mState == BLEConnectionState.DISABLED) {
            throw new IllegalStateException("stopBleServices called in invalid state");
        }
        mCentral.stop();
        mPeripheral.stop();
        changeState(BLEConnectionState.DISABLED);
    }

    private void logEvent(String event) {
        if (mLogger != null) {
            mLogger.onLogEvent(event);
        }
    }

    // <editor-fold desc="MeshCallback">

    public void onConnectedToPeripheral(BluetoothGatt peripheralPeer) {
        if (mConnectedToPeripheral) {
            // ignore
            return;
        }
        synchronized (mStateGuard) {
            mConnectedToPeripheral = true;
            adjustStateForNewConnection();
        }
        // Read Identity Characteristic
        peripheralPeer.readCharacteristic(GATT.IDENTITY_CHARACTERISTIC);
    }

    public void onConnectedToCentral(BluetoothDevice centralPeer) {
        if (mConnectedToCentral) {
            // ignore;
            return;
        }
        synchronized (mStateGuard) {
            mConnectedToCentral = true;
            adjustStateForNewConnection();
            // Establish connection to centralPeer's peripheral
//            if (mState == BLEConnectionState.FULL_DUPLEX) {
//                // We already are connected to the other peer's peripheral
//                return;
//            }
        }
        centralPeer.connectGatt(mContext, true, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
//                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
//                    boolean result = gatt.readCharacteristic(GATT.MESSAGES_CHARACTERISTIC);
//                    if (result) {
//                        synchronized (mStateGuard) {
//                            changeState(BLEConnectionState.SYNCING);
//                        }
//                    }
//                }
                super.onConnectionStateChange(gatt, status, newState);
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (characteristic == GATT.MESSAGES_CHARACTERISTIC) {
                    // TODO: Repeat read until all request data received
                }
                super.onCharacteristicRead(gatt, characteristic, status);
            }
        });
    }

    // </editor-fold desc="MeshCallback">

    private void adjustStateForNewConnection() {
        switch (mState) {
//            case SEARCHING:
//                changeState(BLEConnectionState.HALF_DUPLEX);
//                // TODO: Start timer. Tear down connection if not reciprocated
//                // in some interval
//                break;
//            case HALF_DUPLEX:
//                changeState(BLEConnectionState.FULL_DUPLEX);
//                break;
//            default:
//                // Ignore
//                logEvent("Got peripheral connection in invalid state " + mState);
        }
    }

    private void changeState(BLEConnectionState newState) {
            mState = newState;
    }

    /** The sequence of actions that describe a complete pairing
     *  Note it is up to the other device to read this device's id */
    public enum PAIR_SEQUENCE { READ_ID, READ_MESSAGES, WRITE_MESSAGES }

    /** Addresses which are currently connected mapped to BluetoothGatts */
    private ConcurrentHashMap<String, BluetoothGatt> mAddressesConnectedTo = new ConcurrentHashMap<>();
    /** Peers which are currently connected mapped to BluetoothGatts */
    private ConcurrentHashMap<Peer, BluetoothGatt> mPeersToAddresses = new ConcurrentHashMap<>();

    /** Messages to send on each successive read to Messages Characteristic */
    private Cursor mMessagesToSend;

    /** Central Callbacks */
    private ScanCallback mCentralScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult scanResult) {
            super.onScanResult(callbackType, scanResult);
            if (!mAddressesConnectedTo.contains(scanResult.getDevice().getAddress())) {
                scanResult.getDevice().connectGatt(mContext, false, mCentralGattCallback);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            logEvent("Scan failed with code " + errorCode);
        }
    };

    private BluetoothGattCallback mCentralGattCallback = new BluetoothGattCallback() {
        public static final String TAG = "BluetoothGattCallback";
        @Override
        public void onConnectionStateChange(BluetoothGatt remotePeripheral, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logEvent("status indicates GATT Connection Success!");
            } else {
                Log.i(TAG, "Connection not successful with status " + status);
            }

            switch (newState) {
                case BluetoothProfile.STATE_DISCONNECTED:
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        //Should we re-attempt to connect to same device this session?
                        mAddressesConnectedTo.remove(remotePeripheral.getDevice().getAddress());
                    }
                    break;
                case BluetoothProfile.STATE_CONNECTED:
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        mAddressesConnectedTo.put(remotePeripheral.getDevice().getAddress(), remotePeripheral);
                    }
                    logEvent("newState indicates indicates GATT connected");
                    boolean discovering = remotePeripheral.discoverServices();
                    logEvent("Discovering services : " + discovering);
                    break;
            }
            super.onConnectionStateChange(remotePeripheral, status, newState);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }
    };

    /** Peripheral Callbacks */

    BluetoothGattServerCallback mPeripheralGattCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            StringBuilder event = new StringBuilder();
            if (newState == BluetoothProfile.STATE_DISCONNECTED)
                event.append("Disconnected");
            else if (newState == BluetoothProfile.STATE_CONNECTED) {
                event.append("Connected");
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (!mAddressesConnectedTo.contains(device.getAddress())) {
                        // TODO : I only call connectGatt here to get a reference to the device's
                        // BluetoothGatt. Hopefully, this call returns immediately as the local
                        // peripheral as indicated connection complete.
                        mAddressesConnectedTo.put(device.getAddress(), device.connectGatt(mContext, false, mCentralGattCallback));
                    }
                }
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                event.append(" Successfully to ");
                event.append(device.getAddress());
                logEvent("Peripheral " + event.toString());
            }
            super.onConnectionStateChange(device, status, newState);
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            byte[] responseData = null;
            try {
                /** Messages Read Request */
                if (characteristic.getUuid().equals(GATT.MESSAGES_READ_UUID)) {
                    if (mMessagesToSend == null) {
                        // We received a Read
                        queueMessagesForTransmission();
                        logEvent(String.format("Got Messages read request. Queued %d messages for delivery", mMessagesToSend.getCount()));
                    }
                    // Send next message in queue or report no messages to send
                    if (mMessagesToSend != null && mMessagesToSend.moveToNext()) {
                        // Respond with next message in queue
                        responseData = getResponseForMessage(mMessagesToSend);
                        logEvent("Sending next message in read response");
                        mPeripheral.getGattServer().sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, responseData);
                    } else if (mMessagesToSend != null) {
                        // We've sent all messages. Close Cursor and send READ_NOT_PERMITTED
                        mMessagesToSend.close();
                        mMessagesToSend = null;
                        logEvent("Sent all messages. Sending end-of-messages response");
                        mPeripheral.getGattServer().sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, null);
                    } else {
                        // There were no messages to send (mMessagesToSend was null). send READ_NOT_PERMITTED
                        logEvent("No messages to send. Sending end-of-messages response");
                        mPeripheral.getGattServer().sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, null);
                    }
                }
                /** Identity Read Request */
                else if (characteristic.getUuid().equals(GATT.IDENTITY_READ_UUID)) {
                    responseData = ChatApp.getPrimaryIdentityResponse(mContext);
                }

                if (responseData != null) {
                    logEvent("Recognized CharacteristicReadRequest. Sent response " + new String(responseData, "UTF-8"));
                } else {
                    logEvent("CharacteristicReadRequest Failure. Failed to generate response data");
                    mPeripheral.getGattServer().sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
                }
            }
            catch (UnsupportedEncodingException e) {
                mPeripheral.getGattServer().sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
                e.printStackTrace();
            }
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            byte[] responseData = null;
            try {
                /** Messages Write Request */
                if (characteristic.getUuid().equals(GATT.MESSAGES_WRITE_UUID)) {
                    logEvent("Got Message write request with data " + new String(value, "UTF-8") + " response needed " + responseNeeded);
                    ChatApp.consumeReceivedBroadcastMessage(mContext, value);
                    if (responseNeeded) {
                        mPeripheral.getGattServer().sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
                    }
                }
                /** Identity Write Request */
                else if (characteristic.getUuid().equals(GATT.IDENTITY_WRITE_UUID)) {
                    logEvent("Got Identity write request with data " + new String(value, "UTF-8") + " response needed " + responseNeeded);
                    ChatApp.consumeReceivedIdentity(mContext, value);
                    if (responseNeeded) {
                        mPeripheral.getGattServer().sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
                    }
                }

                // TODO: Send error code when needed

//                if (responseData != null) {
//                    logEvent("Recognized CharacteristicReadRequest. Sent response " + new String(responseData, "UTF-8"));
//                } else {
//                    logEvent("CharacteristicReadRequest Failure. Failed to generate response data");
//                    mPeripheral.getGattServer().sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
//                }
            }
            catch (UnsupportedEncodingException e) {
                mPeripheral.getGattServer().sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
                e.printStackTrace();
            }
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
        }
    };

    private void queueMessagesForTransmission() {
        Cursor messages = ChatApp.getMessagesToSend(mContext);
        if (messages != null && messages.getCount() > 0) {
            mMessagesToSend = messages;
        }
    }

    /**
     * A cursor pre-moved to a row corresponding to the message
     * to send
     */
    private byte[] getResponseForMessage(Cursor message) {
        return ChatApp.getBroadcastMessageResponseForString(mContext,
                                    message.getString(message.getColumnIndex(MessageTable.body)));
    }

    // </editor-fold desc="Private API">
}
