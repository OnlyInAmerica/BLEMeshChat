package pro.dbro.ble.ble;

import android.content.Context;
import android.support.annotation.NonNull;

import pro.dbro.ble.model.BLEMessage;
import pro.dbro.ble.model.BLEPeer;

/**
 * A higher level class that manages advertising and scanning for
 * other BLE devices capable of mesh chat.
 *
 * Created by davidbrodsky on 10/12/14.
 */
public class BLEMeshManager {

    private Context mContext;
    private BLECentral mCentral;
    private BLEPeripheral mPeripheral;

    public interface BLEMeshManagerCallback {
        public void peerAvailable(BLEPeer peer);
        public void messageReceived(BLEMessage incomingMsg);
    }

    // <editor-fold desc="Public API">

    public BLEMeshManager(@NonNull Context context) {
        mContext = context;
        init();
    }

    public void sendMessage(BLEPeer peer, BLEMessage outgoingMsg) {
        // Establish connection with peer
        // Send message to peer
    }

    // </editor-fold>

    // <editor-fold desc="Private API">

    private void init() {
        mCentral = new BLECentral(mContext);
        mPeripheral = new BLEPeripheral(mContext);
    }

    private void startBleServices() {
        mCentral.start();
        mPeripheral.start();
    }

    private void stopBleServices() {
        mCentral.stop();
        mPeripheral.stop();
    }

    // </editor-fold>
}
