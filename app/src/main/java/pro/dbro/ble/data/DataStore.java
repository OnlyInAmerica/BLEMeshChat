package pro.dbro.ble.data;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import pro.dbro.ble.data.model.Message;
import pro.dbro.ble.data.model.MessageCollection;
import pro.dbro.ble.data.model.Peer;
import pro.dbro.ble.protocol.IdentityPacket;
import pro.dbro.ble.protocol.MessagePacket;
import pro.dbro.ble.protocol.Protocol;

/**
 * Data persistence layer. Any data storage mechanism
 * needs to implement this interface.
 *
 * Created by davidbrodsky on 10/20/14.
 */
public abstract class DataStore {

    protected Context mContext;

    public DataStore(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    public abstract Peer createLocalPeerWithAlias(@NonNull String alias, @Nullable Protocol protocol);

    public abstract Peer getPrimaryLocalPeer();

    public abstract MessageCollection getOutgoingMessagesForPeer(@NonNull Peer recipient);

    public abstract MessageCollection getRecentMessages();

    public abstract Peer createOrUpdateRemotePeerWithProtocolIdentity(@NonNull IdentityPacket identityPacket);

    public abstract Message createOrUpdateMessageWithProtocolMessage(@NonNull MessagePacket protocolMessagePacket);

    public abstract Message getMessageBySignature(@NonNull byte[] signature);

    public abstract Message getMessageById(int id);

    public abstract Peer getPeerByPubKey(@NonNull byte[] publicKey);

    public abstract Peer getPeerById(int id);

}
