package pro.dbro.ble.protocol;

import android.support.annotation.NonNull;

/**
 * An Identity for the local peer
 * Created by davidbrodsky on 10/13/14.
 */
public class OwnedIdentity extends Identity{

    public final byte[] secretKey;

    public OwnedIdentity(@NonNull final byte[] secretKey, @NonNull final byte[] publicKey,
                         @NonNull String alias, byte[] rawPacket) {
        super(publicKey, alias, null, null);
        this.secretKey = secretKey;
    }
}
