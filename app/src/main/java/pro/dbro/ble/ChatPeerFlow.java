package pro.dbro.ble;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import pro.dbro.airshare.session.Peer;
import pro.dbro.ble.data.DataStore;
import pro.dbro.ble.data.model.DataUtil;
import pro.dbro.ble.data.model.Message;
import pro.dbro.ble.data.model.MessageCollection;
import pro.dbro.ble.protocol.IdentityPacket;
import pro.dbro.ble.protocol.MessagePacket;
import pro.dbro.ble.protocol.NoDataPacket;
import pro.dbro.ble.protocol.OwnedIdentityPacket;
import pro.dbro.ble.protocol.Protocol;
import timber.log.Timber;

/**
 * This class orchestrates the flow between two ChatApp Peers, handing network requests and
 * updating the {@link pro.dbro.ble.data.DataStore}. The client of this class may use
 * {@link ChatPeerFlow.Callback} to update their UI or in-memory application state.
 *
 * The general gist of the flow:
 *
 * 1) Client peer writes identity
 * 2) Client peer waits for host identity
 * 3) Client peer writes outgoing messages
 * 4) Client peer waits for incoming messages
 * Created by davidbrodsky on 4/16/15.
 */
public class ChatPeerFlow {

    public static class UnexpectedDataException extends Exception {
        public UnexpectedDataException(String detailMessage) {
            super(detailMessage);
        }
    }

    /** Entity responsible for sending data to a peer */
    public static interface DataOutlet {
        public void sendData(Peer peer, byte[] data);
    }

    public static interface Callback {

        public static enum ConnectionStatus { CONNECTED, DISCONNECTED }

        public void onAppPeerStatusUpdated(@NonNull ChatPeerFlow flow,
                                           @NonNull pro.dbro.ble.data.model.Peer peer,
                                           @NonNull ConnectionStatus status);

        public void onMessageSent(@NonNull ChatPeerFlow flow,
                                  @NonNull Message message,
                                  @NonNull pro.dbro.ble.data.model.Peer recipient);

        public void onMessageReceived(@NonNull ChatPeerFlow flow,
                                      @NonNull Message message,
                                      @Nullable pro.dbro.ble.data.model.Peer sender);

    }

    private static final int MESSAGES_PER_RESPONSE = 50;
    private static final int IDENTITIES_PER_RESPONSE = 10;
    public static enum State { CLIENT_WRITE_ID, HOST_WRITE_ID, CLIENT_WRITE_MSGS, HOST_WRITE_MSGS }

    private State mState = State.CLIENT_WRITE_ID;
    private OwnedIdentityPacket mLocalIdentity;
    private Peer mRemoteAirSharePeer;
    private Protocol mProtocol;
    private DataStore mDataStore;
    private DataOutlet mOutlet;
    private IdentityPacket mRemoteIdentity;
    private Callback mCallback;
    private ArrayDeque<MessagePacket> mMessageOutbox = new ArrayDeque<>();
    private ArrayDeque<IdentityPacket> mIdentityOutbox = new ArrayDeque<>();

    private boolean mPeerIsHost;
    private boolean mIsComplete = false;
    private boolean mFetchedMessages = false;
    private boolean mFetchedIdentities = false;
    private boolean mGotRemotePeerIdentity = false;

    public ChatPeerFlow(DataStore dataStore,
                        Protocol protocol,
                        DataOutlet outlet,
                        Peer remotePeer,
                        boolean peerIsHost,
                        Callback callback) {

        mRemoteAirSharePeer = remotePeer;
        mOutlet = outlet;
        mProtocol = protocol;
        mDataStore = dataStore;
        mLocalIdentity = (OwnedIdentityPacket) dataStore.getPrimaryLocalPeer().getIdentity();
        mPeerIsHost = peerIsHost;
        mCallback = callback;

        // Client initiates flow
        if (mPeerIsHost)
            sendIdentity();
    }

    public boolean isComplete() {
        return mIsComplete;
    }

    public Peer getRemoteAirSharePeer() {
        return mRemoteAirSharePeer;
    }

    public void queueMessage(MessagePacket message) {
        mMessageOutbox.add(message);
    }

    /**
     * Called when data is acknowledged as sent to the peer passed to this instance's constructor
     * @return whether this flow is complete and should not receive further events.
     */
    public boolean onDataSent(byte[] data) throws UnexpectedDataException {
        // When data is ack'd we should be in a local-peer writing state
        if ((!mPeerIsHost && (mState == State.CLIENT_WRITE_ID || (mState == State.CLIENT_WRITE_MSGS && !mIsComplete))) ||
            (mPeerIsHost && (mState == State.HOST_WRITE_ID || (mState == State.HOST_WRITE_MSGS && !mIsComplete)))) {

            throw new IllegalStateException(String.format("onDataSent invalid state %s for local as %s", mState, mPeerIsHost ? "client" : "host"));

        }

        Timber.d("Sent data %s", DataUtil.bytesToHex(data));

        byte type = mProtocol.getPacketType(data);

        // TODO : Perhaps we should cache last sent item to avoid deserializing bytes we've
        // just serialized in sendData
        switch (mState) {
            case HOST_WRITE_ID:
            case CLIENT_WRITE_ID:

                switch(type) {
                    case IdentityPacket.TYPE:

                        IdentityPacket sentIdPkt = mProtocol.deserializeIdentity(data);
                        mDataStore.createOrUpdateRemotePeerWithProtocolIdentity(sentIdPkt);
                        // We can only report the identity sent once we know the peer's identity
                        // We also always want to send our own identity first
                        if (mRemoteIdentity != null) {
                            Timber.d("Marked identity %s delivered to %s", sentIdPkt.alias, mRemoteIdentity.alias);
                            mDataStore.markIdentityDeliveredToPeer(sentIdPkt, mRemoteIdentity);
                        }

                        mIdentityOutbox.poll();

                        sendAsAppropriate();
                        break;

                    case NoDataPacket.TYPE:

                        incrementStateAndSendAsAppropriate();
                        break;

                    default:
                        throw new UnexpectedDataException(String.format("Expected IdentityPacket (type %d). Got type %d", IdentityPacket.TYPE, type));

                }
                break;

            case HOST_WRITE_MSGS:
            case CLIENT_WRITE_MSGS:

                switch(type) {
                    case MessagePacket.TYPE:

                        MessagePacket msgPkt = mProtocol.deserializeMessageWithIdentity(data, mRemoteIdentity);
                        Message msg = mDataStore.createOrUpdateMessageWithProtocolMessage(msgPkt);
                        // Mark incoming messages as delivered to sender
                        mDataStore.markMessageDeliveredToPeer(msgPkt, mRemoteIdentity);
                        mCallback.onMessageSent(this, msg, mDataStore.getPeerByPubKey(mRemoteIdentity.publicKey));

                        mMessageOutbox.poll();

                        sendAsAppropriate();
                        break;

                    case NoDataPacket.TYPE:

                        incrementStateAndSendAsAppropriate();
                        break;

                    default:
                        throw new UnexpectedDataException(String.format("Expected MessagePacket (type %d). Got type %d", MessagePacket.TYPE, type));

                }

                break;

            default:
                Timber.e("Flow received unexpected response from client peer");
        }
        return mIsComplete;
    }

    /**
     * Called when data is received from the peer passed to this instance's constructor
     * @return whether this flow is complete and should not receive further events.
     */
    public boolean onDataReceived(byte[] data) throws UnexpectedDataException {
        // When data comes in we should be in a remote-peer writing state
        if ((!mPeerIsHost && (mState == State.HOST_WRITE_ID || (mState == State.HOST_WRITE_MSGS && !mIsComplete))) ||
            (mPeerIsHost && (mState == State.CLIENT_WRITE_ID || (mState == State.CLIENT_WRITE_MSGS && !mIsComplete)))) {

            throw new IllegalStateException(String.format("onDataReceived invalid state %s for local as %s", mState, mPeerIsHost ? "client" : "host"));

        }

        //Timber.d("Received data %s", DataUtil.bytesToHex(data));

        byte type = mProtocol.getPacketType(data);

        switch (mState) {
            case HOST_WRITE_ID:
            case CLIENT_WRITE_ID:

                switch(type) {
                    case IdentityPacket.TYPE:

                        mRemoteIdentity = mProtocol.deserializeIdentity(data);
                        Timber.d("Got remote identity for %s", mRemoteIdentity.alias);
                        pro.dbro.ble.data.model.Peer remotePeer = mDataStore.createOrUpdateRemotePeerWithProtocolIdentity(mRemoteIdentity);
                        // Only treat first identity as that of connected peer
                        if (!mGotRemotePeerIdentity) {
                            mCallback.onAppPeerStatusUpdated(this, remotePeer, Callback.ConnectionStatus.CONNECTED);
                            mGotRemotePeerIdentity = true;
                        }
                        break;

                    case NoDataPacket.TYPE:

                        Timber.d("Received identity NoData");
                        incrementStateAndSendAsAppropriate();
                        break;

                    default:

                        throw new UnexpectedDataException(String.format("Expected IdentityPacket (type %d). Got type %d", IdentityPacket.TYPE, type));
                }

                break;

            case HOST_WRITE_MSGS:
            case CLIENT_WRITE_MSGS:

                switch (type) {
                    case MessagePacket.TYPE:

                        MessagePacket msgPkt = mProtocol.deserializeMessageWithIdentity(data, mRemoteIdentity);
                        Timber.d("Received msg %s", msgPkt.body);

                        // Mark incoming messages as delivered to sender

                        boolean isNewMessage = true;
                        Message existingMessage = mDataStore.getMessageBySignature(msgPkt.signature);
                        if (existingMessage != null) {
                            isNewMessage = false;
                            existingMessage.close();
                        }

                        // TODO : Allow updating a message?
                        Message msg = mDataStore.createOrUpdateMessageWithProtocolMessage(msgPkt);
                        mDataStore.markMessageDeliveredToPeer(msgPkt, mRemoteIdentity);

                        if (isNewMessage)
                            mCallback.onMessageReceived(this, msg, mDataStore.getPeerByPubKey(mRemoteIdentity.publicKey));

                        break;

                    case NoDataPacket.TYPE:

                        Timber.d("Received msg NoData");
                        incrementStateAndSendAsAppropriate();
                        break;

                    default:

                        throw new UnexpectedDataException(String.format("Expected MessagePacket (type %d). Got type %d", MessagePacket.TYPE, type));

                }
                break;

            default:
                Timber.e("Flow received unexpected response from client peer");
        }
        return mIsComplete;
    }

    private void sendIdentity() {
        if (!mFetchedIdentities) {

            // If we're the client, we're initiating the identity flow, and we won't have the remote identity yet
            mIdentityOutbox.addAll(getIdentitiesForIdentity(mRemoteIdentity == null ? null : mRemoteIdentity.publicKey,
                    IDENTITIES_PER_RESPONSE));
            mFetchedIdentities = true;
        }

        Timber.d("Send identity %s", mIdentityOutbox.size() == 0 ? "NoData" : "");
        mOutlet.sendData(mRemoteAirSharePeer,
                         mIdentityOutbox.size() == 0 ?
                            mProtocol.serializeNoDataPacket(mLocalIdentity).rawPacket :
                            mIdentityOutbox.peek().rawPacket);
    }

    private void sendMessage() {
        if (!mFetchedMessages) {
            mMessageOutbox.addAll(getMessagesForIdentity(mRemoteIdentity.publicKey, MESSAGES_PER_RESPONSE));
            mFetchedMessages = true;
        }

        Timber.d("Send message %s", mMessageOutbox.size() == 0 ? "NoData" : "");
        mOutlet.sendData(mRemoteAirSharePeer,
                         mMessageOutbox.size() == 0 ?
                            mProtocol.serializeNoDataPacket(mLocalIdentity).rawPacket :
                            mMessageOutbox.peek().rawPacket);
    }

    private void incrementStateAndSendAsAppropriate() {
        if (mState == State.HOST_WRITE_MSGS) {
            Timber.d("ChatPeerFlow complete!");
            mIsComplete = true;
            return;
        }

        mState = State.values()[mState.ordinal() + 1];
        Timber.d("ChatPeerFlow New State : %s", mState);
        sendAsAppropriate();
    }

    private void sendAsAppropriate() {

        switch (mState) {
            case CLIENT_WRITE_ID:
                if (mPeerIsHost) sendIdentity();
                break;

            case HOST_WRITE_ID:
                if (!mPeerIsHost) sendIdentity();
                break;

            case CLIENT_WRITE_MSGS:
                if (mPeerIsHost) sendMessage();
                break;

            case HOST_WRITE_MSGS:
                if (!mPeerIsHost) sendMessage();
                break;
        }
    }

    /**
     * Return a queue of message packets for delivery to remote identity with given public key.
     *
     * If recipientPublicKey is null, queues most recent messages
     */
    private ArrayDeque<MessagePacket> getMessagesForIdentity(@Nullable byte[] recipientPublicKey, int maxMessages) {
        ArrayDeque<MessagePacket> messagePacketQueue = new ArrayDeque<>();

        if (recipientPublicKey != null) {
            // Get messages not delievered to peer
            pro.dbro.ble.data.model.Peer recipient = mDataStore.getPeerByPubKey(recipientPublicKey);
            List<MessagePacket> messages = mDataStore.getOutgoingMessagesForPeer(recipient, maxMessages);

            if (messages == null || messages.size() == 0) {
                Timber.d("Got no messages for peer with pub key " + DataUtil.bytesToHex(recipientPublicKey));
            } else {
                messagePacketQueue.addAll(messages);
            }
        } else {
            // Get most recent messages
            MessageCollection recentMessages = mDataStore.getRecentMessages();
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
     * If recipientPublicKey is null, or no messages undelivered for recipient,
     * the user identity will be queued. As such this method will never return a null
     * or empty queue. Thus it should only be called once per flow and should not
     * be used as an indication of whether identity transmission with a peer is complete.
     */
    private ArrayDeque<IdentityPacket> getIdentitiesForIdentity(@Nullable byte[] recipientPublicKey, int maxIdentities) {
        List<IdentityPacket> identities = null;
        ArrayDeque<IdentityPacket> identityPacketQueue = new ArrayDeque<>();
        if (recipientPublicKey != null) {
            // We have a public key for the remote peer, fetch undelivered identities
            pro.dbro.ble.data.model.Peer recipient = mDataStore.getPeerByPubKey(recipientPublicKey);
            identities = mDataStore.getOutgoingIdentitiesForPeer(recipient, maxIdentities);
        }

        if (identities == null || identities.size() == 0) {
            Timber.d("Got no identities to send for peer %s. Sending own identity", recipientPublicKey == null ? "" : "with pub key " + DataUtil.bytesToHex(recipientPublicKey).substring(2, 6));
            // For now, at least send our identity
            if (identities == null) identities = new ArrayList<>(1);
            identities.add(mDataStore.getPrimaryLocalPeer().getIdentity());
        }
        identityPacketQueue.addAll(identities);

        return identityPacketQueue;
    }

}
