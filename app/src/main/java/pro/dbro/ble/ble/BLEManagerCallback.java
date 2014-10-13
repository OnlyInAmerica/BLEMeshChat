package pro.dbro.ble.ble;

import pro.dbro.ble.model.BLEMessage;
import pro.dbro.ble.model.BLEPeer;

/**
 * Callback used to propagate BLE events up from
 * {@link pro.dbro.ble.ble.BLEPeripheral} and {@link pro.dbro.ble.ble.BLECentral}
 * to this class, and ultimately to clients of this class.
 */
public interface BLEManagerCallback {

    public void peerAvailable(BLEPeer peer);

    public void messageReceived(BLEMessage incomingMsg);

}
