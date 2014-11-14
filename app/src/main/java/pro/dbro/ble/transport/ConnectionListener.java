package pro.dbro.ble.transport;

/**
 * Created by davidbrodsky on 11/14/14.
 */
public interface ConnectionListener {

    public void connectedTo(String deviceAddress);

    public void disconnectedFrom(String deviceAddress);

}
