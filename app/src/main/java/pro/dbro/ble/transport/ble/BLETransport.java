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
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import pro.dbro.ble.protocol.Identity;
import pro.dbro.ble.protocol.Message;
import pro.dbro.ble.protocol.Protocol;
import pro.dbro.ble.transport.Transport;

/**
 * Created by davidbrodsky on 10/20/14.
 */
public class BLETransport extends Transport implements BLECentral.BLECentralConnectionGovernor, BLEPeripheral.BLEPeripheralConnectionGovernor, BLECentral.BLECentralConnectionListener {
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
    private HashMap<byte[], ArrayDeque<Message>> mMessageOutboxes = new HashMap<>();
    private HashMap<byte[], ArrayDeque<Identity>> mIdentitiesOutboxes = new HashMap<>();
    private HashMap<String, Identity> mAddressesToIdentity = new HashMap<>();
    private ConcurrentHashMap<Identity, BluetoothGatt> mRemotePeripherals = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Identity, BluetoothDevice> mRemoteCentrals = new ConcurrentHashMap<>();

    // <editor-fold desc="Public API">

    public BLETransport(@NonNull Context context, @NonNull Identity identity, @NonNull Protocol protocol, @NonNull TransportDataProvider dataProvider) {
        super(identity, protocol, dataProvider);
        mContext = context.getApplicationContext();
        init();
    }

    @Override
    public void makeAvailable() {
        mCentral.start();
        mPeripheral.start();
    }

    @Override
    public void sendMessage(Message message) {
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
        return !mPeripheral.getConnectedDeviceAddresses().contains(potentialPeer.getDevice().getAddress());
    }

    /** BLEPeripheralConnectionGovernor */
    @Override
    public boolean shouldConnectToCentral(BluetoothDevice potentialPeer) {
        return !mCentral.getConnectedDeviceAddresses().contains(potentialPeer.getAddress());
    }

    /** BLECentralRequests */

    BLECentralRequest mMessageReadRequest = new BLECentralRequest(GATT.MESSAGES_READ, BLECentralRequest.RequestType.READ) {
        @Override
        public boolean handleResponse(BluetoothGatt remotePeripheral, BluetoothGattCharacteristic characteristic, int status) {
            // Consume message from characteristic.getValue()
            // If status == GATT_SUCCESS, return false to re-issue this request
            // else if status == READ_NOT_PERMITTED, return true
            Message receivedMessage = mProtocol.deserializeMessage(characteristic.getValue());
            if (mCallback != null) mCallback.receivedMessage(receivedMessage);

            return isCentralRequestComplete(status);
        }
    };

    BLECentralRequest mIdentityReadRequest = new BLECentralRequest(GATT.IDENTITY_READ, BLECentralRequest.RequestType.READ) {
        @Override
        public boolean handleResponse(BluetoothGatt remotePeripheral, BluetoothGattCharacteristic characteristic, int status) {
            // Consume Identity from characteristic.getValue()
            // If status == GATT_SUCCESS, return false to re-issue this request
            // else if status == READ_NOT_PERMITTED, return true
            Identity receivedIdentity = mProtocol.deserializeIdentity(characteristic.getValue());
            mAddressesToIdentity.put(remotePeripheral.getDevice().getAddress(), receivedIdentity);
            if (mCallback != null) {
                mCallback.receivedIdentity(receivedIdentity);
                mCallback.becameAvailable(receivedIdentity);
            }
            return isCentralRequestComplete(status);
        }
    };

    BLECentralRequest mMessageWriteRequest = new BLECentralRequest(GATT.MESSAGES_WRITE, BLECentralRequest.RequestType.WRITE) {
        @Override
        public boolean handleResponse(BluetoothGatt remotePeripheral, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && mCallback != null) {
                mCallback.sentMessage(getNextMessageForDeviceAddress(remotePeripheral.getDevice().getAddress(), false));
            }
            // If we have more messages to send, indicate request should be repeated
            return (getNextMessageForDeviceAddress(remotePeripheral.getDevice().getAddress(), false) == null);
        }

        @Override
        public byte[] getDataToWrite(BluetoothGatt remotePeripheral) {
            //return byte[] next_message
            Message forRecipient = getNextMessageForDeviceAddress(remotePeripheral.getDevice().getAddress(), true);
            return forRecipient.rawPacket;
        }
    };

    BLECentralRequest mIdentityWriteRequest = new BLECentralRequest(GATT.IDENTITY_WRITE, BLECentralRequest.RequestType.WRITE) {
        @Override
        public boolean handleResponse(BluetoothGatt remotePeripheral, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && mCallback != null) {
                mCallback.sentIdentity(getNextIdentityForDeviceAddress(remotePeripheral.getDevice().getAddress(), false));
            }
            // If we have more identities to send, issue write request with next identity
            return (getNextIdentityForDeviceAddress(remotePeripheral.getDevice().getAddress(), false) == null);
        }

        @Override
        public byte[] getDataToWrite(BluetoothGatt remotePeripheral) {
            //return byte[] next_identity
            Identity forRecipient = getNextIdentityForDeviceAddress(remotePeripheral.getDevice().getAddress(), true);
            return forRecipient.rawPacket;
        }
    };

    /** BLEPeripheral Responses */

    BLEPeripheralResponse mMessageReadResponse = new BLEPeripheralResponse(GATT.MESSAGES_READ, BLEPeripheralResponse.RequestType.READ) {
        @Override
        public void respondToRequest(BluetoothGattServer localPeripheral, BluetoothDevice remoteCentral, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            // Get messages to send and send first
            Message forRecipient = getNextMessageForDeviceAddress(remoteCentral.getAddress(), true);
            boolean haveAnotherMessage = getNextMessageForDeviceAddress(remoteCentral.getAddress(), false) != null;
            byte[] payload = forRecipient.rawPacket;
            int gattStatus = haveAnotherMessage ? BluetoothGatt.GATT_SUCCESS : BluetoothGatt.GATT_READ_NOT_PERMITTED;
            boolean success = localPeripheral.sendResponse(remoteCentral, requestId, gattStatus, 0, payload);
            if (success && mCallback != null) mCallback.sentMessage(forRecipient);

        }
    };

    BLEPeripheralResponse mIdentityReadResponse = new BLEPeripheralResponse(GATT.IDENTITY_READ, BLEPeripheralResponse.RequestType.READ) {
        @Override
        public void respondToRequest(BluetoothGattServer localPeripheral, BluetoothDevice remoteCentral, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            // Get identities to send and send first
            Identity forRecipient = getNextIdentityForDeviceAddress(remoteCentral.getAddress(), true);
            boolean haveAnotherIdentity = getNextIdentityForDeviceAddress(remoteCentral.getAddress(), false) != null;
            byte[] payload = forRecipient.rawPacket;
            int gattStatus = haveAnotherIdentity ? BluetoothGatt.GATT_SUCCESS : BluetoothGatt.GATT_READ_NOT_PERMITTED;
            boolean success = localPeripheral.sendResponse(remoteCentral, requestId, gattStatus, 0, payload);
            if (success && mCallback != null) mCallback.sentIdentity(forRecipient);
        }
    };

    BLEPeripheralResponse mMessageWriteResponse = new BLEPeripheralResponse(GATT.MESSAGES_WRITE, BLEPeripheralResponse.RequestType.WRITE) {
        @Override
        public void respondToRequest(BluetoothGattServer localPeripheral, BluetoothDevice remoteCentral, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            // Consume message and send GATT_SUCCESS If valid and response needed
            Message receivedMessage = mProtocol.deserializeMessage(characteristic.getValue());
            if (mCallback != null) mCallback.receivedMessage(receivedMessage);
            if (responseNeeded) {
                // TODO: Response code based on message validation?
                localPeripheral.sendResponse(remoteCentral, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            }
        }
    };

    BLEPeripheralResponse mIdentityWriteResponse = new BLEPeripheralResponse(GATT.IDENTITY_WRITE, BLEPeripheralResponse.RequestType.WRITE) {
        @Override
        public void respondToRequest(BluetoothGattServer localPeripheral, BluetoothDevice remoteCentral, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            // Consume Identity and send GATT_SUCCESS if valid and response needed
            Identity receivedIdentity = mProtocol.deserializeIdentity(characteristic.getValue());
            if (mCallback != null) mCallback.receivedIdentity(receivedIdentity);
            mAddressesToIdentity.put(remoteCentral.getAddress(), receivedIdentity);
            if (responseNeeded) {
                // TODO: Response code based on message validation?
                localPeripheral.sendResponse(remoteCentral, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            }
        }
    };

    /** BLECentralConnectionListener */

    @Override
    public void connectedTo(String deviceAddress) {
        // do nothing. We shouldn't report peer available until we have an Identity for it
    }

    @Override
    public void disconnectedFrom(String deviceAddress) {
        Identity disconnectedIdentity = mAddressesToIdentity.remove(deviceAddress);
        if (mCallback != null) {
            mCallback.becameUnavailable(disconnectedIdentity);
        }
    }

    /** Utility */

    @Nullable
    private Identity getNextIdentityForDeviceAddress(String address, boolean removeFromQueue) {
        byte[] publicKey = getPublicKeyForDeviceAddress(address);
        return getNextIdentityForPublicKey(publicKey, removeFromQueue);
    }

    @Nullable
    private Message getNextMessageForDeviceAddress(String address, boolean removeFromQueue) {
        // Do we have an Identity on file for this address?
        byte[] publicKey = getPublicKeyForDeviceAddress(address);
        return getNextMessageForPublicKey(publicKey, removeFromQueue);
    }

    @Nullable
    private Message getNextMessageForPublicKey(@Nullable byte[] publicKey, boolean removeFromQueue) {
        if (!mMessageOutboxes.containsKey(publicKey) || mMessageOutboxes.get(publicKey).size() == 0) {
            ArrayDeque<Message> messagesForRecipient = mDataProvider.getMessagesForIdentity(publicKey, MESSAGES_PER_RESPONSE);
            mMessageOutboxes.put(publicKey, messagesForRecipient);
        }

        return removeFromQueue ? mMessageOutboxes.get(publicKey).pop() : mMessageOutboxes.get(publicKey).peek();
    }

    @Nullable
    private Identity getNextIdentityForPublicKey(@Nullable byte[] publicKey, boolean removeFromQueue) {
        if (!mIdentitiesOutboxes.containsKey(publicKey) || mIdentitiesOutboxes.get(publicKey).size() == 0) {
            ArrayDeque<Identity> identitiesForRecipient = mDataProvider.getIdentitiesForIdentity(publicKey, IDENTITIES_PER_RESPONSE);
            mIdentitiesOutboxes.put(publicKey, identitiesForRecipient);
        }

        return removeFromQueue ? mIdentitiesOutboxes.get(publicKey).pop() : mIdentitiesOutboxes.get(publicKey).peek();

    }

    private byte[] getPublicKeyForDeviceAddress(String address) {
        byte[] publicKey = mAddressesToIdentity.get(address).publicKey;
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

    // </editor-fold desc="Private API">
}
