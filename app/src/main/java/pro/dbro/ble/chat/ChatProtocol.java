package pro.dbro.ble.chat;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import pro.dbro.ble.crypto.Identity;
import pro.dbro.ble.crypto.KeyPair;

/**
 * Created by davidbrodsky on 10/14/14.
 */
public class ChatProtocol {
    public static final String TAG = "ChatProtocol";

    // <editor-fold desc="Public API">
    /** Bluetooth LE Mesh Chat Protocol Version */
    public static final byte VERSION = 0x01;

    /** Identity */
    private static final int MESSAGE_RESPONSE_LENGTH    = 309;  // bytes
    private static final int IDENTITY_RESPONSE_LENGTH   = 140;  // bytes
    private static final int MESSAGE_BODY_LENGTH        = 140;  // bytes
    private static final int ALIAS_LENGTH               = 35;   // bytes

    private static ByteBuffer sTimeStampBuffer = ByteBuffer.allocate(Long.SIZE);

    @Nullable
    public static byte[] makeIdentityResponse(@NonNull KeyPair keyPair) {
        // Protocol version 1
        //[[version=1][timestamp=8][sender_public_key=32][display_name=35]][signature=64]
        try {
            byte[] identity = new byte[IDENTITY_RESPONSE_LENGTH - Identity.crypto_sign_BYTES];
            int writeIndex = 0;
            writeIndex += addVersionToBuffer(identity, writeIndex);
            writeIndex += addTimestampToBuffer(identity, writeIndex);
            writeIndex += addPublicKeyToBuffer(keyPair.publicKey, identity, writeIndex);
            writeIndex += addAliasToBuffer(keyPair.alias, identity, writeIndex);

            if (writeIndex != IDENTITY_RESPONSE_LENGTH)
                throw new IllegalStateException("Generated Identity does not match expected length");

            return Identity.signMessage(keyPair, identity);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Failed to generate Identity response. Are there invalid UTF-8 characters in the user alias?");
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] makePublicMessageResponse(@NonNull KeyPair keyPair, String body) {
        // Protocol version 1
        //[[version=1][timestamp=8][sender_public_key=32][message=140][reply_signature=64]][signature=64]
        try {
            byte[] message = new byte[MESSAGE_RESPONSE_LENGTH - Identity.crypto_sign_BYTES];
            int writeIndex = 0;
            writeIndex += addVersionToBuffer(message, writeIndex);
            writeIndex += addTimestampToBuffer(message, writeIndex);
            writeIndex += addPublicKeyToBuffer(keyPair.publicKey, message, writeIndex);
            writeIndex += addMessageBodyToBuffer(body, message, writeIndex);
            writeIndex += 64; // Empty reply_signature

            if (writeIndex != MESSAGE_RESPONSE_LENGTH)
                throw new IllegalStateException("Generated Message does not match expected length");

            return Identity.signMessage(keyPair, message);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Failed to generate Identity response. Are there invalid UTF-8 characters in the user alias?");
            e.printStackTrace();
        }
        return null;
    }


    // </editor-fold desc="Public API">

    // <editor-fold desc="Private API">

    private static int addVersionToBuffer(@NonNull byte[] input, int offset) {
        int bytesToWrite = 1;
        if (input.length < offset + bytesToWrite)
            throw new IllegalArgumentException("input buffer has insufficient length");

        input[offset] = VERSION;
        return bytesToWrite;
    }

    private static int addTimestampToBuffer(@NonNull byte[] input, int offset) {
        int bytesToWrite = Long.SIZE / 8;
        if (input.length < offset + bytesToWrite)
            throw new IllegalArgumentException("input buffer has insufficient length");

        long unixTime64 = System.currentTimeMillis() / 1000L;
        sTimeStampBuffer.putLong(unixTime64);
        System.arraycopy(sTimeStampBuffer.array(), 0, input, offset, bytesToWrite);
        return bytesToWrite;
    }

    private static int addPublicKeyToBuffer(@NonNull byte[] public_key, @NonNull byte[] input, int offset) {
        int bytesToWrite = public_key.length;
        if (input.length < offset + bytesToWrite)
            throw new IllegalArgumentException("input buffer has insufficient length");

        System.arraycopy(public_key, 0, input, offset, bytesToWrite);
        return bytesToWrite;
    }

    private static int addAliasToBuffer(@NonNull String alias, @NonNull byte[] input, int offset) throws UnsupportedEncodingException {
        int bytesToWrite = ALIAS_LENGTH;
        if (input.length < offset + bytesToWrite)
            throw new IllegalArgumentException("input buffer has insufficient length");

        byte[] aliasAsBytes = alias.getBytes("UTF-8");
        byte[] paddedAliasAsBytes = new byte[ALIAS_LENGTH];

        truncateOrPadTextBuffer(aliasAsBytes, paddedAliasAsBytes);

        System.arraycopy(paddedAliasAsBytes, 0, input, offset, bytesToWrite);
        return bytesToWrite;
    }

    private static int addMessageBodyToBuffer(@NonNull String body, @NonNull byte[] input, int offset) throws UnsupportedEncodingException {
        int bytesToWrite = MESSAGE_BODY_LENGTH;
        if (input.length < offset + bytesToWrite)
            throw new IllegalArgumentException("input buffer has insufficient length");

        byte[] bodyAsBytes = body.getBytes("UTF-8");
        byte[] paddedBodyAsBytes = new byte[ALIAS_LENGTH];

        truncateOrPadTextBuffer(bodyAsBytes, paddedBodyAsBytes);

        System.arraycopy(paddedBodyAsBytes, 0, input, offset, bytesToWrite);
        return bytesToWrite;
    }

    /**
     * Truncates or pads input to fit precisely inside output.
     * After this call output will contain the truncated or padded input
     */
    private static void truncateOrPadTextBuffer(byte[] input, byte[] output) {
        if (input.length != output.length) {
            System.arraycopy(input, 0, output, 0, Math.min(input.length, output.length));
            for (int x = input.length; x < output.length; x++) {
                output[x] = 0x20; // UTF-8 space
            }
        }
    }

    // </editor-fold desc="Private API">
}
