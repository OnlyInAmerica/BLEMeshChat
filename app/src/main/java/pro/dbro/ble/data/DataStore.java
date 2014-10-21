package pro.dbro.ble.data;

import android.content.Context;
import android.support.annotation.NonNull;

import java.util.List;

import pro.dbro.ble.data.model.Message;
import pro.dbro.ble.data.model.MessageCollection;
import pro.dbro.ble.data.model.Peer;
import pro.dbro.ble.protocol.Identity;

/**
 * Data persistence layer. Any data storage mechanism
 * needs to implement this interface.
 *
 * Created by davidbrodsky on 10/20/14.
 */
public abstract class DataStore {

    protected Context mContext;

    public DataStore(Context context) {
        mContext = context.getApplicationContext();
    }

    public abstract Peer createLocalPeerWithAlias(@NonNull String alias);

    public abstract Peer getPrimaryLocalPeer();

    public abstract MessageCollection getOutgoingMessagesForPeer(@NonNull Peer recipient);

    public abstract Peer createRemotePeerWithProtocolIdentity(@NonNull Identity identity);

    public abstract Message createMessageWithProtocolMessage(@NonNull pro.dbro.ble.protocol.Message protocolMessage);

    public abstract Message getMessageById(int id);

    public abstract Peer getPeerByPubKey(@NonNull byte[] public_key);

    public abstract Peer getPeerById(int id);

}
