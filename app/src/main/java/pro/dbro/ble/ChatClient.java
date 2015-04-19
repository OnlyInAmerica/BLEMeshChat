package pro.dbro.ble;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.HashMap;

import pro.dbro.airshare.app.AirShareService;
import pro.dbro.airshare.transport.Transport;
import pro.dbro.ble.data.ContentProviderStore;
import pro.dbro.ble.data.DataStore;
import pro.dbro.ble.data.model.DataUtil;
import pro.dbro.ble.data.model.Message;
import pro.dbro.ble.data.model.Peer;
import pro.dbro.ble.protocol.BLEProtocol;
import pro.dbro.ble.protocol.MessagePacket;
import pro.dbro.ble.protocol.OwnedIdentityPacket;
import pro.dbro.ble.protocol.Protocol;
import pro.dbro.ble.ui.Notification;
import pro.dbro.ble.ui.activities.LogConsumer;
import timber.log.Timber;

/**
 * Created by davidbrodsky on 10/13/14.
 */
public class ChatClient implements AirShareService.Callback,
                                   ChatPeerFlow.DataOutlet, ChatPeerFlow.Callback {

    public static interface Callback {
        /** Client should not invoke remotePeer#close() */
        public void onAppPeerStatusUpdated(@NonNull Peer remotePeer,
                                           @NonNull ConnectionStatus status);
    }

    public static final String TAG = "ChatApp";
    public static final String AIRSHARE_SERVICE_NAME = "BLEMeshChat";

    private Context   mContext;
    private DataStore mDataStore;
    private Protocol  mProtocol;
    private AirShareService.ServiceBinder mAirShareServiceBinder;
    private Callback mCallback;

    private HashMap<pro.dbro.airshare.session.Peer, ChatPeerFlow> mFlows = new HashMap<>();

    /** AirShare Peer -> BLEMeshChat Peer id */
    private BiMap<pro.dbro.airshare.session.Peer, Integer> mConnectedPeers = HashBiMap.create();

    private LogConsumer mLogger;

    // <editor-fold desc="Public API">

    public ChatClient(@NonNull Context context) {
        mContext = context;

        mProtocol  = new BLEProtocol();
        mDataStore = new ContentProviderStore(context);
    }

    public void setAirShareServiceBinder(AirShareService.ServiceBinder binder) {
        mAirShareServiceBinder = binder;
        mAirShareServiceBinder.setCallback(this);
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    // <editor-fold desc="Identity & Availability">

    public void setLogConsumer(LogConsumer logger) {
        mLogger = logger;

//        if (mTransport != null) mTransport.setLogConsumer(mLogger);
    }

    public void makeAvailable() {
        if (mDataStore.getPrimaryLocalPeer() == null) {
            Timber.e("No primary Identity. Cannot make client available");
            return;
        }

        if (mAirShareServiceBinder == null) {
            Timber.e("No AirShareBinder set. Cannot make available");
            return;
        }

        mAirShareServiceBinder.advertiseLocalUser();
        mAirShareServiceBinder.scanForOtherUsers();
    }

    public void makeUnavailable() {
        if (mAirShareServiceBinder == null) {
            Timber.e("No AirShareBinder set. Cannot make unavailable");
            return;
        }

        mAirShareServiceBinder.stop();
    }

    public Peer getPrimaryLocalPeer() {
        return mDataStore.getPrimaryLocalPeer();
    }

    public Peer createPrimaryIdentity(String alias) {
        // TODO Test if this should be moved to background thread and async call?
        return mDataStore.createLocalPeerWithAlias(alias, mProtocol);
    }

    // </editor-fold desc="Identity & Availability">

    // <editor-fold desc="Messages">

    public void sendPublicMessageFromPrimaryIdentity(String body) {
        MessagePacket messagePacket = mProtocol.serializeMessage((OwnedIdentityPacket) getPrimaryLocalPeer().getIdentity(), body);
        mDataStore.createOrUpdateMessageWithProtocolMessage(messagePacket).close();
        // TODO : Send to connected peers. Future peers will get message during flow
        if (mAirShareServiceBinder != null) {

            for (pro.dbro.airshare.session.Peer peer : mConnectedPeers.keySet()) {
                ChatPeerFlow flow = mFlows.get(peer);
                // If we're actively flowing with a peer, add the message to that flow
                // else, send immediately
                if (flow != null && !flow.isComplete())
                    flow.queueMessage(messagePacket);
                else
                    mAirShareServiceBinder.send(messagePacket.rawPacket, peer);
            }

        }
    }

    // </editor-fold desc="Messages">

    public DataStore getDataStore() {
        return mDataStore;
    }

    // </editor-fold desc="Public API">

    // <editor-fold desc="Private API">

    @Override
    public void onAppPeerStatusUpdated(@NonNull ChatPeerFlow flow,
                                       @NonNull Peer remotePeer,
                                       @NonNull ConnectionStatus status) {

        Timber.d("%s %s", remotePeer.getAlias(), status == ConnectionStatus.CONNECTED ? "connected" : "disconnected");
        if (mCallback != null)
            mCallback.onAppPeerStatusUpdated(remotePeer, status);

        if (!mAirShareServiceBinder.isActivityReceivingMessages())
            Notification.displayPeerAvailableNotification(mContext, remotePeer, status == ConnectionStatus.CONNECTED);

        switch (status) {
            case CONNECTED:
                mConnectedPeers.put(flow.getRemoteAirSharePeer(), remotePeer.getId());
                break;

            case DISCONNECTED:
                mConnectedPeers.remove(flow.getRemoteAirSharePeer());
                break;
        }
    }

    @Override
    public void onMessageSent(@NonNull ChatPeerFlow flow, @NonNull Message message, @NonNull Peer recipient) {
        Timber.d("Sent message: '%s'", message.getBody());
        // TODO : Might be unnecessary
        message.close();
    }

    @Override
    public void onMessageReceived(@NonNull ChatPeerFlow flow, @NonNull Message message, Peer sender) {
        Timber.d("Received message: '%s' with sig '%s' ", message.getBody(), DataUtil.bytesToHex(message.getSignature()).substring(0, 3));

        // We don't check that mAirShareServiceBinder is not null because this callback is provoked
        // by the binder callbacks

        // Send message notification if it's a new message and no Activity is reported active
        if (!mAirShareServiceBinder.isActivityReceivingMessages()) {
            Notification.displayMessageNotification(mContext, message, sender);
            message.close();
        }
    }

    @Override
    public void onDataRecevied(byte[] data, pro.dbro.airshare.session.Peer sender, Exception exception) {
        ChatPeerFlow flow = mFlows.get(sender);

        if (flow == null) {
            Timber.w("No flow for %s", sender.getAlias());
            return;
        }

        try {
            flow.onDataReceived(data);
        } catch (ChatPeerFlow.UnexpectedDataException e) {
            Timber.e(e, "Error processing received data");
        }
    }

    @Override
    public void onDataSent(byte[] data, pro.dbro.airshare.session.Peer recipient, Exception exception) {
        ChatPeerFlow flow = mFlows.get(recipient);

        if (flow == null) {
            Timber.w("No flow for %s", recipient.getAlias());
            return;
        }

        try {
            flow.onDataSent(data);
        } catch (ChatPeerFlow.UnexpectedDataException e) {
            Timber.e(e, "Error processing sent data");
        }
    }

    @Override
    public void onPeerStatusUpdated(pro.dbro.airshare.session.Peer peer, Transport.ConnectionStatus newStatus, boolean peerIsHost) {
        if (newStatus == Transport.ConnectionStatus.CONNECTED) {
            mConnectedPeers.put(peer, null); // We will add the BLEMeshChat peer id after identity is received
            Timber.d("Beginning flow with %s as %s", peer.getAlias(), peerIsHost ? "host" : "client");
            mFlows.put(peer, new ChatPeerFlow(mDataStore, mProtocol, this, peer, peerIsHost, this));
        }
        else if (newStatus == Transport.ConnectionStatus.DISCONNECTED) {

            if (!mConnectedPeers.containsKey(peer) || mConnectedPeers.get(peer) == null) {
                if (mConnectedPeers.containsKey(peer)) mConnectedPeers.remove(peer);
                Timber.w("Cannot report peer %s disconnected, no connection record", peer.getAlias());
                return;
            }

            int blePeerId = mConnectedPeers.get(peer);
            Peer remotePeer = mDataStore.getPeerById(blePeerId);
            onAppPeerStatusUpdated(mFlows.get(peer), remotePeer, ConnectionStatus.DISCONNECTED);
        }
    }

    @Override
    public void sendData(pro.dbro.airshare.session.Peer peer, byte[] data) {
        if(mAirShareServiceBinder == null) {
            Timber.e("AirShare Service binder is null! Cannot send data");
            return;
        }
        mAirShareServiceBinder.send(data, peer);
    }

    // </editor-fold desc="Private API">
}
