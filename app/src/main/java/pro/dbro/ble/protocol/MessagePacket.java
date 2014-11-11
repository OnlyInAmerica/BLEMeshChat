package pro.dbro.ble.protocol;

import android.support.annotation.NonNull;

import java.util.Date;

/**
 * Created by davidbrodsky on 10/15/14.
 */
public class MessagePacket {

    final public IdentityPacket sender;
    final public String body;
    final public Date authoredDate;
    final public byte[] signature;
    final public byte[] replySig;
    final public byte[] rawPacket;

    public MessagePacket(@NonNull final byte[] publicKey, @NonNull byte[] signature, @NonNull byte[] replySig, @NonNull Date authoredDate,
                         @NonNull String body, @NonNull byte[] rawPacket) {
        this.body         = body;
        this.signature    = signature;
        this.replySig     = replySig;
        this.rawPacket    = rawPacket;
        this.authoredDate = authoredDate;
        sender            = new IdentityPacket(publicKey, null, null, null); // We don't have the sender's full identity response
    }

    public MessagePacket(@NonNull IdentityPacket sender, @NonNull byte[] signature, @NonNull byte[] replySig, @NonNull String body, @NonNull byte[] rawPacket, @NonNull Date authoredDate) {
        this.body         = body.trim();
        this.signature    = signature;
        this.replySig     = replySig;
        this.rawPacket    = rawPacket;
        this.authoredDate = authoredDate;
        this.sender       = sender;
    }

    public static MessagePacket attachIdentityToMessage(@NonNull MessagePacket message, @NonNull IdentityPacket identity) {
        return new MessagePacket(identity, message.signature, message.replySig, message.body, message.rawPacket, message.authoredDate);
    }
}
