package pro.dbro.ble;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

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
    public void receivedMessageFromIdentity(MessagePacket messagePacket, IdentityPacket identityPacket) {
        Log.i(TAG, String.format("Received message: '%s' with sig '%s' ", messagePacket.body, DataUtil.bytesToHex(messagePacket.signature).substring(0, 3)));
        mDataStore.createOrUpdateMessageWithProtocolMessage(messagePacket).close();
        if (identityPacket != null) {
            mDataStore.markMessageDeliveredToPeer(messagePacket, identityPacket); // Never deliver this message back to peer it came from
        } else {
            Log.w(TAG, String.format("Got no identity for message %s. Cannot record receipt from this identity", messagePacket.body));
        }
    }

    // </editor-fold desc="Private API">
}
