package pro.dbro.ble;

/**
 * Implemented by a Service or other entity to report an Activity is bound, and thus
 * in the foreground. e.g: Useful to determine whether to post message notifications.
 *
 * Created by davidbrodsky on 11/14/14.
 */
public interface ActivityRecevingMessagesIndicator {

    public boolean isActivityReceivingMessages();

}
