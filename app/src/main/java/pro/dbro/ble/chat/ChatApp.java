package pro.dbro.ble.chat;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;

import java.util.Date;

import pro.dbro.ble.crypto.Identity;
import pro.dbro.ble.crypto.KeyPair;
import pro.dbro.ble.model.ChatContentProvider;
import pro.dbro.ble.model.DateUtil;
import pro.dbro.ble.model.Message;
import pro.dbro.ble.model.MessageTable;
import pro.dbro.ble.model.Peer;
import pro.dbro.ble.model.PeerTable;

/**
 * Created by davidbrodsky on 10/13/14.
 */
public class ChatApp {

    /**
     * @return the first user peer entry in the database,
     * or null if no identity is set.
     */
    public static Peer getPrimaryIdentity(@NonNull Context context) {
        // TODO: caching
        Cursor result = context.getContentResolver().query(ChatContentProvider.Peers.PEERS,
                null,
                PeerTable.secKey + " IS NOT NULL",
                null,
                null);

        if (result != null && result.moveToFirst()) {
            return new Peer(result);
        }
        return null;
    }

    /**
     * Create a new Identity and return the database id
     */
    public static int createNewIdentity(@NonNull Context context, @NonNull String alias) {
        KeyPair keypair = Identity.generateKeyPairForAlias(alias);
        ContentValues newIdentity = new ContentValues();
        newIdentity.put(PeerTable.pubKey, keypair.publicKey);
        newIdentity.put(PeerTable.secKey, keypair.secretKey);
        newIdentity.put(PeerTable.alias, alias);
        newIdentity.put(PeerTable.lastSeenDate, DateUtil.storedDateFormatter.format(new Date()));

        Uri newIdentityUri = context.getContentResolver().insert(ChatContentProvider.Peers.PEERS, newIdentity);
        return Integer.parseInt(newIdentityUri.getLastPathSegment());
    }

    public static byte[] getPrimaryIdentityResponse(@NonNull Context context) {
        Peer primaryIdentity = getPrimaryIdentity(context);

        if (primaryIdentity == null) throw new IllegalStateException("No primary Identity");

        return ChatProtocol.makeIdentityResponse(primaryIdentity.getKeyPair());
    }

    public static Cursor getMessagesToSend(@NonNull Context context) {
        // TODO: filtering
        return context.getContentResolver().query(ChatContentProvider.Messages.MESSAGES, null, null, null, null);
    }

    public static byte[] getBroadcastMessageResponseForString(@NonNull Context context, @NonNull String message) {
        return ChatProtocol.makePublicMessageResponse(getPrimaryIdentity(context).getKeyPair(), message);
    }
}
