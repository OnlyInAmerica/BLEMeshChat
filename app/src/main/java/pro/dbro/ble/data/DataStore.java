package pro.dbro.ble.data;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.List;

import pro.dbro.ble.data.model.Message;
import pro.dbro.ble.data.model.MessageCollection;
import pro.dbro.ble.data.model.Peer;
import pro.dbro.ble.protocol.Identity;
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

    public abstract Peer createOrUpdateRemotePeerWithProtocolIdentity(@NonNull Identity identity);

    public abstract Message createOrUpdateMessageWithProtocolMessage(@NonNull pro.dbro.ble.protocol.Message protocolMessage);

    public abstract Message getMessageBySignature(@NonNull byte[] signature);

    public abstract Message getMessageById(int id);

    public abstract Peer getPeerByPubKey(@NonNull byte[] publicKey);

    public abstract Peer getPeerById(int id);

}
