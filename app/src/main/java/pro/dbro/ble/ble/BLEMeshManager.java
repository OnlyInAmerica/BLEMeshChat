package pro.dbro.ble.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.support.annotation.NonNull;

import pro.dbro.ble.LogConsumer;
import pro.dbro.ble.model.BLEMessage;
import pro.dbro.ble.model.BLEPeer;

/**
 * A higher level class that manages advertising and scanning for
 * other BLE devices capable of mesh chat.
 *
 * Created by davidbrodsky on 10/12/14.
 */
public class BLEMeshManager implements BLEComponentCallback {

    private Context mContext;
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

        /**
         * One Central - Peripheral connection established
        */
        HALF_DUPLEX,

        /** Two symmetrical Central - Peripheral connections established with single peer.
         *  It is now possible for both devices to read each others Identity Characteristic
        */
        FULL_DUPLEX,

        /**
         * Local peer and remote peer have both completed read request on Identity Characteristic.
         * It is now possible for both devices to read each others Message Characteristic.
         * When syncing is complete return to {@link #SEARCHING}
        */
        SYNCING,

    }

    // <editor-fold desc="Public API">

    public BLEMeshManager(@NonNull Context context) {
        mContext = context;
        init();
        startBleServices();
    }

    public void setLogConsumer(LogConsumer logConsumer) {
        mLogger = logConsumer;
    }

    public void setMeshCallback(BLEManagerCallback cb) {
        mClientCallback = cb;
    }

    public void sendMessage(BLEPeer peer, BLEMessage outgoingMsg) {
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
        mCentral.setComponentCallback(this);
        mPeripheral = new BLEPeripheral(mContext);
        mPeripheral.setComponentCallback(this);

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

    @Override
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

    @Override
    public void onConnectedToCentral(BluetoothDevice centralPeer) {
        if (mConnectedToCentral) {
            // ignore;
            return;
        }
        synchronized (mStateGuard) {
            mConnectedToCentral = true;
            adjustStateForNewConnection();
            // Establish connection to centralPeer's peripheral
            if (mState == BLEConnectionState.FULL_DUPLEX) {
                // We already are connected to the other peer's peripheral
                return;
            }
        }
        centralPeer.connectGatt(mContext, true, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    boolean result = gatt.readCharacteristic(GATT.MESSAGES_CHARACTERISTIC);
                    if (result) {
                        synchronized (mStateGuard) {
                            changeState(BLEConnectionState.SYNCING);
                        }
                    }
                }
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
            case SEARCHING:
                changeState(BLEConnectionState.HALF_DUPLEX);
                // TODO: Start timer. Tear down connection if not reciprocated
                // in some interval
                break;
            case HALF_DUPLEX:
                changeState(BLEConnectionState.FULL_DUPLEX);
                break;
            default:
                // Ignore
                logEvent("Got peripheral connection in invalid state " + mState);
        }
    }

    private void changeState(BLEConnectionState newState) {
            mState = newState;
    }

    // </editor-fold desc="Private API">
}
