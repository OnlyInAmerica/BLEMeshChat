package pro.dbro.ble.transport.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import pro.dbro.ble.data.model.DataUtil;
import pro.dbro.ble.protocol.IdentityPacket;
import pro.dbro.ble.protocol.MessagePacket;
import pro.dbro.ble.protocol.Protocol;
import pro.dbro.ble.transport.Transport;

/**
 * Created by davidbrodsky on 10/20/14.
 */
public class BLETransport extends Transport implements BLECentral.BLECentralConnectionGovernor,
                                                       BLEPeripheral.BLEPeripheralConnectionGovernor,
                                                       BLECentral.BLECentralConnectionListener {
    public static final String TAG = "BLETransport";

    /** How many items to send for each request for this client's items */
    private static final int MESSAGES_PER_RESPONSE = 10;
    private static final int IDENTITIES_PER_RESPONSE = 10;

    private Context mContext;

    private BLECentral mCentral;
    private BLEPeripheral mPeripheral;

    /** Identity public key -> outbox
     * use public key instead of Identity to avoid overriding Identity#equals, #hashcode 'for now'
    */
    private HashMap<byte[], ArrayDeque<MessagePacket>> mMessageOutboxes = new HashMap<>();
    private HashMap<byte[], ArrayDeque<IdentityPacket>> mIdentitiesOutboxes = new HashMap<>();
    private HashMap<String, IdentityPacket> mAddressesToIdentity = new HashMap<>();
    private ConcurrentHashMap<IdentityPacket, BluetoothGatt> mRemotePeripherals = new ConcurrentHashMap<>();
    private ConcurrentHashMap<IdentityPacket, BluetoothDevice> mRemoteCentrals = new ConcurrentHashMap<>();

    // <editor-fold desc="Public API">

    public BLETransport(@NonNull Context context, @NonNull IdentityPacket identityPacket, @NonNull Protocol protocol, @NonNull TransportDataProvider dataProvider) {
        super(identityPacket, protocol, dataProvider);
        mContext = context.getApplicationContext();
        init();
    }

    @Override
    public void makeAvailable() {
//        mCentral.start();
        mPeripheral.start();
    }

    @Override
    public void sendMessage(MessagePacket messagePacket) {
        // TODO  If Message is instanceof DirectMessage etc, try to send directly to device
        // Note the message should already be stored and will be automatically delivered on next connection
    }

    @Override
    public void makeUnavailable() {
//        mCentral.stop();
        mPeripheral.stop();
    }

    // </editor-fold desc="Public API">

    // <editor-fold desc="Private API">

    private void init() {
        mCentral = new BLECentral(mContext);
        mCentral.setConnectionGovernor(this);
        mCentral.setConnectionListener(this);
        // These Central requests are executed in order
        mCentral.addDefaultBLECentralRequest(mIdentityReadRequest);
        mCentral.addDefaultBLECentralRequest(mIdentityWriteRequest);
        mCentral.addDefaultBLECentralRequest(mMessageReadRequest);
        mCentral.addDefaultBLECentralRequest(mMessageWriteRequest);

        mPeripheral = new BLEPeripheral(mContext);
        mPeripheral.setConnectionGovernor(this);
        mPeripheral.addDefaultBLEPeripheralResponse(mMessageReadResponse);
        mPeripheral.addDefaultBLEPeripheralResponse(mIdentityReadResponse);
        mPeripheral.addDefaultBLEPeripheralResponse(mMessageWriteResponse);
        mPeripheral.addDefaultBLEPeripheralResponse(mIdentityWriteResponse);
    }

    /** BLECentralConnectionGovernor */
    @Override
    public boolean shouldConnectToPeripheral(ScanResult potentialPeer) {
        // If the peripheral is connected to this device, don't duplicate the connection as central
        boolean mayConnect =  !mPeripheral.getConnectedDeviceAddresses().contains(potentialPeer.getDevice().getAddress());
        if (!mayConnect) {
            Log.i("CentralGovernor", String.format("peripheral connected to %d devices, including potential peer", mPeripheral.getConnectedDeviceAddresses().size()));
        }
        return mayConnect;
    }

    /** BLEPeripheralConnectionGovernor */
    @Override
    public boolean shouldConnectToCentral(BluetoothDevice potentialPeer) {
        boolean mayConnect = !mCentral.getConnectedDeviceAddresses().contains(potentialPeer.getAddress());
        if (!mayConnect) {
            Log.i("PeripheralGovernor", String.format("central connected to %d devices, including potential peer", mCentral.getConnectedDeviceAddresses().size()));
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
            MessagePacket receivedMessagePacket = mProtocol.deserializeMessage(characteristic.getValue(), null);
            if (mCallback != null) mCallback.receivedMessage(receivedMessagePacket);

            return isCentralRequestComplete(status);
        }
    };

    BLECentralRequest mIdentityReadRequest = new BLECentralRequest(GATT.IDENTITY_READ, BLECentralRequest.RequestType.READ) {
        @Override
        public boolean handleResponse(BluetoothGatt remotePeripheral, BluetoothGattCharacteristic characteristic, int status) {
            // Consume Identity from characteristic.getValue()
            // If status == GATT_SUCCESS, return false to re-issue this request
            // else if status == READ_NOT_PERMITTED, return true
            IdentityPacket receivedIdentityPacket;
            try {
                receivedIdentityPacket = mProtocol.deserializeIdentity(characteristic.getValue());
            } catch (IllegalArgumentException e) {
                Log.w(TAG, String.format("Received malformed Identity from %s, ignoring", remotePeripheral.getDevice().getAddress()));
                return true; // Don't try again. TODO Add some retry limit
            }
            mAddressesToIdentity.put(remotePeripheral.getDevice().getAddress(), receivedIdentityPacket);
            if (mCallback != null) {
                mCallback.receivedIdentity(receivedIdentityPacket);
                mCallback.becameAvailable(receivedIdentityPacket);
            }
            return isCentralRequestComplete(status);
        }
    };

    BLECentralRequest mMessageWriteRequest = new BLECentralRequest(GATT.MESSAGES_WRITE, BLECentralRequest.RequestType.WRITE) {
        @Override
        public boolean handleResponse(BluetoothGatt remotePeripheral, BluetoothGattCharacteristic characteristic, int status) {
            MessagePacket justSent = getNextMessageForDeviceAddress(remotePeripheral.getDevice().getAddress(), true);
            if (justSent == null) {
                // No data was available for this request. Mark request complete
                return true;
            } else {
                if (status == BluetoothGatt.GATT_SUCCESS && mCallback != null) {
                    mCallback.sentMessage(getNextMessageForDeviceAddress(remotePeripheral.getDevice().getAddress(), false), mAddressesToIdentity.get(remotePeripheral.getDevice().getAddress()));
                }
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
                return true;
            } else {
                if (status == BluetoothGatt.GATT_SUCCESS && mCallback != null) {
                    mCallback.sentIdentity(getNextIdentityForDeviceAddress(remotePeripheral.getDevice().getAddress(), false),
                            mAddressesToIdentity.get(remotePeripheral.getDevice().getAddress()));
                }
                // If we have more messages to send, indicate request should be repeated
                return (getNextIdentityForDeviceAddress(remotePeripheral.getDevice().getAddress(), false) == null);
            }
        }

        @Override
        public byte[] getDataToWrite(BluetoothGatt remotePeripheral) {
            //return byte[] next_identity
            IdentityPacket forRecipient = getNextIdentityForDeviceAddress(remotePeripheral.getDevice().getAddress(), true);
            return forRecipient.rawPacket;
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
                    Log.i(TAG, String.format("Responded to message read request with outgoing status %b. response sent: %b data (%d bytes): %s", responseGattStatus, responseSent, (payload == null || payload.length == 0) ? 0 : payload.length, (payload == null || payload.length == 0) ? "null" : DataUtil.bytesToHex(payload)));
                    if (responseSent) {
                        if (mCallback != null)
                            mCallback.sentMessage(forRecipient, mAddressesToIdentity.get(remoteCentral.getAddress()));
                        return payload;
                    }
                } else {
                    boolean success = localPeripheral.sendResponse(remoteCentral, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, null);
                    Log.i(TAG, "Had no messages for peer. Sent READ_NOT_PERMITTED with success " + success);
                }
            } catch(Exception e) {
                Log.i(TAG, "Failed to respond to message read request");
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
                Log.i(TAG, String.format("Responded to identity read request with outgoing status %b. response sent: %b data: %s", responseGattStatus, responseSent, (payload == null || payload.length == 0) ? "null" : DataUtil.bytesToHex(payload)));
                if (responseSent && mCallback != null) mCallback.sentIdentity(forRecipient, mAddressesToIdentity.get(remoteCentral.getAddress()));
                if (responseSent) {
                    if (mCallback != null)
                        mCallback.sentIdentity(forRecipient, mAddressesToIdentity.get(remoteCentral.getAddress()));
                    return payload;
                }
            } else {
                boolean success = localPeripheral.sendResponse(remoteCentral, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, null);
                Log.i(TAG, "Had no identities for peer. Sent READ_NOT_PERMITTED with success " + success);
            }
            return null;
        }
    };

    BLEPeripheralResponse mMessageWriteResponse = new BLEPeripheralResponse(GATT.MESSAGES_WRITE, BLEPeripheralResponse.RequestType.WRITE) {
        @Override
        public byte[] respondToRequest(BluetoothGattServer localPeripheral, BluetoothDevice remoteCentral, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, byte[] value) {
            // Consume message and send GATT_SUCCESS If valid and response needed
            testValueVsCharacteristicValue(value, characteristic);

            MessagePacket receivedMessagePacket = mProtocol.deserializeMessage(value, mAddressesToIdentity.get(remoteCentral.getAddress()));
            if (mCallback != null) mCallback.receivedMessage(receivedMessagePacket);
            if (responseNeeded) {
                // TODO: Response code based on message validation?
                localPeripheral.sendResponse(remoteCentral, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            }
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
                if (mCallback != null) {
                    Log.i(TAG, "delivering identity to callback: ");
                    mCallback.receivedIdentity(receivedIdentityPacket);
                }
                mAddressesToIdentity.put(remoteCentral.getAddress(), receivedIdentityPacket);
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
            mCallback.becameUnavailable(disconnectedIdentityPacket);
        }
    }

    /** Utility */

    @Nullable
    private IdentityPacket getNextIdentityForDeviceAddress(String address, boolean removeFromQueue) {
        byte[] publicKey = getPublicKeyForDeviceAddress(address);
        return getNextIdentityForPublicKey(publicKey, removeFromQueue);
    }

    @Nullable
    private MessagePacket getNextMessageForDeviceAddress(String address, boolean removeFromQueue) {
        // Do we have an Identity on file for this address?
        byte[] publicKey = getPublicKeyForDeviceAddress(address);
        return getNextMessageForPublicKey(publicKey, removeFromQueue);
    }

    @Nullable
    private MessagePacket getNextMessageForPublicKey(@Nullable byte[] publicKey, boolean removeFromQueue) {
        if (!mMessageOutboxes.containsKey(publicKey) || mMessageOutboxes.get(publicKey).size() == 0) {
            ArrayDeque<MessagePacket> messagesForRecipient = mDataProvider.getMessagesForIdentity(publicKey, MESSAGES_PER_RESPONSE);
            mMessageOutboxes.put(publicKey, messagesForRecipient);
        }

        if (mMessageOutboxes.get(publicKey).size() == 0) return null;
        return removeFromQueue ? mMessageOutboxes.get(publicKey).pop() : mMessageOutboxes.get(publicKey).peek();
    }

    @Nullable
    private IdentityPacket getNextIdentityForPublicKey(@Nullable byte[] publicKey, boolean removeFromQueue) {
        if (!mIdentitiesOutboxes.containsKey(publicKey) || mIdentitiesOutboxes.get(publicKey).size() == 0) {
            ArrayDeque<IdentityPacket> identitiesForRecipient = mDataProvider.getIdentitiesForIdentity(publicKey, IDENTITIES_PER_RESPONSE);
            mIdentitiesOutboxes.put(publicKey, identitiesForRecipient);
        }
        if (mIdentitiesOutboxes.get(publicKey).size() == 0) return null;
        return removeFromQueue ? mIdentitiesOutboxes.get(publicKey).pop() : mIdentitiesOutboxes.get(publicKey).peek();
    }

    private byte[] getPublicKeyForDeviceAddress(String address) {
        byte[] publicKey = null;
        if (mAddressesToIdentity.containsKey(address)) publicKey = mAddressesToIdentity.get(address).publicKey;

        if (publicKey == null) {
            // No public key on file, perform naive message send for now
            Log.w(TAG, String.format("Don't have identity on file for device %s. Naively sending messages", address));
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

    // </editor-fold desc="Private API">
}
