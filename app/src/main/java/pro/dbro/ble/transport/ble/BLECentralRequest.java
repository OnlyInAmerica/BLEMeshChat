package pro.dbro.ble.transport.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

/**
 * A request the BLECentral should perform on a remote BLEPeripheral
 *
 * Created by davidbrodsky on 10/20/14.
 */
public abstract class BLECentralRequest {
    private static final String TAG = "BLECentralRequest";

    public static enum RequestType { READ, WRITE }

    public BluetoothGattCharacteristic mCharacteristic;
    public RequestType mRequestType;

    public BLECentralRequest(BluetoothGattCharacteristic characteristic, RequestType requestType) {
        mCharacteristic = characteristic;
        mRequestType = requestType;
    }

    public final void doRequest(BluetoothGatt remotePeripheral) {
        boolean success = false;
        switch (mRequestType) {
            case READ:
                success = remotePeripheral.readCharacteristic(mCharacteristic);
                break;
            case WRITE:
                mCharacteristic.setValue(getDataToWrite(remotePeripheral));
                success = remotePeripheral.writeCharacteristic(mCharacteristic);
                break;
        }
        Log.i(TAG, String.format("%s to %s... success: %b", mRequestType.toString(), mCharacteristic.getUuid().toString().substring(0,3), success));
    }

    /**
     * Handle the request response and return whether this request should
     * be considered complete. If it is not complete, it will be re-issued
     * along with any modifications made to characteristic.
     *
     * @return true if this request should be considered complete. false if it should
     * be re-issued with characteristic
     */
    public abstract boolean handleResponse(BluetoothGatt remotePeripheral, BluetoothGattCharacteristic characteristic, int status);

    /**
     * If this is a WRITE request, Override to return data to write
     * with knowledge of the actual remotePeripheral
     */
    public byte[] getDataToWrite(BluetoothGatt remotePeripheral) {
        return null;
    }

}
