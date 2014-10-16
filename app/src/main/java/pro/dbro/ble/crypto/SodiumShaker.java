package pro.dbro.ble.crypto;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.abstractj.kalium.NaCl;

import java.util.Arrays;
import java.util.Date;

import pro.dbro.ble.protocol.OwnedIdentity;

/**
 * Wrapper around libsodium functions.
 *
 * Created by davidbrodsky on 10/13/14.
 */
public class SodiumShaker {
    private static final String TAG = "Identity";

    public static final int crypto_sign_PUBLICKEYBYTES = 32;
    private static final int crypto_sign_SECRETKEYBYTES = 64;
    public static final int crypto_sign_BYTES = 64;

    static {
        // Load native libraries
        NaCl.sodium();
    }

    public static OwnedIdentity generateKeyPairForAlias(@NonNull String alias) {
        byte[] pk = new byte[crypto_sign_PUBLICKEYBYTES];
        byte[] sk = new byte[crypto_sign_SECRETKEYBYTES];

        org.abstractj.kalium.Sodium.crypto_sign_ed25519_keypair(pk, sk);
        return new OwnedIdentity(sk, pk, alias, new Date());
    }

    @Nullable
    public static byte[] signMessage(@NonNull OwnedIdentity keypair, @NonNull byte[] message) {
        // TODO : recompile jni wrapper with detached sign method exposed public
        // to sealed_message allocation
        // see :http://doc.libsodium.org/public-key_cryptography/public-key_signatures.html
        byte[] sealed_message = new byte[crypto_sign_BYTES + message.length];
        int[] sealed_message_len = new int[0];

        org.abstractj.kalium.Sodium.crypto_sign_ed25519(sealed_message, sealed_message_len,
                message, message.length, keypair.secretKey);
        return sealed_message;
    }

    public static boolean verifyMessage(@NonNull byte[] pubkey, @NonNull byte[] signedMessage, @NonNull byte[] expectedMessage) {
        // Verify signature
        byte[] unsealed_message = new byte[expectedMessage.length];
        int[] message_length = new int[0];

        if (org.abstractj.kalium.Sodium.crypto_sign_ed25519_open(unsealed_message,
                message_length,
                signedMessage,
                signedMessage.length, pubkey) != 0) {
        } else {
            return Arrays.equals(unsealed_message, expectedMessage);
        }
        return false;
    }
}
