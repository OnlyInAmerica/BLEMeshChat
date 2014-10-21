package pro.dbro.ble.transport.ble;

import android.bluetooth.BluetoothGatt;

import pro.dbro.ble.data.model.Peer;

/**
 * Describes a connection to a peripheral device
 *
 * Created by davidbrodsky on 10/18/14.
 */
public class BLECentralConnection {

    /** The sequence of actions that a central must initiate
     *  for a full pairing
    */
    public enum CentralConnectionState { READ_ID, READ_MESSAGES, WRITE_MESSAGES }

    public BluetoothGatt gattServer;
    public CentralConnectionState connectionState;
    public Peer peer;
}
