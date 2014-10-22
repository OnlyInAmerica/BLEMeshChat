package pro.dbro.ble;

import android.content.Context;
import android.util.Log;

import java.util.ArrayDeque;

import pro.dbro.ble.data.ContentProviderStore;
import pro.dbro.ble.data.DataStore;
import pro.dbro.ble.data.model.MessageCollection;
import pro.dbro.ble.data.model.Peer;
import pro.dbro.ble.protocol.BLEProtocol;
import pro.dbro.ble.protocol.Identity;
import pro.dbro.ble.protocol.Message;
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
        Message message = mProtocol.serializeMessage((pro.dbro.ble.protocol.OwnedIdentity) getPrimaryIdentity().getIdentity(), body);
        mDataStore.createOrUpdateMessageWithProtocolMessage(message);
        mTransport.sendMessage(message);
    }

    // </editor-fold desc="Messages">

    public DataStore getDataStore() {
        return mDataStore;
    }

    // </editor-fold desc="Public API">

    // <editor-fold desc="Private API">

    /** TransportDataProvider */

    @Override
    public ArrayDeque<Message> getMessagesForIdentity(byte[] recipientPublicKey, int maxMessages) {
        Peer recipient = mDataStore.getPeerByPubKey(recipientPublicKey);
        MessageCollection messages = mDataStore.getOutgoingMessagesForPeer(recipient);
        ArrayDeque<Message> messageQueue = new ArrayDeque<>();
        for (int x = 0; x < maxMessages; x++) {
            messageQueue.push(messages.getMessageAtPosition(x).getProtocolMessage(mDataStore));
        }
        return messageQueue;
    }

    @Override
    public ArrayDeque<Identity> getIdentitiesForIdentity(byte[] recipientPublicKey, int maxIdentities) {
        // TODO: Propagate identities
        ArrayDeque<Identity> identities = new ArrayDeque<>();
        identities.push(mDataStore.getPrimaryLocalPeer().getIdentity());
        return identities;
    }

    /** TransportEventCallback */

    @Override
    public void becameAvailable(Identity identity) {
        Log.i(TAG, String.format("%s available",identity.alias));
    }

    @Override
    public void becameUnavailable(Identity identity) {
        Log.i(TAG, String.format("%s unavailable",identity.alias));
    }

    @Override
    public void sentIdentity(Identity identity) {
        Log.i(TAG, String.format("Send identity: %s",identity.alias));
    }

    @Override
    public void sentMessage(Message message) {
        Log.i(TAG, String.format("Send message: '%s'",message.body));

    }

    @Override
    public void receivedIdentity(Identity identity) {
        Log.i(TAG, String.format("Received identity for '%s'", identity.alias));
        mDataStore.createOrUpdateRemotePeerWithProtocolIdentity(identity);
    }

    @Override
    public void receivedMessage(Message message) {
        Log.i(TAG, String.format("Received message: '%s'",message.body));
        mDataStore.createOrUpdateMessageWithProtocolMessage(message);
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
