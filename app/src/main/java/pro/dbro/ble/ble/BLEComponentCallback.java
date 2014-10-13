package pro.dbro.ble.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;

/**
 * Created by davidbrodsky on 10/12/14.
 */
public interface BLEComponentCallback {
    public void onConnectedToPeripheral(BluetoothGatt peripheralPeer);

    public void onConnectedToCentral(BluetoothDevice centralPeer);

}
