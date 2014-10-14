package pro.dbro.ble.crypto;

import android.support.annotation.NonNull;

/**
 * Created by davidbrodsky on 10/13/14.
 */
public class KeyPair {

    public final byte[] secretKey;
    public final byte[] publicKey;
    public final String alias;

    public KeyPair(@NonNull final byte[] secretKey, @NonNull final byte[] publicKey, @NonNull String alias) {
        this.secretKey = secretKey;
        this.publicKey = publicKey;
        this.alias = alias;
    }
}
