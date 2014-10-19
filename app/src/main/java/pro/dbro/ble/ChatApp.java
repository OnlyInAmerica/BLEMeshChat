package pro.dbro.ble;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Date;

import pro.dbro.ble.model.DataUtil;
import pro.dbro.ble.model.Message;
import pro.dbro.ble.model.MessageTable;
import pro.dbro.ble.protocol.Identity;
import pro.dbro.ble.protocol.OwnedIdentity;
import pro.dbro.ble.crypto.SodiumShaker;
import pro.dbro.ble.model.ChatContentProvider;
import pro.dbro.ble.model.Peer;
import pro.dbro.ble.model.PeerTable;
import pro.dbro.ble.protocol.ChatProtocol;

/**
 * Created by davidbrodsky on 10/13/14.
 */
public class ChatApp {
    public static final String TAG = "ChatApp";

    /**
     * @return the first user peer entry in the database,
     * or null if no identity is set.
     */
    @Nullable
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
        OwnedIdentity keypair = SodiumShaker.generateOwnedIdentityForAlias(alias);
        ContentValues newIdentity = new ContentValues();
        newIdentity.put(PeerTable.pubKey, keypair.publicKey);
        newIdentity.put(PeerTable.secKey, keypair.secretKey);
        newIdentity.put(PeerTable.alias, alias);
        newIdentity.put(PeerTable.lastSeenDate, DataUtil.storedDateFormatter.format(new Date()));

        Uri newIdentityUri = context.getContentResolver().insert(ChatContentProvider.Peers.PEERS, newIdentity);
        return Integer.parseInt(newIdentityUri.getLastPathSegment());
    }

    public static byte[] getPrimaryIdentityResponse(@NonNull Context context) {
        Peer primaryIdentity = getPrimaryIdentity(context);

        if (primaryIdentity == null) throw new IllegalStateException("No primary Identity");

        return ChatProtocol.createIdentityResponse((OwnedIdentity) primaryIdentity.getKeyPair());
    }

    public static Cursor getMessagesToSend(@NonNull Context context) {
        // TODO: filtering
        return context.getContentResolver().query(ChatContentProvider.Messages.MESSAGES, null, null, null, null);
    }

    /**
     * Create a Message entry for the given body on behalf of the primary identity.
     * Return the byte[] response ready for transmission
     */
    public static byte[] createBroadcastMessageResponseForString(@NonNull Context context, @NonNull String message) {
        Peer user = getPrimaryIdentity(context);
        byte[] messageResponse = ChatProtocol.createPublicMessageResponse((OwnedIdentity) user.getKeyPair(), message);
        Message parsedMessage = consumeReceivedBroadcastMessage(context, messageResponse);
        return messageResponse;
    }

    public static Peer consumeReceivedIdentity(@NonNull Context context, @NonNull byte[] identity) {
        Identity protocolIdentity = ChatProtocol.consumeIdentityResponse(identity);

        // insert or update
        Peer applicationIdentity = getOrCreatePeerByProtocolIdentity(context, protocolIdentity);

        return applicationIdentity;

    }

    @Nullable
    public static Message consumeReceivedBroadcastMessage(@NonNull Context context, @NonNull byte[] message) {
        pro.dbro.ble.protocol.Message protocolMessage = ChatProtocol.consumeMessageResponse(message);

        // Query if peer exists
        Peer peer = getOrCreatePeerByProtocolIdentity(context, protocolMessage.sender);

        if (peer == null)
            throw new IllegalStateException("Failed to get peer for message");

        // Insert message into database
        ContentValues newMessageEntry = new ContentValues();
        newMessageEntry.put(MessageTable.body, protocolMessage.body);
        newMessageEntry.put(MessageTable.peerId, peer.getId());
        newMessageEntry.put(MessageTable.receivedDate, DataUtil.storedDateFormatter.format(new Date()));
        newMessageEntry.put(MessageTable.authoredDate, DataUtil.storedDateFormatter.format(protocolMessage.authoredDate));
        newMessageEntry.put(MessageTable.signature, protocolMessage.signature);
        Uri newMessageUri = context.getContentResolver().insert(
                    ChatContentProvider.Messages.MESSAGES,
                    newMessageEntry);
        // Fetch message
        Cursor newMessage = getMessageById(context, Integer.parseInt(newMessageUri.getLastPathSegment()));
        //Uri newIdentityUri = context.getContentResolver().insert(ChatContentProvider.Messages.MESSAGES, newMessage);
        if (newMessage != null && newMessage.moveToFirst()) {
            return new Message(newMessage);
        } else
            return null;
    }

    /** Utility */

    private static Cursor getMessageById(Context context, int id) {
        return context.getContentResolver().query(ChatContentProvider.Messages.MESSAGES, null,
               MessageTable.id + " = ?",
               new String[] {String.valueOf(id)},
               null);
    }

    public static Cursor getPeerByPubKey(Context context, byte[] public_key) {
        return context.getContentResolver().query(
               ChatContentProvider.Peers.PEERS,
               null,
               "quote(" + PeerTable.pubKey + ") = ?",
               new String[] {DataUtil.bytesToHex(public_key)},
               null);
    }

    private static Peer getOrCreatePeerByProtocolIdentity(Context context, Identity protocolPeer) {
        // Query if peer exists
        Cursor peerCursor = getPeerByPubKey(context, protocolPeer.publicKey);

        Peer peer = null;         // Wraps the Cursor representation when available

        ContentValues peerValues = new ContentValues();
        peerValues.put(PeerTable.lastSeenDate, DataUtil.storedDateFormatter.format(new Date()));
        peerValues.put(PeerTable.pubKey, protocolPeer.publicKey);
        peerValues.put(PeerTable.alias, protocolPeer.alias);

        if (peerCursor != null && peerCursor.moveToFirst()) {
            // Peer exists. Modify lastSeenDate
            peer = new Peer(peerCursor);
            int updated = context.getContentResolver().update(
                    ChatContentProvider.Peers.PEERS,
                    peerValues,
                    PeerTable.pubKey + " = ?" ,
                    new String[] {DataUtil.bytesToHex(protocolPeer.publicKey)});
            if (updated != 1) {
                Log.e(TAG, "Failed to update peer last seen");
            }
        } else {
            // Peer does not exist. Create.
            Uri peerUri = context.getContentResolver().insert(
                    ChatContentProvider.Peers.PEERS,
                    peerValues);

            // Fetch newly created peer
            peerCursor = getPeerById(context, Integer.parseInt(peerUri.getLastPathSegment()));

            if (peerCursor != null && peerCursor.moveToFirst()) {
                peer = new Peer(peerCursor);
            } else {
                Log.e(TAG, "Failed to query peer after insertion.");
            }
        }
        return peer;
    }

    public static Cursor getPeerById(Context context, int id) {
        return context.getContentResolver().query(
               ChatContentProvider.Peers.PEERS,
               null,
               PeerTable.id + " = ?",
               new String[] {String.valueOf(id)},
               null);
    }
}
