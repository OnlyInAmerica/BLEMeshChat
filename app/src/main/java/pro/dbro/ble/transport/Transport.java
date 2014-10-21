package pro.dbro.ble.transport;

import java.util.ArrayDeque;

import pro.dbro.ble.protocol.Identity;
import pro.dbro.ble.protocol.Message;
import pro.dbro.ble.protocol.Protocol;

/**
 * Created by davidbrodsky on 10/20/14.
 */
public abstract class Transport {

    protected Identity mIdentity;
    protected Protocol mProtocol;
    protected TransportEventCallback mCallback;
    protected TransportDataProvider mDataProvider;

    public static interface TransportEventCallback {

        public void becameAvailable(Identity identity);
        public void becameUnavailable(Identity identity);

        public void sentIdentity(Identity identity);
        public void sentMessage(Message message);

        public void receivedIdentity(Identity identity);
        public void receivedMessage(Message message);

    }

    public static interface TransportDataProvider {
        // TODO: take Identity, not byte[] publicKey
        public ArrayDeque<Message> getMessagesForIdentity(byte[] recipientPublicKey, int maxMessages);
        public ArrayDeque<Identity> getIdentitiesForIdentity(byte[] recipientPublicKey, int maxIdentities);
    }

    public Transport(Identity identity, Protocol protocol, TransportDataProvider dataProvider) {
        mIdentity = identity;
        mProtocol = protocol;
        mDataProvider = dataProvider;
    }

    public void setTransportCallback(TransportEventCallback callback) {
        mCallback = callback;
    }

    public abstract void makeAvailable();

    public abstract void sendMessage(Message message);

    public abstract void makeUnavailable();

}
