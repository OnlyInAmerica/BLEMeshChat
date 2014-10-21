package pro.dbro.ble.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Date;

import pro.dbro.ble.crypto.SodiumShaker;
import pro.dbro.ble.data.model.ChatContentProvider;
import pro.dbro.ble.data.model.DataUtil;
import pro.dbro.ble.data.model.Message;
import pro.dbro.ble.data.model.MessageCollection;
import pro.dbro.ble.data.model.MessageTable;
import pro.dbro.ble.data.model.Peer;
import pro.dbro.ble.data.model.PeerTable;
import pro.dbro.ble.protocol.Identity;
import pro.dbro.ble.protocol.OwnedIdentity;

/**
 * API for the application's data persistence
 *
 * If the underlying data storage were to be replaced, this should be the
 * only class requiring modification.
 *
 * Created by davidbrodsky on 10/20/14.
 */
public class SQLDataStore extends DataStore {
    public static final String TAG = "DataManager";

    public SQLDataStore(Context context) {
        super(context);
    }

    @Nullable
    @Override
    public Peer createLocalPeerWithAlias(@NonNull String alias) {
        OwnedIdentity keypair = SodiumShaker.generateOwnedIdentityForAlias(alias);
        ContentValues newIdentity = new ContentValues();
        newIdentity.put(PeerTable.pubKey, keypair.publicKey);
        newIdentity.put(PeerTable.secKey, keypair.secretKey);
        newIdentity.put(PeerTable.alias, alias);
        newIdentity.put(PeerTable.lastSeenDate, DataUtil.storedDateFormatter.format(new Date()));

        Uri newIdentityUri = mContext.getContentResolver().insert(ChatContentProvider.Peers.PEERS, newIdentity);
        return getPeerById(mContext, Integer.parseInt(newIdentityUri.getLastPathSegment()));
    }

    /**
     * @return the first user peer entry in the database,
     * or null if no identity is set.
     */
    @Nullable
    public Peer getPrimaryLocalPeer() {
        // TODO: caching
        Cursor result = mContext.getContentResolver().query(ChatContentProvider.Peers.PEERS,
                null,
                PeerTable.secKey + " IS NOT NULL",
                null,
                null);
        if (result != null && result.moveToFirst()) {
            return new Peer(result);
        }
        return null;
    }

    @Nullable
    @Override
    public MessageCollection getOutgoingMessagesForPeer(@NonNull Peer recipient) {
        // TODO: filtering. Don't return Cursor
        Cursor messagesCursor = mContext.getContentResolver().query(ChatContentProvider.Messages.MESSAGES, null, null, null, null);
        return new MessageCollection(messagesCursor);
    }

    @Nullable
    @Override
    public Peer createRemotePeerWithProtocolIdentity(@NonNull Identity identity) {
        return null;
    }

    @Override
    public Message createMessageWithProtocolMessage(@NonNull pro.dbro.ble.protocol.Message protocolMessage) {
        return null;
    }

    @Override
    public Message getMessageById(int id) {
        return null;
    }

    @Override
    public Peer getPeerByPubKey(@NonNull byte[] public_key) {
        return null;
    }

    @Override
    public Peer getPeerById(int id) {
        return null;
    }

    public Cursor getMessagesToSend(@NonNull Context context) {
        // TODO: filtering. Don't return Cursor
        return context.getContentResolver().query(ChatContentProvider.Messages.MESSAGES, null, null, null, null);
    }

    @Nullable
    public Message createMessageWithProtocolMessage(@NonNull Context context, @NonNull pro.dbro.ble.protocol.Message protocolMessage) {
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

        // DEBUGGING
        new Exception().printStackTrace(System.out);
        Uri newMessageUri = context.getContentResolver().insert(
                ChatContentProvider.Messages.MESSAGES,
                newMessageEntry);
        // Fetch message
        return getMessageById(context, Integer.parseInt(newMessageUri.getLastPathSegment()));
    }

    /** Utility */

    /**
     * Create a new Peer for the application user and return the database id
     */
    public Peer createLocalPeerForAlias(@NonNull Context context, @NonNull String alias) {
        OwnedIdentity keypair = SodiumShaker.generateOwnedIdentityForAlias(alias);
        ContentValues newIdentity = new ContentValues();
        newIdentity.put(PeerTable.pubKey, keypair.publicKey);
        newIdentity.put(PeerTable.secKey, keypair.secretKey);
        newIdentity.put(PeerTable.alias, alias);
        newIdentity.put(PeerTable.lastSeenDate, DataUtil.storedDateFormatter.format(new Date()));

        Uri newIdentityUri = context.getContentResolver().insert(ChatContentProvider.Peers.PEERS, newIdentity);
        return getPeerById(context, Integer.parseInt(newIdentityUri.getLastPathSegment()));
    }

    @Nullable
    private Message getMessageById(@NonNull Context context, int id) {
        Cursor messageCursor = context.getContentResolver().query(ChatContentProvider.Messages.MESSAGES, null,
                MessageTable.id + " = ?",
                new String[] {String.valueOf(id)},
                null);
        if (messageCursor != null && messageCursor.moveToFirst()) {
            return new Message(messageCursor);
        }
        return null;
    }

    @Nullable
    public Peer getPeerByPubKey(@NonNull Context context, @NonNull byte[] public_key) {
        Cursor peerCursor = context.getContentResolver().query(
                ChatContentProvider.Peers.PEERS,
                null,
                "quote(" + PeerTable.pubKey + ") = ?",
                new String[] {DataUtil.bytesToHex(public_key)},
                null);
        if (peerCursor != null && peerCursor.moveToFirst()) {
            return new Peer(peerCursor);
        }
        return null;
    }

    @Nullable
    private Peer getOrCreatePeerByProtocolIdentity(@NonNull Context context, @NonNull Identity protocolPeer) {
        // Query if peer exists
        Peer peer = getPeerByPubKey(context, protocolPeer.publicKey);

        ContentValues peerValues = new ContentValues();
        peerValues.put(PeerTable.lastSeenDate, DataUtil.storedDateFormatter.format(new Date()));
        peerValues.put(PeerTable.pubKey, protocolPeer.publicKey);
        peerValues.put(PeerTable.alias, protocolPeer.alias);

        if (peer != null) {
            // Peer exists. Modify lastSeenDate
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
            peer = getPeerById(context, Integer.parseInt(peerUri.getLastPathSegment()));

            if (peer == null) {
                Log.e(TAG, "Failed to query peer after creation.");
            }
        }
        return peer;
    }

    @Nullable
    public Peer getPeerById(@NonNull Context context, int id) {
        Cursor peerCursor = context.getContentResolver().query(
                ChatContentProvider.Peers.PEERS,
                null,
                PeerTable.id + " = ?",
                new String[] {String.valueOf(id)},
                null);
        if (peerCursor != null && peerCursor.moveToFirst()) {
            return new Peer(peerCursor);
        }
        return null;
    }
}
