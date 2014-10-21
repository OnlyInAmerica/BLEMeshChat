package pro.dbro.ble.protocol;

import android.support.annotation.NonNull;

import java.util.Date;

/**
 * An identity for a remote peer
 * Created by davidbrodsky on 10/13/14.
 */
public class Identity {

    public final byte[] publicKey;
    public final Date   dateSeen;
    public final String alias;
    public final byte[] rawPacket;

    public Identity(@NonNull final byte[] publicKey, @NonNull String alias, Date dateSeen,
                    @NonNull final byte[] rawPacket) {
        // dateSeen is allowed null because it's meaningless for OwnedIdentities
        this.publicKey  = publicKey;
        this.alias      = alias;
        this.dateSeen   = dateSeen;
        this.rawPacket  = rawPacket;
    }
}
