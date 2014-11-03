package pro.dbro.ble.transport;

import java.util.ArrayDeque;

import pro.dbro.ble.protocol.IdentityPacket;
import pro.dbro.ble.protocol.MessagePacket;
import pro.dbro.ble.protocol.Protocol;

/**
 * Created by davidbrodsky on 10/20/14.
 */
public abstract class Transport {

    protected IdentityPacket mIdentityPacket;
    protected Protocol mProtocol;
    protected TransportEventCallback mCallback;
    protected TransportDataProvider mDataProvider;

    public static interface TransportEventCallback {

        public void becameAvailable(IdentityPacket identityPacket);
        public void becameUnavailable(IdentityPacket identityPacket);

        public void sentIdentity(IdentityPacket payloadIdentity, IdentityPacket recipientIdentity);
        public void sentMessage(MessagePacket messagePacket, IdentityPacket recipientIdentity);

        public void receivedIdentity(IdentityPacket identityPacket);
        public void receivedMessage(MessagePacket messagePacket);

    }

    public static interface TransportDataProvider {
        // TODO: take Identity, not byte[] publicKey
        public ArrayDeque<MessagePacket> getMessagesForIdentity(byte[] recipientPublicKey, int maxMessages);
        public ArrayDeque<IdentityPacket> getIdentitiesForIdentity(byte[] recipientPublicKey, int maxIdentities);
    }

    public Transport(IdentityPacket identityPacket, Protocol protocol, TransportDataProvider dataProvider) {
        mIdentityPacket = identityPacket;
        mProtocol = protocol;
        mDataProvider = dataProvider;
    }

    public void setTransportCallback(TransportEventCallback callback) {
        mCallback = callback;
    }

    public abstract void makeAvailable();

    public abstract void sendMessage(MessagePacket messagePacket);

    public abstract void makeUnavailable();

}
