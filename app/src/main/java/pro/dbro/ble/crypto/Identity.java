package pro.dbro.ble.crypto;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.abstractj.kalium.NaCl;
import org.abstractj.kalium.Sodium;

import java.util.Arrays;

/**
 * Created by davidbrodsky on 10/13/14.
 */
public class Identity {
    private static final String TAG = "Identity";

    private static final int crypto_sign_PUBLICKEYBYTES = 32;
    private static final int crypto_sign_SECRETKEYBYTES = 64;
    private static final int crypto_sign_BYTES = 64;

    static {
        // Load native libraries
        NaCl.sodium();
    }

    public static KeyPair generateKeyPairForAlias(@NonNull String alias) {
        final int crypto_sign_PUBLICKEYBYTES = 32;
        final int crypto_sign_SECRETKEYBYTES = 64;
        final int crypto_sign_BYTES = 64;

        String message = "test";

        byte[] pk = new byte[crypto_sign_PUBLICKEYBYTES];
        byte[] sk = new byte[crypto_sign_SECRETKEYBYTES];

        // On Identity Read:
        // [name][sig][pub]
        // [20]  [64] [32]

        // Message Read/Write:
        //[body][sigbody][pub]
        //[140] [64]     [32]
        Sodium.crypto_sign_ed25519_keypair(pk, sk);
        return new KeyPair(sk, pk, alias);
    }

    @Nullable
    public static byte[] signMessage(@NonNull KeyPair keypair, @NonNull byte[] message) {
        byte[] sealed_message = new byte[crypto_sign_BYTES + message.length];
        int[] sealed_message_len = new int[0];

        Sodium.crypto_sign_ed25519(sealed_message, sealed_message_len,
                message, message.length, keypair.secretKey);
        return sealed_message;
    }

    public static boolean verifyMessage(@NonNull byte[] pubkey, @NonNull byte[] signedMessage, @NonNull byte[] expectedMessage) {
        // Verify signature
        byte[] unsealed_message = new byte[expectedMessage.length];
        int[] message_length = new int[0];
        if (Sodium.crypto_sign_ed25519_open(unsealed_message,
                message_length,
                signedMessage,
                signedMessage.length, pubkey) != 0) {
        } else {
            return Arrays.equals(unsealed_message, expectedMessage);
        }
        return false;
    }
}
