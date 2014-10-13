package pro.dbro.ble.ble;

import pro.dbro.ble.model.Message;
import pro.dbro.ble.model.Peer;

/**
 * Callback used to represent high level BLE events
 * irrespective of whether the remote peer is Central or Peripheral
 */
public interface BLEManagerCallback {

    public void peerAvailable(Peer peer);

    public void messageReceived(Message incomingMsg);

}
