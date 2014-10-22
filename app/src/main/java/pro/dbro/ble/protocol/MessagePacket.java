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


    public MessagePacket(@NonNull final byte[] publicKey, @NonNull byte[] signature, @NonNull byte[] replySig, @NonNull String alias, @NonNull Date dateSeen,
                         @NonNull String body, @NonNull byte[] rawPacket) {
        this.body      = body;
        this.signature = signature;
        this.replySig  = replySig;
        this.rawPacket = rawPacket;
        authoredDate   = dateSeen;
        sender         = new IdentityPacket(publicKey, alias, dateSeen, null); // We don't have the sender's full identity response
    }

    public MessagePacket(@NonNull IdentityPacket sender, @NonNull byte[] signature, @NonNull byte[] replySig, @NonNull String body, @NonNull byte[] rawPacket) {
        this.body      = body;
        this.signature = signature;
        this.replySig  = replySig;
        this.rawPacket = rawPacket;
        authoredDate   = sender.dateSeen;
        this.sender    = sender;
    }
}
