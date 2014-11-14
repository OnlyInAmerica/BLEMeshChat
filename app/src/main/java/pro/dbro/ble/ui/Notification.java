package pro.dbro.ble.ui;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import java.util.ArrayList;

import pro.dbro.ble.R;
import pro.dbro.ble.data.model.DataUtil;
import pro.dbro.ble.data.model.Message;
import pro.dbro.ble.data.model.Peer;
import pro.dbro.ble.ui.activities.MainActivity;

/**
 * Created by davidbrodsky on 11/14/14.
 */
public class Notification {

    /** Notification Ids */
    private static final int MESSAGE_NOTIFICATION_ID = 1;
    private static final int PEER_AVAILABLE_NOTIFICATION_ID = 2;

    private static final int MAX_MESSAGES_TO_SHOW = 6;

    private static final ArrayList<String> sNotificationInboxItems = new ArrayList<>(MAX_MESSAGES_TO_SHOW + 1);

    // <editor-fold desc="Public API">

    /**
     * Display a notification representing peer being available, or remove any indicating such
     * if isAvailable is false.
     *
     * Does not call peer.close()
     */
    public static void displayPeerAvailableNotification(@NonNull Context context, @NonNull Peer peer, boolean isAvailable) {
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (!isAvailable) {
            mNotificationManager.cancel(DataUtil.bytesToHex(peer.getPublicKey()), PEER_AVAILABLE_NOTIFICATION_ID);
            return;
        }
        if (peer.getAlias() == null) return; // TODO : Notify of peers without alias?

        String title = String.format("%s is nearby", peer.getAlias());

        Intent resultIntent = new Intent(context, MainActivity.class);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setSmallIcon(R.drawable.ic_launcher);
        builder.setContentTitle(title);
        builder.setContentIntent(makePendingIntent(context, resultIntent));
        builder.setContentText(context.getString(R.string.notification_touch_to_chat));

        mNotificationManager.notify(DataUtil.bytesToHex(peer.getPublicKey()), PEER_AVAILABLE_NOTIFICATION_ID, builder.build());
    }

    /**
     * Display a notification representing a new received message. Multiple calls to this method are displayed as a single
     * notification, showing a preview of the last MAX_MESSAGES_TO_SHOW messages.
     *
     * Does not call message.close() or sender.close()
     */
    public static void displayMessageNotification(@NonNull Context context, @NonNull Message message, @Nullable Peer sender) {
        StringBuilder nBuilder = new StringBuilder();
        if (sender != null && sender.getAlias() != null) {
            nBuilder.append(sender.getAlias());
            nBuilder.append(": ");
        }
        nBuilder.append(message.getBody().substring(0, 80) + (message.getBody().length() > 80 ? "..." : ""));
        sNotificationInboxItems.add(nBuilder.toString());
        if (sNotificationInboxItems.size() > MAX_MESSAGES_TO_SHOW) sNotificationInboxItems.remove(sNotificationInboxItems.size()-1);

        Intent resultIntent = new Intent(context, MainActivity.class);

        NotificationCompat.InboxStyle inboxStyle =
                new NotificationCompat.InboxStyle();
        inboxStyle.setBigContentTitle(context.getString(R.string.notification_new_messages));

        for (String inboxItem : sNotificationInboxItems) {
            inboxStyle.addLine(inboxItem);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setContentTitle(context.getString(R.string.notification_new_messages));
        builder.setSmallIcon(R.drawable.ic_launcher);
        builder.setContentIntent(makePendingIntent(context, resultIntent));
        builder.setStyle(inboxStyle);
        builder.setContentText(sNotificationInboxItems.get(0));

        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.notify(MESSAGE_NOTIFICATION_ID, builder.build());
    }

    // </editor-fold desc="Public API">

    // <editor-fold desc="Private API">

    private static PendingIntent makePendingIntent(@NonNull Context context, @NonNull Intent resultIntent) {
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        // Adds the back stack
        stackBuilder.addParentStack(MainActivity.class);
        // Adds the Intent to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        // Gets a PendingIntent containing the entire back stack
        return stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // </editor-fold desc="Private API">
}
