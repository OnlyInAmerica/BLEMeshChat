package pro.dbro.ble.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.ArrayDeque;
import java.util.List;
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

    /** Addresses which are currently connected mapped to BluetoothGatts */
    private ConcurrentHashMap<String, BluetoothGatt> mAddressesConnectedTo = new ConcurrentHashMap<>();
    /** Peers which are currently connected mapped to BluetoothGatts */
    private ConcurrentHashMap<Peer, BluetoothGatt> mPeersToAddresses = new ConcurrentHashMap<>();

    /** Messages to send
     * As a peripheral:
     *      Send a message on each received read to Messages Characteristic
     * As a central:
     *      Send a message on each initiated write to Message Characteristic
     * */
    private Cursor mMessagesToSend;

    /*****************************************
     *            Central Callbacks          *
     *   Initiates read and write requests   *
     *****************************************
     *
     * - Initiate Reads to Peripheral Characteristics
     *   (Receiving data)
     *
     *      For each target characteristic the central device
     *      initiates an initial read request. The central
     *      than receives data responses onCharacteristicRead
     *      and repeats with a follow up request until the
     *      {@link BluetoothGatt#GATT_READ_NOT_PERMITTED} status
     *      code is returned.
     *
     * - Initiates Writes to Peripheral Characteristics
     *   (Sending data)
     *
     *      The central device acts in a similar manner when
     *      writing data to the remote peripheral. Generally
     *      the central client will attempt to read before writing.
     *
     */
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

        private boolean connected = false;

        /** Queue of characteristics to read from the remote peripheral
         *
         * The containing BluetoothGattCallback will queue all characteristics before initiating the
         * first read. On each resulting call to onCharacteristicRead if the GATT status code is
         * {@link BluetoothGatt#GATT_READ_NOT_PERMITTED} the peripheral has sent all available data
         * for the requested characteristic. Therefore the next characteristic in {@link #mCharacteristicsToRead}
         * is de-queued and a request for it made to the remote peripheral. Data is received and the
         * request repeated until the GATT status code received in onCharacteristicRead
         * is {@link BluetoothGatt#GATT_READ_NOT_PERMITTED}
         */
        ArrayDeque<BluetoothGattCharacteristic> mCharacteristicsToRead = new ArrayDeque<>();

        /**
         * Queue of characteristics to write to the remote peripheral
         *
         * The containing BluetoothGattCallback will queue all characteristics before initiating the
         * first read. Generally all reads are completed before writing begins.
         * When writing begins data is queued for the target characteristic and the first write
         * request is sent. On each resulting call to OnCharacteristicWrite the next
         * characteristic is removed from the queue, it's data queued, and the responses sent as before.
         */
        ArrayDeque<BluetoothGattCharacteristic> mCharacteristicsToWrite = new ArrayDeque<>();

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
                    // TODO: Don't discover services. Go right to GATT reading / writing
                    boolean discovering = remotePeripheral.discoverServices();
                    logEvent("Discovering services : " + discovering);
                    break;
            }
            super.onConnectionStateChange(remotePeripheral, status, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            List<BluetoothGattService> serviceList = gatt.getServices();
            for (BluetoothGattService service : serviceList) {
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                for (BluetoothGattCharacteristic characteristic : characteristics) {
                    if (characteristic.getUuid().equals(GATT.IDENTITY_READ_UUID)) {
                        logEvent("Queuing identity read");
                        queueReadOp(gatt, characteristic);
                    }
                    else if (characteristic.getUuid().equals(GATT.IDENTITY_WRITE_UUID)) {
                        // TODO: Propagate Identities
                        //logEvent("Queuing identity write");
                        //queueWriteOp(gatt, characteristic);
                    }
                    if (characteristic.getUuid().equals(GATT.MESSAGES_READ_UUID)) {
                        // Request to receive peripheral's messages
                        logEvent("Queuing message read");
                        queueReadOp(gatt, characteristic);
                    }
                    else if (characteristic.getUuid().equals(GATT.MESSAGES_WRITE_UUID)) {
                        // Request to send peripheral our messages
                        logEvent("Queuing message write");
                        // TODO: Queue messages
                        queueWriteOp(gatt, characteristic);
                    }

                }
            }
            super.onServicesDiscovered(gatt, status);
        }

        /**
         * Callback with data from prior request
         * Consume data and repeat request if more data available
         */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            logEvent(String.format("onCharacteristicRead %s status: %d", characteristic.getStringValue(0), status));
            /** Messages Read Response */
            if (characteristic.getUuid().equals(GATT.MESSAGES_READ_UUID)) {
                consumeMessageReadResponse(characteristic.getValue());
            }
            /** Identity Read Response **/
            else if (characteristic.getUuid().equals(GATT.IDENTITY_READ_UUID)) {
                consumeIdentityReadResponse(characteristic.getValue());
            } else {
                logEvent("Got OnCharacteristicRead for unknown characteristic " + characteristic.getUuid().toString());
            }

            // Perform another request if GATT status indicates more data available
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // If more data available, begin the next read
                gatt.readCharacteristic(characteristic);
            } else if (status == BluetoothGatt.GATT_READ_NOT_PERMITTED || status == BluetoothGatt.GATT_FAILURE) {
                // No more data available for this characteristic
                // Move on to next characteristic
                if (mCharacteristicsToRead.size() > 0) {
                    // Continue reading more interesting peripheral characteristics
                    BluetoothGattCharacteristic ch = mCharacteristicsToRead.pop();
                    logEvent("reading char " + ch.getUuid().toString());
                    boolean result = gatt.readCharacteristic(ch);
                    logEvent(String.format("Sending Read to %s . Success: %b", ch.getUuid().toString(), result));
                } else if (mCharacteristicsToWrite.size() > 0) {
                    // Else move on to writing our data to peripheral characteristics
                    BluetoothGattCharacteristic ch = mCharacteristicsToWrite.pop();
                    boolean result = gatt.writeCharacteristic(ch);
                    logEvent(String.format("Sending Write to %s . Success: %b", ch.getUuid().toString(), result));
                } else {
                    // No more data to sync with peripheral
                    logEvent("Synced all data with remote peripheral. Safe to disconnect");
                    // TODO: disconnect?
                }
            } else {
                logEvent("Central client got unhandled GATT status onCharacteristicRead " + status);
            }

            super.onCharacteristicRead(gatt, characteristic, status);
        }

        /**
         * Callback indicating the previous write request finished
         * Write more data if available
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            logEvent(String.format("Received Write from %s with status %d", characteristic.getUuid(), status));

            /** Messages Write Response */
            if (characteristic.getUuid().equals(GATT.MESSAGES_WRITE_UUID)) {
                // If we have more messages to send, do it
                logEvent("TODO: Send more messages");
            }
            /** Identity Write Response **/
            else if (characteristic.getUuid().equals(GATT.IDENTITY_WRITE_UUID)) {
                // If we have more identities to write, do it
                logEvent("TODO: Send more identities");
            } else {
                logEvent("Got OnCharacteristicWrite for unknown characteristic " + characteristic.getUuid().toString());
            }

            if (mCharacteristicsToWrite.size() > 0) {
                BluetoothGattCharacteristic ch = mCharacteristicsToWrite.pop();
                // TODO: Write first bit of data for characteristic
                boolean result = gatt.writeCharacteristic(ch);
                logEvent(String.format("Sending Write to %s . Success: %b", ch.getUuid().toString(), result));

            }
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        private void queueReadOp(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            mCharacteristicsToRead.push(characteristic);
            if (mCharacteristicsToWrite.size() == 2 && mCharacteristicsToRead.size() == 2) {
                BluetoothGattCharacteristic ch = mCharacteristicsToRead.pop();
                boolean success = gatt.readCharacteristic(ch);
                logEvent(String.format("Sending Read to %s. Success: %b", ch.getUuid().toString(), success));
            }
        }

        private void queueWriteOp(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            mCharacteristicsToWrite.push(characteristic);
            if (mCharacteristicsToWrite.size() == 2 && mCharacteristicsToRead.size() == 2) {
                BluetoothGattCharacteristic ch = mCharacteristicsToRead.pop();
                boolean success = gatt.readCharacteristic(ch);
                logEvent(String.format("Sending Read to %s. Success: %b", ch.getUuid().toString(), success));
            }
        }
    };

    /*****************************************
     *         Peripheral Callbacks          *
     *  Responds to read and write requests  *
     *****************************************
     *
     * The peripheral device responds to reads,
     * returning GATT_SUCCESS if more data is
     * available for delivery to a subsequent request
     * made by the remote central. When the peripheral
     * is out of data for the target characteristic,
     * {@link android.bluetooth.BluetoothGatt#GATT_READ_NOT_PERMITTED}
     * will be returned.
    */

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

        /**
         * Send responses to the remote central.
         */
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
                    if (responseData != null) {
                        logEvent("Sending Identity. Sent response " + new String(responseData, "UTF-8"));
                        mPeripheral.getGattServer().sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, responseData);
                    } else {
                        logEvent("Error preparing Identity response. Sending failure");
                        mPeripheral.getGattServer().sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
                    }
                }
            }
            catch (UnsupportedEncodingException e) {
                mPeripheral.getGattServer().sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
                e.printStackTrace();
            }
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
        }

        /**
         * Consume responses sent by the remote central
         */
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
        return ChatApp.createBroadcastMessageResponseForString(mContext,
                message.getString(message.getColumnIndex(MessageTable.body)));
    }

    /**
     * Process data representing an incoming message
     */
    private void consumeMessageReadResponse(byte[] message) {
        ChatApp.consumeReceivedBroadcastMessage(mContext, message);
    }

    /**
     * Process data representing a remote identity
     */
    private void consumeIdentityReadResponse(byte[] identity) {
        ChatApp.consumeReceivedIdentity(mContext, identity);
    }

    // </editor-fold desc="Private API">
}
