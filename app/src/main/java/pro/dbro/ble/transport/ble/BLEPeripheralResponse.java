package pro.dbro.ble.transport.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;

/**
 * Created by davidbrodsky on 10/20/14.
 */
public abstract class BLEPeripheralResponse {

    public static enum RequestType { READ, WRITE }

    public BluetoothGattCharacteristic mCharacteristic;
    public RequestType mRequestType;

    public BLEPeripheralResponse(BluetoothGattCharacteristic characteristic, RequestType requestType) {
        mCharacteristic = characteristic;
        mRequestType = requestType;
    }

    /**
     * @return any data sent in this response for caching purposes. Large requests/responses will be packetized
     * over several requests, but BLEPeripheral will deliver the final recombined result.
     */
    public abstract byte[] respondToRequest(BluetoothGattServer localPeripheral, BluetoothDevice remoteCentral,
                                          int requestId, BluetoothGattCharacteristic characteristic,
                                          boolean preparedWrite, boolean responseNeeded, byte[] value);
}
