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
    private static final int IDENTITY_LENGTH = 140;  // bytes
    private static final int ALIAS_LENGTH    = 35;  // bytes

    private static ByteBuffer sTimeStampBuffer = ByteBuffer.allocate(Long.SIZE);

    @Nullable
    public static byte[] makeIdentityResponse(@NonNull KeyPair keyPair) {
        try {
            byte[] identity = new byte[IDENTITY_LENGTH - Identity.crypto_sign_BYTES];
            int writeIndex = 0;
            // Protocol version 1
            //[[version=1][timestamp=8][sender_public_key=32][display_name=35]][signature=64]
            writeIndex += addVersionToBuffer(identity, writeIndex);
            writeIndex += addTimestampToBuffer(identity, writeIndex);
            writeIndex += addPublicKeyToBuffer(keyPair.publicKey, identity, writeIndex);
            writeIndex += addAliasToBuffer(keyPair.alias, identity, writeIndex);

            if (writeIndex != IDENTITY_LENGTH)
                throw new IllegalStateException("Generated Identity does not match expected length");

            return Identity.signMessage(keyPair, identity);
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

        if (aliasAsBytes.length != ALIAS_LENGTH) {
            System.arraycopy(aliasAsBytes, 0, paddedAliasAsBytes, 0, Math.min(aliasAsBytes.length, ALIAS_LENGTH));
            for (int x = aliasAsBytes.length; x < ALIAS_LENGTH; x++) {
                paddedAliasAsBytes[x] = 0x20; // UTF-8 space
            }
        }

        System.arraycopy(paddedAliasAsBytes, 0, input, offset, bytesToWrite);
        return bytesToWrite;
    }

//    public static void arraycopy (Object src, int srcPos, Object dst, int dstPos, int length)
//
//    Added in API level 1
//    Copies length elements from the array src, starting at offset srcPos, into the array dst, starting at offset dstPos.
//
//    The source and destination arrays can be the same array, in which case copying is performed as if the source elements are first copied into a temporary array and then into the destination array.
//
//    Parameters
//    src	the source array to copy the content.
//    srcPos	the starting index of the content in src.
//    dst	the destination array to copy the data into.
//    dstPos	the starting index for the copied content in dst.
//    length	the number of elements to be copied.

    // </editor-fold desc="Private API">


}
