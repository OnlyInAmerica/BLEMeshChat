package pro.dbro.ble.transport.ble;

import pro.dbro.ble.data.model.Message;
import pro.dbro.ble.data.model.Peer;

/**
 * Callback used to represent high level BLE events
 * irrespective of whether the remote peer is Central or Peripheral
 */
public interface BLETransportCallback {

    public static enum PeerStatus { AVAILABLE, UNAVAILABLE }

    public void onPeerStatusChange(Peer peer, PeerStatus status);

    public void onMessageReceived(Message incomingMsg);

}
