package pro.dbro.ble.protocol;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;

import pro.dbro.ble.crypto.SodiumShaker;

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

    private static final ByteBuffer sTimeStampBuffer = ByteBuffer.allocate(Long.SIZE);

    static {
        sTimeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    @Nullable
    public static byte[] createIdentityResponse(@NonNull OwnedIdentity ownedIdentity) {
        // Protocol version 1
        //[[version=1][timestamp=8][sender_public_key=32][display_name=35]][signature=64]
        try {
            byte[] identity = new byte[IDENTITY_RESPONSE_LENGTH - SodiumShaker.crypto_sign_BYTES];
            int writeIndex = 0;
            writeIndex += addVersionToBuffer(identity, writeIndex);
            writeIndex += addTimestampToBuffer(identity, writeIndex);
            writeIndex += addPublicKeyToBuffer(ownedIdentity.publicKey, identity, writeIndex);
            writeIndex += addAliasToBuffer(ownedIdentity.alias, identity, writeIndex);

            if (writeIndex != IDENTITY_RESPONSE_LENGTH)
                throw new IllegalStateException("Generated Identity does not match expected length");

            return SodiumShaker.signMessage(ownedIdentity, identity);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Failed to generate Identity response. Are there invalid UTF-8 characters in the user alias?");
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] createPublicMessageResponse(@NonNull OwnedIdentity ownedIdentity, String body) {
        // Protocol version 1
        //[[version=1][timestamp=8][sender_public_key=32][message=140][reply_signature=64]][signature=64]
        try {
            byte[] message = new byte[MESSAGE_RESPONSE_LENGTH - SodiumShaker.crypto_sign_BYTES];
            int writeIndex = 0;
            writeIndex += addVersionToBuffer(message, writeIndex);
            writeIndex += addTimestampToBuffer(message, writeIndex);
            writeIndex += addPublicKeyToBuffer(ownedIdentity.publicKey, message, writeIndex);
            writeIndex += addMessageBodyToBuffer(body, message, writeIndex);
            writeIndex += 64; // Empty reply_signature

            if (writeIndex != MESSAGE_RESPONSE_LENGTH)
                throw new IllegalStateException("Generated Message does not match expected length");

            return SodiumShaker.signMessage(ownedIdentity, message);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Failed to generate Identity response. Are there invalid UTF-8 characters in the user alias?");
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    public static Identity consumeIdentityResponse(@NonNull byte[] identity) {
        if (identity.length != IDENTITY_RESPONSE_LENGTH)
            throw new IllegalArgumentException("Identity response is illegal length");

        // Protocol version 1
        //[[version=1][timestamp=8][sender_public_key=32][display_name=35]][signature=64]
        try {
            int readIndex     = 0;
            byte[] timestamp  = new byte[Long.SIZE];
            byte[] public_key = new byte[SodiumShaker.crypto_sign_PUBLICKEYBYTES];
            byte[] alias      = new byte[ALIAS_LENGTH];
            byte[] signedData = new byte[IDENTITY_RESPONSE_LENGTH - SodiumShaker.crypto_sign_BYTES];

            readIndex += assertBufferVersion(identity, readIndex);
            readIndex += getBytesFromBuffer(identity, timestamp, readIndex);
            readIndex += getBytesFromBuffer(identity, public_key, readIndex);
            readIndex += getBytesFromBuffer(identity, alias, readIndex);

            System.arraycopy(identity, 0, signedData, 0, signedData.length);
            boolean validSignature = SodiumShaker.verifyMessage(public_key, identity, signedData);
            if (!validSignature)
                throw new IllegalStateException("Identity signature does not match content!");

            return new Identity(public_key, new String(alias, "UTF-8"), getDateFromTimestampBuffer(timestamp, 0));
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Failed to generate Identity response. Are there invalid UTF-8 characters in the user alias?");
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    public static Message consumeMessageResponse(@NonNull byte[] message) {
        if (message.length != MESSAGE_RESPONSE_LENGTH)
            throw new IllegalArgumentException("Identity response is illegal length");

        // Protocol version 1
        //[[version=1][timestamp=8][sender_public_key=32][message=140][reply_signature=64]][signature=64]
        try {
            int readIndex     = 0;
            byte[] timestamp  = new byte[Long.SIZE];
            byte[] public_key = new byte[SodiumShaker.crypto_sign_PUBLICKEYBYTES];
            byte[] body       = new byte[MESSAGE_BODY_LENGTH];
            byte[] alias      = new byte[ALIAS_LENGTH];
            byte[] signedData = new byte[MESSAGE_RESPONSE_LENGTH - SodiumShaker.crypto_sign_BYTES];
            byte[] signature  = new byte[SodiumShaker.crypto_sign_BYTES];

            readIndex += assertBufferVersion(message, readIndex);
            readIndex += getBytesFromBuffer(message, timestamp, readIndex);
            readIndex += getBytesFromBuffer(message, public_key, readIndex);
            readIndex += getBytesFromBuffer(message, body, readIndex);
            readIndex += 64; // TODO: Consume reply_signature
            readIndex += getBytesFromBuffer(message, signature, readIndex);

            System.arraycopy(message, 0, signedData, 0, signedData.length);
            boolean validSignature = SodiumShaker.verifyMessage(public_key, message, signedData);
            if (!validSignature)
                throw new IllegalStateException("Message signature does not match content!");

            long timestampLong;
            synchronized (sTimeStampBuffer) {
                sTimeStampBuffer.clear();
                sTimeStampBuffer.put(timestamp);
                // TODO: Test if flip needed
                timestampLong = sTimeStampBuffer.getLong();
            }

            return new Message(public_key, signature, new String(alias, "UTF-8"), getDateFromTimestampBuffer(timestamp, 0), new String(body, "UTF_8"));
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
        assertBufferLength(input, offset + bytesToWrite);

        input[offset] = VERSION;
        return bytesToWrite;
    }

    private static int getVersionFromBuffer(@NonNull byte[] input, @NonNull byte[] version, int offset) {
        int bytesToRead = 1;
        assertBufferLength(input, offset + bytesToRead);
        version[0] = input[offset];
        return bytesToRead;
    }

    private static int addTimestampToBuffer(@NonNull byte[] input, int offset) {
        synchronized (sTimeStampBuffer) {
            int bytesToWrite = Long.SIZE / 8;
            assertBufferLength(input, offset + bytesToWrite);

            long unixTime64 = System.currentTimeMillis() / 1000L;
            sTimeStampBuffer.putLong(unixTime64);
            System.arraycopy(sTimeStampBuffer.array(), 0, input, offset, bytesToWrite);
            return bytesToWrite;
        }
    }

    private static int addPublicKeyToBuffer(@NonNull byte[] public_key, @NonNull byte[] input, int offset) {
        int bytesToWrite = public_key.length;
        assertBufferLength(input, offset + bytesToWrite);

        System.arraycopy(public_key, 0, input, offset, bytesToWrite);
        return bytesToWrite;
    }

    private static int addAliasToBuffer(@NonNull String alias, @NonNull byte[] input, int offset) throws UnsupportedEncodingException {
        int bytesToWrite = ALIAS_LENGTH;
        assertBufferLength(input, offset + bytesToWrite);

        byte[] aliasAsBytes = alias.getBytes("UTF-8");
        byte[] paddedAliasAsBytes = new byte[ALIAS_LENGTH];

        truncateOrPadTextBuffer(aliasAsBytes, paddedAliasAsBytes);

        System.arraycopy(paddedAliasAsBytes, 0, input, offset, bytesToWrite);
        return bytesToWrite;
    }

    private static int addMessageBodyToBuffer(@NonNull String body, @NonNull byte[] input, int offset) throws UnsupportedEncodingException {
        int bytesToWrite = MESSAGE_BODY_LENGTH;
        assertBufferLength(input, offset + bytesToWrite);

        byte[] bodyAsBytes = body.getBytes("UTF-8");
        byte[] paddedBodyAsBytes = new byte[ALIAS_LENGTH];

        truncateOrPadTextBuffer(bodyAsBytes, paddedBodyAsBytes);

        System.arraycopy(paddedBodyAsBytes, 0, input, offset, bytesToWrite);
        return bytesToWrite;
    }

    private static int getBytesFromBuffer(@NonNull byte[] input, @NonNull byte[] output, int offset) {
        int bytesToRead = output.length;
        assertBufferLength(input, offset + bytesToRead);

        System.arraycopy(input, offset, output, 0, bytesToRead);
        return bytesToRead;
    }

    /** Utility */

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

    private static void assertBufferLength(byte[] input, int minimumLength) {
        if (input.length < minimumLength)
            throw new IllegalArgumentException("input buffer has insufficient length");
    }

    private static int assertBufferVersion(byte[] input, int offset) {
        byte[] version = new byte[1];
        getVersionFromBuffer(input, version, offset);

        if (version[0] != VERSION)
            throw new IllegalStateException("Response is for an unknown protocol version");
        return 1;
    }

    @Nullable
    private static Date getDateFromTimestampBuffer(byte[] timestamp, int offset) {
        synchronized (sTimeStampBuffer) {
            sTimeStampBuffer.clear();
            sTimeStampBuffer.put(timestamp);
            // TODO: Test if flip needed
            return new Date(sTimeStampBuffer.getLong() * 1000);
        }
    }

    // </editor-fold desc="Private API">
}
