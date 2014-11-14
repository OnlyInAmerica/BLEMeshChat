package pro.dbro.ble.transport;

/**
 * Created by davidbrodsky on 11/14/14.
 */
public interface ConnectionGovernor {

    public boolean shouldConnectToAddress(String address);

}
