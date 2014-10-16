package pro.dbro.ble.protocol;

import android.support.annotation.NonNull;

import java.util.Date;

/**
 * Created by davidbrodsky on 10/15/14.
 */
public class Message {

    public Identity sender;
    public String body;
    public Date authoredDate;
    public byte[] signature;

    public Message(@NonNull final byte[] publicKey, @NonNull byte[] signature, @NonNull String alias, @NonNull Date dateSeen,
                   @NonNull String body) {
        this.body      = body;
        this.signature = signature;
        authoredDate   = dateSeen;
        sender         = new Identity(publicKey, alias, dateSeen);
    }
}
