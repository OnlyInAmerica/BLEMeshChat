package pro.dbro.ble.protocol;

import android.support.annotation.NonNull;

/**
 * An Identity for the local peer
 * Created by davidbrodsky on 10/13/14.
 */
public class OwnedIdentityPacket extends IdentityPacket {

    public final byte[] secretKey;

    public OwnedIdentityPacket(@NonNull final byte[] secretKey, @NonNull final byte[] publicKey,
                               @NonNull String alias, byte[] rawPacket) {
        super(publicKey, alias, null, rawPacket);
        this.secretKey = secretKey;
    }
}
