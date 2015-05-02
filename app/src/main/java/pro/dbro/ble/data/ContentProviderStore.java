package pro.dbro.ble.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import pro.dbro.ble.crypto.KeyPair;
import pro.dbro.ble.crypto.SodiumShaker;
import pro.dbro.ble.data.model.ChatContentProvider;
import pro.dbro.ble.data.model.DataUtil;
import pro.dbro.ble.data.model.IdentityDeliveryTable;
import pro.dbro.ble.data.model.Message;
import pro.dbro.ble.data.model.MessageCollection;
import pro.dbro.ble.data.model.MessageDeliveryTable;
import pro.dbro.ble.data.model.MessageTable;
import pro.dbro.ble.data.model.Peer;
import pro.dbro.ble.data.model.PeerTable;
import pro.dbro.ble.protocol.IdentityPacket;
import pro.dbro.ble.protocol.MessagePacket;
import pro.dbro.ble.protocol.OwnedIdentityPacket;
import pro.dbro.ble.protocol.Protocol;

/**
 * API for the application's data persistence
 *
 * If the underlying data storage were to be replaced, this should be the
 * only class requiring modification.
 *
 * Created by davidbrodsky on 10/20/14.
 */
public class ContentProviderStore extends DataStore {
    public static final String TAG = "DataManager";

    public ContentProviderStore(Context context) {
        super(context);
    }

    @Override
    public void markMessageDeliveredToPeer(@NonNull MessagePacket messagePacket, @NonNull IdentityPacket recipientPacket) {
        Message message = getMessageBySignature(messagePacket.signature);
        Peer recipient = getPeerByPubKey(recipientPacket.publicKey);

        if (message == null || recipient == null) {
            Log.w(TAG, "Unable to record message delivery. No peer or message database id available");
            return;
        }

        ContentValues delivery = new ContentValues();
        delivery.put(MessageDeliveryTable.messageId, message.getId());
        delivery.put(MessageDeliveryTable.peerId, recipient.getId());

        mContext.getContentResolver().insert(ChatContentProvider.MessageDeliveries.MESSAGE_DELIVERIES, delivery);
        Log.i(TAG, "Recorded message delivery");
        message.close();
    }

    @Override
    public void markIdentityDeliveredToPeer(@NonNull IdentityPacket payloadIdentity, @NonNull IdentityPacket recipientIdentity) {
        Peer payloadPeer = getPeerByPubKey(payloadIdentity.publicKey);
        Peer recipientPeer = getPeerByPubKey(recipientIdentity.publicKey);

        if (payloadPeer == null || recipientPeer == null) {
            Log.w(TAG, "Unable to fetch payload or recipient identity. Cannot mark identity delivered");
            return;
        }

        ContentValues delivery = new ContentValues();
        delivery.put(IdentityDeliveryTable.peerPayloadId, payloadPeer.getId());
        delivery.put(IdentityDeliveryTable.peerRecipientId, recipientPeer.getId());

        mContext.getContentResolver().insert(ChatContentProvider.IdentityDeliveries.IDENTITY_DELIVERIES, delivery);
        Log.i(TAG, "Recorded identity delivery");
    }

    @Nullable
    @Override
    public Peer createLocalPeerWithAlias(@NonNull String alias, @Nullable Protocol protocol) {
        KeyPair keyPair = SodiumShaker.generateKeyPair();
        ContentValues dbEntry = new ContentValues();
        dbEntry.put(PeerTable.pubKey, keyPair.publicKey);
        dbEntry.put(PeerTable.secKey, keyPair.secretKey);
        dbEntry.put(PeerTable.alias, alias);
        dbEntry.put(PeerTable.lastSeenDate, DataUtil.storedDateFormatter.format(new Date()));
        if (protocol != null) {
            // If protocol is available, use it to cache the Identity packet for transmission
            dbEntry.put(PeerTable.rawPkt, protocol.serializeIdentity(
                    new OwnedIdentityPacket(keyPair.secretKey, keyPair.publicKey, alias, null)));
        }
        Uri newIdentityUri = mContext.getContentResolver().insert(ChatContentProvider.Peers.PEERS, dbEntry);
        return getPeerById(Integer.parseInt(newIdentityUri.getLastPathSegment()));
    }

    /**
     * @return the first user peer entry in the database,
     * or null if no identity is set.
     */
    @Override
    @Nullable
    public Peer getPrimaryLocalPeer() {
        // TODO: caching
        Cursor result = mContext.getContentResolver().query(ChatContentProvider.Peers.PEERS,
                null,
                PeerTable.secKey + " IS NOT NULL",
                null,
                null);
        if (result != null && result.moveToFirst()) {
            Peer peer = new Peer(result);
            result.close();
            return peer;
        }
        return null;
    }

    @Nullable
    @Override
    public List<MessagePacket> getOutgoingMessagesForPeer(@NonNull Peer recipient, int maxMessages) {
        // TODO : Don't send messages past a certain age etc?
        Cursor messagesCursor = mContext.getContentResolver().query(ChatContentProvider.Messages.MESSAGES, null, null, null, null);
        if (messagesCursor != null) {
            List<MessagePacket> messagesToSend = new ArrayList<>();
            while (messagesCursor.moveToNext()) {
                Message individualMessage = new Message(messagesCursor);
                if (!haveDeliveredMessageToPeer(individualMessage, recipient)) {
                    messagesToSend.add(individualMessage.getProtocolMessage(this));
                    if (messagesToSend.size() > maxMessages) break;
                }
            }

            messagesCursor.close();
            return messagesToSend;
        }
        return null;
    }

    @Override
    public List<IdentityPacket> getOutgoingIdentitiesForPeer(@NonNull Peer recipient, int maxIdentities) {
        // TODO : Don't send identities past a certain age etc?
        Cursor identitiesCursor = mContext.getContentResolver().query(ChatContentProvider.Peers.PEERS, null, null, null, null);
        if (identitiesCursor != null) {
            List<IdentityPacket> identitiesToSend = new ArrayList<>();
            while (identitiesCursor.moveToNext()) {
                Peer payloadPeer = new Peer(identitiesCursor);
                if (!haveDeliveredPeerIdentityToPeer(payloadPeer, recipient)) {
                    identitiesToSend.add(payloadPeer.getIdentity());
                    if (identitiesToSend.size() > maxIdentities) break;
                }

            }

            identitiesCursor.close();
            return identitiesToSend;
        }
        return null;
    }

    @Override
    public MessageCollection getRecentMessages() {
        Cursor messagesCursor = mContext.getContentResolver().query(ChatContentProvider.Messages.MESSAGES,
                null,
                null,
                null,
                MessageTable.receivedDate + " DESC");

        if (messagesCursor != null /*&& messagesCursor.moveToFirst()*/) {
            return new MessageCollection(messagesCursor);
        }
        return null;
    }

    @Override
    public MessageCollection getRecentMessagesByPeer(@NonNull Peer author) {
        Cursor messagesCursor = mContext.getContentResolver().query(ChatContentProvider.Messages.MESSAGES,
                null,
                MessageTable.peerId + "=?",
                new String[] { String.valueOf(author.getId()) },
                MessageTable.receivedDate + " DESC");

        if (messagesCursor != null /*&& messagesCursor.moveToFirst()*/) {
            return new MessageCollection(messagesCursor);
        }
        return null;
    }

    @Nullable
    @Override
    public Peer createOrUpdateRemotePeerWithProtocolIdentity(@NonNull IdentityPacket remoteIdentityPacket) {
        // Query if peer exists
        Peer peer = getPeerByPubKey(remoteIdentityPacket.publicKey);

        ContentValues peerValues = new ContentValues();
        peerValues.put(PeerTable.lastSeenDate, DataUtil.storedDateFormatter.format(new Date()));
        peerValues.put(PeerTable.pubKey, remoteIdentityPacket.publicKey);
        peerValues.put(PeerTable.alias, remoteIdentityPacket.alias);
        peerValues.put(PeerTable.rawPkt, remoteIdentityPacket.rawPacket);

        if (peer != null) {
            // Peer exists. Modify lastSeenDate
            Log.i(TAG, "Updating peer for pubkey " + DataUtil.bytesToHex(remoteIdentityPacket.publicKey));

            int updated = mContext.getContentResolver().update(
                    ChatContentProvider.Peers.PEERS,
                    peerValues,
                    "quote("+ PeerTable.pubKey + ") = ?" ,
                    new String[] {DataUtil.bytesToHex(remoteIdentityPacket.publicKey)});
            if (updated != 1) {
                Log.e(TAG, "Failed to update peer last seen");
            }
        } else {
            // Peer does not exist. Create.
            Uri peerUri = mContext.getContentResolver().insert(
                    ChatContentProvider.Peers.PEERS,
                    peerValues);

            // Fetch newly created peer
            peer = getPeerById(Integer.parseInt(peerUri.getLastPathSegment()));
            Log.i(TAG, String.format("Created new peer %d for pubkey %s", Integer.parseInt(peerUri.getLastPathSegment()), DataUtil.bytesToHex(remoteIdentityPacket.publicKey)));

            if (peer == null) {
                Log.e(TAG, "Failed to query peer after insertion.");
            }
        }
        return peer;
    }

    @Nullable
    @Override
    public Message createOrUpdateMessageWithProtocolMessage(@NonNull MessagePacket protocolMessagePacket) {
        // Query if peer exists
        Peer peer = getPeerByPubKey(protocolMessagePacket.sender.publicKey);

        if (peer == null)
            throw new IllegalStateException("Failed to get peer for message");

        // See if message exists
        Message message = getMessageBySignature(protocolMessagePacket.signature);
        if (message == null) {
            // Message doesn't exist in our database

            // Insert message into database
            ContentValues newMessageEntry = new ContentValues();
            newMessageEntry.put(MessageTable.body, protocolMessagePacket.body);
            newMessageEntry.put(MessageTable.peerId, peer.getId());
            newMessageEntry.put(MessageTable.receivedDate, DataUtil.storedDateFormatter.format(new Date()));
            newMessageEntry.put(MessageTable.authoredDate, DataUtil.storedDateFormatter.format(protocolMessagePacket.authoredDate));
            newMessageEntry.put(MessageTable.signature, protocolMessagePacket.signature);
            newMessageEntry.put(MessageTable.replySig, protocolMessagePacket.replySig);
            newMessageEntry.put(MessageTable.rawPacket, protocolMessagePacket.rawPacket);

            Uri newMessageUri = mContext.getContentResolver().insert(
                    ChatContentProvider.Messages.MESSAGES,
                    newMessageEntry);
            message = getMessageById(Integer.parseInt(newMessageUri.getLastPathSegment()));
        } else {
            // We already have a message with this signature
            // Since we currently don't have any mutable message fields (e.g hopcount)
            // do nothing
            Log.i(TAG, "Received stored message. Ignoring");
        }

        return message;
    }

    @Nullable
    @Override
    public Message getMessageBySignature(@NonNull byte[] signature) {
        Cursor messageCursor = mContext.getContentResolver().query(
                ChatContentProvider.Messages.MESSAGES,
                null,
                "quote(" + MessageTable.signature + ") = ?",
                new String[] {DataUtil.bytesToHex(signature)},
                null);
        if (messageCursor != null && messageCursor.moveToFirst()) {
            return new Message(messageCursor);
        }
        return null;
    }

    @Nullable
    @Override
    public Message getMessageById(int id) {
        Cursor messageCursor = mContext.getContentResolver().query(ChatContentProvider.Messages.MESSAGES, null,
                MessageTable.id + " = ?",
                new String[]{String.valueOf(id)},
                null);
        if (messageCursor != null && messageCursor.moveToFirst()) {
            return new Message(messageCursor);
        }
        return null;
    }

    @Nullable
    @Override
    public Peer getPeerByPubKey(@NonNull byte[] publicKey) {
        Cursor peerCursor = mContext.getContentResolver().query(
                ChatContentProvider.Peers.PEERS,
                null,
                "quote(" + PeerTable.pubKey + ") = ?",
                new String[] {DataUtil.bytesToHex(publicKey)},
                null);
        if (peerCursor != null && peerCursor.moveToFirst()) {
            Peer peer = new Peer(peerCursor);
            peerCursor.close();
            return peer;
        }
        return null;
    }

    @Nullable
    @Override
    public Peer getPeerById(int id) {
        Cursor peerCursor = mContext.getContentResolver().query(
                ChatContentProvider.Peers.PEERS,
                null,
                PeerTable.id + " = ?",
                new String[] {String.valueOf(id)},
                null);
        if (peerCursor != null && peerCursor.moveToFirst()) {
            Peer peer = new Peer(peerCursor);
            peerCursor.close();
            return peer;
        }
        return null;
    }

    @Override
    public int countPeers() {
        Cursor peerCursor = mContext.getContentResolver().query(
                ChatContentProvider.Peers.PEERS,
                new String[] {PeerTable.id},
                null,
                null,
                null);
        if (peerCursor != null) {
            int result = peerCursor.getCount();
            peerCursor.close();
            return result;
        }
        return 0;
    }

    @Override
    public int countMessagesPassed() {
        Cursor deliveryCursor = mContext.getContentResolver().query(
                ChatContentProvider.MessageDeliveries.MESSAGE_DELIVERIES,
                new String[] {MessageDeliveryTable.id},
                null,
                null,
                null);
        if (deliveryCursor != null) {
            int result = deliveryCursor.getCount();
            deliveryCursor.close();
            return result;
        }
        return 0;
    }

    /** Utility */

    private boolean haveDeliveredMessageToPeer(Message message, Peer peer) {
        Cursor deliveryCursor = mContext.getContentResolver().query(ChatContentProvider.MessageDeliveries.MESSAGE_DELIVERIES,
                null,
                MessageDeliveryTable.messageId + " = ? AND " + MessageDeliveryTable.peerId + " = ?",
                new String[]{String.valueOf(message.getId()), String.valueOf(peer.getId())},
                null);
        try {
            return deliveryCursor != null && deliveryCursor.moveToFirst();
        } finally {
            if (deliveryCursor != null) deliveryCursor.close();
        }
    }

    /**
     * @return whether peerPayload has been delivered to peerRecipient
     */
    private boolean haveDeliveredPeerIdentityToPeer(Peer peerPayload, Peer peerRecipient) {
        Cursor deliveryCursor = mContext.getContentResolver().query(ChatContentProvider.IdentityDeliveries.IDENTITY_DELIVERIES,
                null,
                IdentityDeliveryTable.peerRecipientId + " = ? AND " + IdentityDeliveryTable.peerPayloadId + " = ?",
                new String[]{String.valueOf(peerRecipient.getId()), String.valueOf(peerPayload.getId())},
                null);
        try {
            return deliveryCursor != null && deliveryCursor.moveToFirst();
        } finally {
            if (deliveryCursor != null) deliveryCursor.close();
        }
    }
}
