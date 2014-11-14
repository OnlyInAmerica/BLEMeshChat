package pro.dbro.ble.transport.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;

import pro.dbro.ble.data.model.DataUtil;
import pro.dbro.ble.protocol.IdentityPacket;
import pro.dbro.ble.protocol.MessagePacket;
import pro.dbro.ble.protocol.Protocol;
import pro.dbro.ble.transport.ConnectionGovernor;
import pro.dbro.ble.transport.ConnectionListener;
import pro.dbro.ble.transport.Transport;
import pro.dbro.ble.ui.activities.LogConsumer;

/**
 * Created by davidbrodsky on 10/20/14.
 */
public class BLETransport extends Transport implements ConnectionGovernor, ConnectionListener {
    public static final String TAG = "BLETransport";

    /** How many items to send for each request for this client's items */
    private static final int MESSAGES_PER_RESPONSE = 10;
    private static final int IDENTITIES_PER_RESPONSE = 10;

    private Context mContext;

    private BLECentral mCentral;
    private BLEPeripheral mPeripheral;

    private LogConsumer mLogger;

    /** Outgoing message queues by device key. see {@link #getKeyForDevice(byte[], String)} */
    private HashMap<String, ArrayDeque<MessagePacket>> mMessageOutboxes = new HashMap<>();
    /** Outgoing identity queues by device key */
    private HashMap<String, ArrayDeque<IdentityPacket>> mIdentitiesOutboxes = new HashMap<>();
    /** Remote identities by device address */
    private HashMap<String, IdentityPacket> mAddressesToIdentity = new HashMap<>();

    // <editor-fold desc="Public API">

    public BLETransport(@NonNull Context context, @NonNull IdentityPacket identityPacket, @NonNull Protocol protocol, @NonNull TransportDataProvider dataProvider) {
        super(identityPacket, protocol, dataProvider);
        mContext = context.getApplicationContext();
        init();
    }

    public void setLogConsumer(LogConsumer logger) {
        mLogger = logger;
        mCentral.setLogConsumer(logger);
        mPeripheral.setLogConsumer(logger);
    }

    @Override
    public void makeAvailable() {
        mCentral.start();
        mPeripheral.start();
    }

    @Override
    public void sendMessage(MessagePacket messagePacket) {
        // TODO  If Message is instanceof DirectMessage etc, try to send directly to device
        // Note the message should already be stored and will be automatically delivered on next connection
    }

    @Override
    public void makeUnavailable() {
        mCentral.stop();
        mPeripheral.stop();
    }

    // </editor-fold desc="Public API">

    // <editor-fold desc="Private API">

    private void init() {
        mCentral = new BLECentral(mContext);
        mCentral.setConnectionGovernor(this);
        mCentral.setConnectionListener(this);
        // These Central requests are executed in order
        // Note the local Identity must be first presented to the remote peer
        // so that it can calculate which identities to present back
        mCentral.addDefaultBLECentralRequest(mIdentityWriteRequest);
        mCentral.addDefaultBLECentralRequest(mIdentityReadRequest);
        mCentral.addDefaultBLECentralRequest(mMessageReadRequest);
        mCentral.addDefaultBLECentralRequest(mMessageWriteRequest);

        mPeripheral = new BLEPeripheral(mContext);
        mPeripheral.setConnectionGovernor(this);
        mPeripheral.setConnectionListener(this);
        mPeripheral.addDefaultBLEPeripheralResponse(mMessageReadResponse);
        mPeripheral.addDefaultBLEPeripheralResponse(mIdentityReadResponse);
        mPeripheral.addDefaultBLEPeripheralResponse(mMessageWriteResponse);
        mPeripheral.addDefaultBLEPeripheralResponse(mIdentityWriteResponse);
    }

    /** ConnectionGovernor */
    @Override
    public boolean shouldConnectToAddress(String address) {
        boolean centralConnectedToPeer = mCentral.getConnectedDeviceAddresses().contains(address);
        boolean peripheralConnectedToPeer = mPeripheral.getConnectedDeviceAddresses().contains(address);

        boolean mayConnect =  !centralConnectedToPeer && !peripheralConnectedToPeer;

        if (!mayConnect) {
            Log.i("ConnectionGovernor", String.format("Blocking connection to %s. Central Connections: %d (includes peer: %b) Peripheral Connections: %d (includes peer: %b)",
                    address,
                    mCentral.getConnectedDeviceAddresses().size(),
                    centralConnectedToPeer,
                    mPeripheral.getConnectedDeviceAddresses().size(),
                    peripheralConnectedToPeer));
        }
        return mayConnect;
    }

    /** BLECentralRequests */

    BLECentralRequest mMessageReadRequest = new BLECentralRequest(GATT.MESSAGES_READ, BLECentralRequest.RequestType.READ) {
        @Override
        public boolean handleResponse(BluetoothGatt remotePeripheral, BluetoothGattCharacteristic characteristic, int status) {
            // Consume message from characteristic.getValue()
            // If status == GATT_SUCCESS, return false to re-issue this request
            // else if status == READ_NOT_PERMITTED, return true
            if (characteristic.getValue() == null || characteristic.getValue().length == 0) {
                return isCentralRequestComplete(status); // retry if got status success
            }
            MessagePacket receivedMessagePacket = mProtocol.deserializeMessage(characteristic.getValue());
            // Note this isn't the author of the message, but the courier who delivered it to us
            IdentityPacket courierIdentity = mAddressesToIdentity.get(remotePeripheral.getDevice().getAddress());
            if (mCallback != null) mCallback.receivedMessageFromIdentity(receivedMessagePacket, courierIdentity);
            logEvent("Central read message " + receivedMessagePacket.body);

            return isCentralRequestComplete(status);
        }
    };

    BLECentralRequest mIdentityReadRequest = new BLECentralRequest(GATT.IDENTITY_READ, BLECentralRequest.RequestType.READ) {
        @Override
        public boolean handleResponse(BluetoothGatt remotePeripheral, BluetoothGattCharacteristic characteristic, int status) {
            // Consume Identity from characteristic.getValue()
            // If status == GATT_SUCCESS, return false to re-issue this request
            // else if status == READ_NOT_PERMITTED, return true
            if (characteristic.getValue() == null || characteristic.getValue().length == 0) {
                return isCentralRequestComplete(status); // retry if got status success
            }
            IdentityPacket receivedIdentityPacket;
            try {
                receivedIdentityPacket = mProtocol.deserializeIdentity(characteristic.getValue());
            } catch (IllegalArgumentException e) {
                Log.w(TAG, String.format("Received malformed Identity from %s, ignoring", remotePeripheral.getDevice().getAddress()));
                return true; // Don't try again. TODO Add some retry limit
            }
            handleIdentityBecameAvailable(remotePeripheral.getDevice().getAddress(), receivedIdentityPacket);
            logEvent(String.format("Central read identity %s..", DataUtil.bytesToHex(receivedIdentityPacket.publicKey).substring(0,3)));

            return isCentralRequestComplete(status);
        }
    };

    BLECentralRequest mMessageWriteRequest = new BLECentralRequest(GATT.MESSAGES_WRITE, BLECentralRequest.RequestType.WRITE) {
        @Override
        public boolean handleResponse(BluetoothGatt remotePeripheral, BluetoothGattCharacteristic characteristic, int status) {
            MessagePacket justSent = getNextMessageForDeviceAddress(remotePeripheral.getDevice().getAddress(), true);
            Log.i(TAG, "Handling response after central sent message with body " + (justSent == null ? "null" : justSent.body));
            if (justSent == null) {
                // No data was available for this request. Mark request complete
                logEvent("Central had no message for peer");
                return true;
            } else {
                if (status == BluetoothGatt.GATT_SUCCESS && mCallback != null) {
                    mCallback.sentMessage(getNextMessageForDeviceAddress(remotePeripheral.getDevice().getAddress(), false), mAddressesToIdentity.get(remotePeripheral.getDevice().getAddress()));
                }
                logEvent(String.format("Central wrote message %s..", justSent.body));

                // If we have more messages to send, indicate request should be repeated
                return (getNextMessageForDeviceAddress(remotePeripheral.getDevice().getAddress(), false) == null);
            }
        }

        @Override
        public byte[] getDataToWrite(BluetoothGatt remotePeripheral) {
            //return byte[] next_message
            MessagePacket forRecipient = getNextMessageForDeviceAddress(remotePeripheral.getDevice().getAddress(), true);
            return (forRecipient == null ) ? null : forRecipient.rawPacket;
        }
    };

    BLECentralRequest mIdentityWriteRequest = new BLECentralRequest(GATT.IDENTITY_WRITE, BLECentralRequest.RequestType.WRITE) {
        @Override
        public boolean handleResponse(BluetoothGatt remotePeripheral, BluetoothGattCharacteristic characteristic, int status) {
            IdentityPacket justSent = getNextIdentityForDeviceAddress(remotePeripheral.getDevice().getAddress(), false);
            if (justSent == null) {
                //No data was available for this request. Mark request complete
                logEvent("Central had no identity for peer");

                return true;
            } else {
                if (status == BluetoothGatt.GATT_SUCCESS && mCallback != null) {
                    mCallback.sentIdentity(getNextIdentityForDeviceAddress(remotePeripheral.getDevice().getAddress(), false),
                            mAddressesToIdentity.get(remotePeripheral.getDevice().getAddress()));
                }
                logEvent(String.format("Central wrote identity %s..", DataUtil.bytesToHex(justSent.publicKey).substring(0,3)));
                // If we have more messages to send, indicate request should be repeated
                return (getNextIdentityForDeviceAddress(remotePeripheral.getDevice().getAddress(), false) == null);
            }
        }

        @Override
        public byte[] getDataToWrite(BluetoothGatt remotePeripheral) {
            //return byte[] next_identity
            IdentityPacket forRecipient = getNextIdentityForDeviceAddress(remotePeripheral.getDevice().getAddress(), true);
            return (forRecipient == null) ? null : forRecipient.rawPacket;
        }
    };

    /** BLEPeripheral Responses */

    BLEPeripheralResponse mMessageReadResponse = new BLEPeripheralResponse(GATT.MESSAGES_READ, BLEPeripheralResponse.RequestType.READ) {
        @Override
        public byte[] respondToRequest(BluetoothGattServer localPeripheral, BluetoothDevice remoteCentral, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, byte[] value) {
            try {
                // Get messages to send and send first
                MessagePacket forRecipient = getNextMessageForDeviceAddress(remoteCentral.getAddress(), true);
                if (forRecipient != null) {
                    boolean haveAnotherMessage = getNextMessageForDeviceAddress(remoteCentral.getAddress(), false) != null;
                    byte[] payload = forRecipient.rawPacket;
                    int responseGattStatus = haveAnotherMessage ? BluetoothGatt.GATT_SUCCESS : BluetoothGatt.GATT_READ_NOT_PERMITTED;
                    boolean responseSent = localPeripheral.sendResponse(remoteCentral, requestId, responseGattStatus, 0, payload);
                    //Log.i(TAG, String.format("Responded to message read request with outgoing status %b. response sent: %b data (%d bytes): %s", responseGattStatus, responseSent, (payload == null || payload.length == 0) ? 0 : payload.length, (payload == null || payload.length == 0) ? "null" : DataUtil.bytesToHex(payload)));
                    if (responseSent) {
                        if (mCallback != null)
                            mCallback.sentMessage(forRecipient, mAddressesToIdentity.get(remoteCentral.getAddress()));
                        logEvent(String.format("Peripheral sent message for peer %s", forRecipient.body));
                        return payload;
                    }
                } else {
                    boolean success = localPeripheral.sendResponse(remoteCentral, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, null);
                    logEvent("Peripheral had no message for peer");
                    //Log.i(TAG, "Had no messages for peer. Sent READ_NOT_PERMITTED with success " + success);
                }
            } catch(Exception e) {
                logEvent("Peripheral failed to send message");

                e.printStackTrace();
            }
            return null;
        }
    };

    BLEPeripheralResponse mIdentityReadResponse = new BLEPeripheralResponse(GATT.IDENTITY_READ, BLEPeripheralResponse.RequestType.READ) {
        @Override
        public byte[] respondToRequest(BluetoothGattServer localPeripheral, BluetoothDevice remoteCentral, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, byte[] value) {
            // Get identities to send and send first
            IdentityPacket forRecipient = getNextIdentityForDeviceAddress(remoteCentral.getAddress(), true);
            if (forRecipient != null) {
                // If we don't have a public key for this address, we'll only send one identity (the user's)
                boolean haveAnotherIdentity = mAddressesToIdentity.containsKey(remoteCentral.getAddress()) && getNextIdentityForDeviceAddress(remoteCentral.getAddress(), false) != null;
                byte[] payload = forRecipient.rawPacket;
                int responseGattStatus = haveAnotherIdentity ? BluetoothGatt.GATT_SUCCESS : BluetoothGatt.GATT_READ_NOT_PERMITTED;
                boolean responseSent = localPeripheral.sendResponse(remoteCentral, requestId, responseGattStatus, 0, payload);
                //Log.i(TAG, String.format("Responded to identity read request with outgoing status %b. response sent: %b data: %s", responseGattStatus, responseSent, (payload == null || payload.length == 0) ? "null" : DataUtil.bytesToHex(payload)));
                if (responseSent && mCallback != null) mCallback.sentIdentity(forRecipient, mAddressesToIdentity.get(remoteCentral.getAddress()));
                if (responseSent) {
                    if (mCallback != null)
                        mCallback.sentIdentity(forRecipient, mAddressesToIdentity.get(remoteCentral.getAddress()));
                    logEvent(String.format("Peripheral sent identity %s...", DataUtil.bytesToHex(forRecipient.publicKey).substring(0,3)));

                    return payload;
                }
            } else {
                boolean success = localPeripheral.sendResponse(remoteCentral, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, null);
                logEvent("Peripheral had no identities for peer");
            }
            return null;
        }
    };

    BLEPeripheralResponse mMessageWriteResponse = new BLEPeripheralResponse(GATT.MESSAGES_WRITE, BLEPeripheralResponse.RequestType.WRITE) {
        @Override
        public byte[] respondToRequest(BluetoothGattServer localPeripheral, BluetoothDevice remoteCentral, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, byte[] value) {
            // Consume message and send GATT_SUCCESS If valid and response needed
            testValueVsCharacteristicValue(value, characteristic);

            MessagePacket receivedMessagePacket = mProtocol.deserializeMessage(value);
            IdentityPacket courierIdentity = mAddressesToIdentity.get(remoteCentral.getAddress());
            if (mCallback != null) mCallback.receivedMessageFromIdentity(receivedMessagePacket, courierIdentity);
            if (responseNeeded) {
                // TODO: Response code based on message validation?
                localPeripheral.sendResponse(remoteCentral, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            }
            logEvent(String.format("Peripheral received message %s", receivedMessagePacket.body));
            return null;
        }
    };

    BLEPeripheralResponse mIdentityWriteResponse = new BLEPeripheralResponse(GATT.IDENTITY_WRITE, BLEPeripheralResponse.RequestType.WRITE) {
        @Override
        public byte[] respondToRequest(BluetoothGattServer localPeripheral, BluetoothDevice remoteCentral, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, byte[] value) {
            // Consume Identity and send GATT_SUCCESS if valid and response needed
            testValueVsCharacteristicValue(value, characteristic);

            if (value == null || value.length == 0) {
                Log.i(TAG, "got empty write data");
            } else {
                Log.i(TAG, "got non-empty write data! length: " + value.length);
                IdentityPacket receivedIdentityPacket = mProtocol.deserializeIdentity(value);

                handleIdentityBecameAvailable(remoteCentral.getAddress(), receivedIdentityPacket);
                logEvent(String.format("Peripheral received identity %s...", DataUtil.bytesToHex(receivedIdentityPacket.publicKey).substring(0,3)));
            }

            if (responseNeeded) {
                // TODO: Response code based on message validation?
                localPeripheral.sendResponse(remoteCentral, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            }
            return null;
        }
    };

    /** BLECentralConnectionListener */

    @Override
    public void connectedTo(String deviceAddress) {
        // do nothing. We shouldn't report peer available until we have an Identity for it
    }

    @Override
    public void disconnectedFrom(String deviceAddress) {
        IdentityPacket disconnectedIdentityPacket = mAddressesToIdentity.remove(deviceAddress);
        if (disconnectedIdentityPacket != null && mCallback != null) {
            mCallback.identityBecameUnavailable(disconnectedIdentityPacket);
        }
    }

    /** Utility */

    @Nullable
    private IdentityPacket getNextIdentityForDeviceAddress(String address, boolean removeFromQueue) {
        byte[] publicKey = getPublicKeyForDeviceAddress(address);
        return getNextIdentityForDevice(publicKey, address, removeFromQueue);
    }

    @Nullable
    private MessagePacket getNextMessageForDeviceAddress(String address, boolean removeFromQueue) {
        byte[] publicKey = getPublicKeyForDeviceAddress(address);
        return getNextMessageForDevice(publicKey, address, removeFromQueue);
    }

    @Nullable
    private MessagePacket getNextMessageForDevice(@Nullable byte[] publicKey, @NonNull String address, boolean removeFromQueue) {
        String deviceKey = getKeyForDevice(publicKey, address);

        if (publicKey == null) {
            // This is a special case because we have no public key so we can't record delivery of any items
            // Therefore we can't rely on mDataProvider.getMessagesForIdentity to not return items that were already sent
            // So we'll ensure that we only perform that request once per device address
            if (!mMessageOutboxes.containsKey(deviceKey)) {
                ArrayDeque<MessagePacket> messagesForRecipient = mDataProvider.getMessagesForIdentity(null, IDENTITIES_PER_RESPONSE);
                mMessageOutboxes.put(deviceKey, messagesForRecipient);
            } else {
                // We've already sent our own Identity for this identity-less peer
                Log.i(TAG, "Returning null for nextMessage to " + address);
                return null;
            }
        }
        else if (!mMessageOutboxes.containsKey(deviceKey) || mMessageOutboxes.get(deviceKey).size() == 0) {
            ArrayDeque<MessagePacket> messagesForRecipient = mDataProvider.getMessagesForIdentity(publicKey, MESSAGES_PER_RESPONSE);
            mMessageOutboxes.put(deviceKey, messagesForRecipient);
            Log.i(TAG, String.format("Got %d messages for pk %s", messagesForRecipient.size(), DataUtil.bytesToHex(publicKey)));
        }

        if (mMessageOutboxes.get(deviceKey).size() == 0) return null;
        return removeFromQueue ? mMessageOutboxes.get(deviceKey).poll() : mMessageOutboxes.get(deviceKey).peek();
    }

    @Nullable
    private IdentityPacket getNextIdentityForDevice(@Nullable byte[] publicKey, String address, boolean removeFromQueue) {
        String deviceKey = getKeyForDevice(publicKey, address);
        if (publicKey == null) {
            Log.i(TAG, "Getting identity response for no-identity peer at " + address);
            // This is a special case because we have no public key so we can't record delivery of any items
            // Therefore we can't rely on mDataProvider.getIdentitiesForIdentity to not return items that were already sent
            // So we'll ensure that we only perform that request once per device address
            if (!mIdentitiesOutboxes.containsKey(deviceKey)) {
                ArrayDeque<IdentityPacket> identitiesForRecipient = mDataProvider.getIdentitiesForIdentity(null, IDENTITIES_PER_RESPONSE);
                mIdentitiesOutboxes.put(deviceKey, identitiesForRecipient);
            } else {
                // We've already sent our own Identity for this identity-less peer
                Log.i(TAG, "Returning null for nextIdentity to " + address);
                return null;
            }
        }
        else if (!mIdentitiesOutboxes.containsKey(deviceKey) || mIdentitiesOutboxes.get(deviceKey).size() == 0) {
            // We have a public key for this peer. Proceed as normal
            ArrayDeque<IdentityPacket> identitiesForRecipient = mDataProvider.getIdentitiesForIdentity(publicKey, IDENTITIES_PER_RESPONSE);
            mIdentitiesOutboxes.put(deviceKey, identitiesForRecipient);
        }
        if (mIdentitiesOutboxes.get(deviceKey).size() == 0) return null;
        return removeFromQueue ? mIdentitiesOutboxes.get(deviceKey).poll() : mIdentitiesOutboxes.get(deviceKey).peek();
    }

    private byte[] getPublicKeyForDeviceAddress(String address) {
        byte[] publicKey = null;
        if (mAddressesToIdentity.containsKey(address)) publicKey = mAddressesToIdentity.get(address).publicKey;

        if (publicKey == null) {
            // No public key on file, perform naive message send for now
            Log.w(TAG, String.format("Don't have identity on file for device %s.", address));
        }
        return publicKey;
    }

    private boolean isCentralRequestComplete(int gattStatus) {
        switch(gattStatus) {
            case BluetoothGatt.GATT_READ_NOT_PERMITTED:
            case BluetoothGatt.GATT_FAILURE:
                return true;    // request complete
            case BluetoothGatt.GATT_SUCCESS:
                return false;   // peripheral reports more data available
            default:
                Log.w(TAG, String.format("Got unexpected GATT status %d", gattStatus));
                return true;
        }
    }

    private void testValueVsCharacteristicValue(byte[] value, BluetoothGattCharacteristic characteristic) {
        if (value != null && characteristic.getValue() != null) {
            Log.i(TAG, "are value and characteristic.getValue equal: " + Arrays.equals(value, characteristic.getValue()));
        } else if (value != null) {
            Log.i(TAG, "characteristic.getValue null, but value not null");
        } else if(characteristic.getValue() != null) {
            Log.i(TAG, "value is null but characterisitc.getValue not null");
        }
    }

    /**
     * Return a key used for {@link #mIdentitiesOutboxes} and {@link #mMessageOutboxes} using
     * a string representation of the public key or device address if public key is null
     */
    private String getKeyForDevice(@Nullable byte[] publicKey, @Nullable String deviceAddress) {
        if (publicKey != null) return DataUtil.bytesToHex(publicKey);

        return deviceAddress;
    }

    private void logEvent(String event) {
        if (mLogger != null) {
            mLogger.onLogEvent(event);
        } else {
            Log.i(TAG, event);
        }
    }

    private void handleIdentityBecameAvailable(String fromAddress, IdentityPacket receivedIdentityPacket) {
        if (!mAddressesToIdentity.containsKey(fromAddress)) {
            mAddressesToIdentity.put(fromAddress, receivedIdentityPacket);

            if (mCallback != null)  mCallback.identityBecameAvailable(receivedIdentityPacket);
        }
    }

    // </editor-fold desc="Private API">
}
