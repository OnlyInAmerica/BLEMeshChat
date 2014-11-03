package pro.dbro.ble;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;

import pro.dbro.ble.data.ContentProviderStore;
import pro.dbro.ble.data.DataStore;
import pro.dbro.ble.data.model.DataUtil;
import pro.dbro.ble.data.model.Message;
import pro.dbro.ble.data.model.MessageCollection;
import pro.dbro.ble.data.model.Peer;
import pro.dbro.ble.protocol.BLEProtocol;
import pro.dbro.ble.protocol.IdentityPacket;
import pro.dbro.ble.protocol.MessagePacket;
import pro.dbro.ble.protocol.OwnedIdentityPacket;
import pro.dbro.ble.protocol.Protocol;
import pro.dbro.ble.transport.Transport;
import pro.dbro.ble.transport.ble.BLETransport;

/**
 * Created by davidbrodsky on 10/13/14.
 */
public class ChatApp implements Transport.TransportDataProvider, Transport.TransportEventCallback {
    public static final String TAG = "ChatApp";

    private Context   mContext;
    private DataStore mDataStore;
    private Transport mTransport;
    private Protocol  mProtocol;

    // <editor-fold desc="Public API">

    public ChatApp(Context context) {
        mContext = context;

        mProtocol  = new BLEProtocol();
        mDataStore = new ContentProviderStore(context);
    }

    // <editor-fold desc="Identity & Availability">

    public void makeAvailable() {
        if (mDataStore.getPrimaryLocalPeer() == null) {
            Log.e(TAG, "Now primary Identity. Cannot make client available");
            return;
        }
        mTransport = new BLETransport(mContext, mDataStore.getPrimaryLocalPeer().getIdentity(), mProtocol, this);
        mTransport.setTransportCallback(this);
        mTransport.makeAvailable();
    }

    public void makeUnavailable() {
        mTransport.makeUnavailable();
    }

    public Peer getPrimaryIdentity() {
        return mDataStore.getPrimaryLocalPeer();
    }

    public Peer createPrimaryIdentity(String alias) {
        return mDataStore.createLocalPeerWithAlias(alias, mProtocol);
    }

    // </editor-fold desc="Identity & Availability">

    // <editor-fold desc="Messages">

    public MessageCollection getRecentMessagesFeed() {
        return mDataStore.getRecentMessages();
    }

    public void sendPublicMessageFromPrimaryIdentity(String body) {
        MessagePacket messagePacket = mProtocol.serializeMessage((OwnedIdentityPacket) getPrimaryIdentity().getIdentity(), body);
        mDataStore.createOrUpdateMessageWithProtocolMessage(messagePacket).close();
        mTransport.sendMessage(messagePacket);
    }

    // </editor-fold desc="Messages">

    public DataStore getDataStore() {
        return mDataStore;
    }

    // </editor-fold desc="Public API">

    // <editor-fold desc="Private API">

    /** TransportDataProvider */

    @Override
    public ArrayDeque<MessagePacket> getMessagesForIdentity(@Nullable byte[] recipientPublicKey, int maxMessages) {
        ArrayDeque<MessagePacket> messagePacketQueue = new ArrayDeque<>();

        if (recipientPublicKey != null) {
            // Get messages not delievered to peer
            Peer recipient = mDataStore.getPeerByPubKey(recipientPublicKey);
            List<MessagePacket> messages = mDataStore.getOutgoingMessagesForPeer(recipient, maxMessages);
            recipient.close();

            if (messages == null || messages.size() == 0) {
                Log.i(TAG, "Got no messages for peer with pub key " + DataUtil.bytesToHex(recipientPublicKey));
            } else {
                messagePacketQueue.addAll(messages);
            }
        } else {
            // Get most recent messages
            MessageCollection recentMessages = getRecentMessagesFeed();
            for (int x = 0; x < Math.min(maxMessages, recentMessages.getCursor().getCount()); x++) {
                Message currentMessage = recentMessages.getMessageAtPosition(x);
                if (currentMessage != null)
                    messagePacketQueue.add(currentMessage.getProtocolMessage(mDataStore));
            }
            recentMessages.close();
        }
        return messagePacketQueue;
    }

    /**
     * Return a queue of identity packets for delivery to the remote identity with the given
     * public key.
     *
     * If recipientPublicKey is null only the user identity will be propagated
     */
    @Override
    public ArrayDeque<IdentityPacket> getIdentitiesForIdentity(@Nullable byte[] recipientPublicKey, int maxIdentities) {
        List<IdentityPacket> identities;
        ArrayDeque<IdentityPacket> identityPacketQueue = new ArrayDeque<>();
        if (recipientPublicKey != null) {
            // We have a public key for the remote peer, fetch undelivered identities
            Peer recipient = mDataStore.getPeerByPubKey(recipientPublicKey);
            identities = mDataStore.getOutgoingIdentitiesForPeer(recipient, maxIdentities);
            if (identities == null || identities.size() == 0) {
                Log.i(TAG, "Got no identities to send for peer with pub key " + DataUtil.bytesToHex(recipientPublicKey));
            }
            else {
                identityPacketQueue.addAll(identities);
            }
        } else {
            // No public key for the remote peer, just send our identity
            // NOTE: The caller of this method must know not to re-try the request
            // else it could get in an endless loop
            Peer user = mDataStore.getPrimaryLocalPeer();
            if (user != null)
                identityPacketQueue.add(user.getIdentity());
            user.close();
        }

        return identityPacketQueue;
    }

    /** TransportEventCallback */

    @Override
    public void becameAvailable(IdentityPacket identityPacket) {
        Log.i(TAG, String.format("%s available", identityPacket.alias));
    }

    @Override
    public void becameUnavailable(IdentityPacket identityPacket) {
        Log.i(TAG, String.format("%s unavailable", identityPacket.alias));
    }

    @Override
    public void sentIdentity(@NonNull IdentityPacket payloadIdentity, @Nullable IdentityPacket recipientIdentity) {
        Log.i(TAG, String.format("Sent identity: %s", payloadIdentity.alias));
        if (recipientIdentity != null)
            mDataStore.markIdentityDeliveredToPeer(payloadIdentity, recipientIdentity);
    }

    @Override
    public void sentMessage(@NonNull MessagePacket messagePacket, IdentityPacket recipientIdentity) {
        Log.i(TAG, String.format("Sent message: '%s'", messagePacket.body));
        if (recipientIdentity != null)
            mDataStore.markMessageDeliveredToPeer(messagePacket, recipientIdentity);
    }

    @Override
    public void receivedIdentity(IdentityPacket identityPacket) {
        Log.i(TAG, String.format("Received identity for '%s' with pubkey %s", identityPacket.alias, DataUtil.bytesToHex(identityPacket.publicKey)));
        mDataStore.createOrUpdateRemotePeerWithProtocolIdentity(identityPacket).close();
    }

    @Override
    public void receivedMessage(MessagePacket messagePacket) {
        Log.i(TAG, String.format("Received message: '%s' from peer '%s' with pubkey '%s' ", messagePacket.body, messagePacket.sender.alias, DataUtil.bytesToHex(messagePacket.sender.publicKey)));
        mDataStore.createOrUpdateMessageWithProtocolMessage(messagePacket).close();
        mDataStore.markMessageDeliveredToPeer(messagePacket, messagePacket.sender); // Never deliver this message back to peer it came from
    }

    // </editor-fold desc="Private API">


//    public ChatApp(DataStorage dataStorage, Protocol protocol, )
//    public static final String TAG = "ChatApp";
//
//    /** Outgoing Data **/
//
//    public static byte[] getPrimaryLoaclPeerIdentityResponse(@NonNull Context context) {
//        Peer primaryIdentity = getPrimaryLocalPeer(context);
//
//        if (primaryIdentity == null) throw new IllegalStateException("No primary Identity");
//
//        return ChatProtocol.serializeIdentity((OwnedIdentity) primaryIdentity.getIdentity());
//    }
//
//    public static Cursor getMessagesToSend(@NonNull Context context) {
//        // TODO: filtering
//        return context.getContentResolver().query(ChatContentProvider.Messages.MESSAGES, null, null, null, null);
//    }
//
//    /**
//     * Create a Message entry for the given body on behalf of the primary identity.
//     * Return the byte[] response ready for transmission
//     */
//    public static byte[] saveAndCreateBroadcastMessageResponseForString(@NonNull Context context, @NonNull String message) {
//        Peer user = getPrimaryLocalPeer(context);
//        byte[] messageResponse = ChatProtocol.serializeMessage((OwnedIdentity) user.getIdentity(), message);
//        // Create an entry for the Message in our application database
//        Message parsedMessage = consumeReceivedBroadcastMessage(context, messageResponse);
//        return messageResponse;
//    }
//
//    public static byte[] createBroadcastMessageResponseForString(@NonNull Context context, @NonNull String message) {
//        Peer user = getPrimaryLocalPeer(context);
//        byte[] messageResponse = ChatProtocol.serializeMessage((OwnedIdentity) user.getIdentity(), message);
//        return messageResponse;
//    }
//
//    /** Incoming Data **/
//
//    public static Peer consumeReceivedIdentity(@NonNull Context context, @NonNull byte[] identity) {
//        Identity protocolIdentity = ChatProtocol.deserializeIdentity(identity);
//
//        // insert or update
//        Peer applicationIdentity = getOrCreatePeerByProtocolIdentity(context, protocolIdentity);
//
//        return applicationIdentity;
//
//    }
//
//    @Nullable
//    public static Message consumeReceivedBroadcastMessage(@NonNull Context context, @NonNull byte[] message) {
//        pro.dbro.ble.protocol.Message protocolMessage = ChatProtocol.deserializeMessage(message);
//
//        // Query if peer exists
//        Peer peer = getOrCreatePeerByProtocolIdentity(context, protocolMessage.sender);
//
//        if (peer == null)
//            throw new IllegalStateException("Failed to get peer for message");
//
//        // Insert message into database
//        ContentValues newMessageEntry = new ContentValues();
//        newMessageEntry.put(MessageTable.body, protocolMessage.body);
//        newMessageEntry.put(MessageTable.peerId, peer.getId());
//        newMessageEntry.put(MessageTable.receivedDate, DataUtil.storedDateFormatter.format(new Date()));
//        newMessageEntry.put(MessageTable.authoredDate, DataUtil.storedDateFormatter.format(protocolMessage.authoredDate));
//        newMessageEntry.put(MessageTable.signature, protocolMessage.signature);
//
//        // DEBUGGING
//        new Exception().printStackTrace(System.out);
//        Uri newMessageUri = context.getContentResolver().insert(
//                    ChatContentProvider.Messages.MESSAGES,
//                    newMessageEntry);
//        // Fetch message
//        return getMessageById(context, Integer.parseInt(newMessageUri.getLastPathSegment()));
//    }
//
//    /** Utility */
//
//    /**
//     * @return the first user peer entry in the database,
//     * or null if no identity is set.
//     */
//    @Nullable
//    public static Peer getPrimaryLocalPeer(@NonNull Context context) {
//        // TODO: caching
//        Cursor result = context.getContentResolver().query(ChatContentProvider.Peers.PEERS,
//                null,
//                PeerTable.secKey + " IS NOT NULL",
//                null,
//                null);
//
//        if (result != null && result.moveToFirst()) {
//            return new Peer(result);
//        }
//        return null;
//    }
//
//    /**
//     * Create a new Peer for the application user and return the database id
//     */
//    public static int createLocalPeer(@NonNull Context context, @NonNull String alias) {
//        OwnedIdentity keypair = SodiumShaker.generateOwnedIdentityForAlias(alias);
//        ContentValues newIdentity = new ContentValues();
//        newIdentity.put(PeerTable.pubKey, keypair.publicKey);
//        newIdentity.put(PeerTable.secKey, keypair.secretKey);
//        newIdentity.put(PeerTable.alias, alias);
//        newIdentity.put(PeerTable.lastSeenDate, DataUtil.storedDateFormatter.format(new Date()));
//
//        Uri newIdentityUri = context.getContentResolver().insert(ChatContentProvider.Peers.PEERS, newIdentity);
//        return Integer.parseInt(newIdentityUri.getLastPathSegment());
//    }
//
//    @Nullable
//    private static Message getMessageById(@NonNull Context context, int id) {
//        Cursor messageCursor = context.getContentResolver().query(ChatContentProvider.Messages.MESSAGES, null,
//               MessageTable.id + " = ?",
//               new String[] {String.valueOf(id)},
//               null);
//        if (messageCursor != null && messageCursor.moveToFirst()) {
//            return new Message(messageCursor);
//        }
//        return null;
//    }
//
//    @Nullable
//    public static Peer getPeerByPubKey(@NonNull Context context, @NonNull byte[] public_key) {
//        Cursor peerCursor = context.getContentResolver().query(
//               ChatContentProvider.Peers.PEERS,
//               null,
//               "quote(" + PeerTable.pubKey + ") = ?",
//               new String[] {DataUtil.bytesToHex(public_key)},
//               null);
//        if (peerCursor != null && peerCursor.moveToFirst()) {
//            return new Peer(peerCursor);
//        }
//        return null;
//    }
//
//    @Nullable
//    private static Peer getOrCreatePeerByProtocolIdentity(@NonNull Context context, @NonNull Identity protocolPeer) {
//        // Query if peer exists
//        Peer peer = getPeerByPubKey(context, protocolPeer.publicKey);
//
//        ContentValues peerValues = new ContentValues();
//        peerValues.put(PeerTable.lastSeenDate, DataUtil.storedDateFormatter.format(new Date()));
//        peerValues.put(PeerTable.pubKey, protocolPeer.publicKey);
//        peerValues.put(PeerTable.alias, protocolPeer.alias);
//
//        if (peer != null) {
//            // Peer exists. Modify lastSeenDate
//            int updated = context.getContentResolver().update(
//                    ChatContentProvider.Peers.PEERS,
//                    peerValues,
//                    PeerTable.pubKey + " = ?" ,
//                    new String[] {DataUtil.bytesToHex(protocolPeer.publicKey)});
//            if (updated != 1) {
//                Log.e(TAG, "Failed to update peer last seen");
//            }
//        } else {
//            // Peer does not exist. Create.
//            Uri peerUri = context.getContentResolver().insert(
//                    ChatContentProvider.Peers.PEERS,
//                    peerValues);
//
//            // Fetch newly created peer
//            peer = getPeerById(context, Integer.parseInt(peerUri.getLastPathSegment()));
//
//            if (peer == null) {
//                Log.e(TAG, "Failed to query peer after creation.");
//            }
//        }
//        return peer;
//    }
//
//    @Nullable
//    public static Peer getPeerById(@NonNull Context context, int id) {
//        Cursor peerCursor = context.getContentResolver().query(
//               ChatContentProvider.Peers.PEERS,
//               null,
//               PeerTable.id + " = ?",
//               new String[] {String.valueOf(id)},
//               null);
//        if (peerCursor != null && peerCursor.moveToFirst()) {
//            return new Peer(peerCursor);
//        }
//        return null;
//    }
}
