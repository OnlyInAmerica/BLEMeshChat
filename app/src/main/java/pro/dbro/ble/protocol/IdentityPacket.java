package pro.dbro.ble.protocol;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Date;

/**
 * An identity for a remote peer
 * Created by davidbrodsky on 10/13/14.
 */
public class IdentityPacket {

    public final byte[] publicKey;
    public final Date   dateSeen;
    public final String alias;
    public final byte[] rawPacket;

    public IdentityPacket(@NonNull final byte[] publicKey, @Nullable String alias, @NonNull Date dateSeen,
                          @NonNull final byte[] rawPacket) {
        // dateSeen is allowed null because it's meaningless for OwnedIdentities
        this.publicKey  = publicKey;
        this.alias      = alias == null ? null : alias.trim();
        this.dateSeen   = dateSeen;
        this.rawPacket  = rawPacket;
    }
}
