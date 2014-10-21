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
public class BLEProtocol implements Protocol {
    public static final String TAG = "ChatProtocol";

    // <editor-fold desc="Public API">
    /** Bluetooth LE Mesh Chat Protocol Version */
    public static final byte VERSION = 0x01;

    /** Identity */
    public static final int MESSAGE_RESPONSE_LENGTH    = 309;  // bytes
    public static final int IDENTITY_RESPONSE_LENGTH   = 140;  // bytes
    public static final int MESSAGE_BODY_LENGTH        = 140;  // bytes
    public static final int ALIAS_LENGTH               = 35;   // bytes

    private static final ByteBuffer sTimeStampBuffer = ByteBuffer.allocate(Long.SIZE / 8);

    static {
        sTimeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    /** Outgoing
     *
     * Create raw transmission data from protocol Objects
    */

    @Nullable
    public byte[] createIdentityResponse(@NonNull OwnedIdentity ownedIdentity) {
        // Protocol version 1
        //[[version=1][timestamp=8][sender_public_key=32][display_name=35]][signature=64]
        try {
            byte[] identity = new byte[IDENTITY_RESPONSE_LENGTH];
            int writeIndex = 0;
            writeIndex += addVersionToBuffer(identity, writeIndex);
            writeIndex += addTimestampToBuffer(identity, writeIndex);
            writeIndex += addPublicKeyToBuffer(ownedIdentity.publicKey, identity, writeIndex);
            writeIndex += addAliasToBuffer(ownedIdentity.alias, identity, writeIndex);
            writeIndex += addSignatureToBuffer(ownedIdentity.secretKey, identity, writeIndex);

            if (writeIndex != IDENTITY_RESPONSE_LENGTH)
                throw new IllegalStateException("Generated Identity does not match expected length");

            return identity;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Failed to generate Identity response. Are there invalid UTF-8 characters in the user alias?");
            e.printStackTrace();
        }
        return null;
    }

    public byte[] createPublicMessageResponse(@NonNull OwnedIdentity ownedIdentity, String body) {
        // Protocol version 1
        //[[version=1][timestamp=8][sender_public_key=32][message=140][reply_signature=64]][signature=64]
        try {
            byte[] message = new byte[MESSAGE_RESPONSE_LENGTH];
            int writeIndex = 0;
            writeIndex += addVersionToBuffer(message, writeIndex);
            writeIndex += addTimestampToBuffer(message, writeIndex);
            writeIndex += addPublicKeyToBuffer(ownedIdentity.publicKey, message, writeIndex);
            writeIndex += addMessageBodyToBuffer(body, message, writeIndex);
            writeIndex += 64; // Empty reply_signature
            writeIndex += addSignatureToBuffer(ownedIdentity.secretKey, message, writeIndex);

            if (writeIndex != MESSAGE_RESPONSE_LENGTH)
                throw new IllegalStateException("Generated Message does not match expected length");

            return message;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Failed to generate Identity response. Are there invalid UTF-8 characters in the user alias?");
            e.printStackTrace();
        }
        return null;
    }

    /** Incoming
     *
     * Produce protocol Objects from raw transmission data
     */

    @Nullable
    public Identity consumeIdentityResponse(@NonNull byte[] identity) {
        if (identity.length != IDENTITY_RESPONSE_LENGTH)
            throw new IllegalArgumentException("Identity response is illegal length");

        // Protocol version 1
        //[[version=1][timestamp=8][sender_public_key=32][display_name=35]][signature=64]
        try {
            int readIndex     = 0;
            byte[] timestamp  = new byte[Long.SIZE / 8];
            byte[] public_key = new byte[SodiumShaker.crypto_sign_PUBLICKEYBYTES];
            byte[] alias      = new byte[ALIAS_LENGTH];
            byte[] signature  = new byte[SodiumShaker.crypto_sign_BYTES];
            byte[] signedData = new byte[IDENTITY_RESPONSE_LENGTH - SodiumShaker.crypto_sign_BYTES];

            readIndex += assertBufferVersion(identity, readIndex);
            readIndex += getBytesFromBuffer(identity, timestamp, readIndex);
            readIndex += getBytesFromBuffer(identity, public_key, readIndex);
            readIndex += getBytesFromBuffer(identity, alias, readIndex);
            readIndex += getBytesFromBuffer(identity, signature, readIndex);

            System.arraycopy(identity, 0, signedData, 0, signedData.length);
            boolean validSignature = SodiumShaker.verifySignature(public_key, signature, signedData);
            if (!validSignature)
                throw new IllegalStateException("Identity signature does not match content!");

            return new Identity(public_key, new String(alias, "UTF-8"), getDateFromTimestampBuffer(timestamp), identity);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Failed to generate Identity response. Are there invalid UTF-8 characters in the user alias?");
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    public Message consumeMessageResponse(@NonNull byte[] message) {
        if (message.length != MESSAGE_RESPONSE_LENGTH)
            throw new IllegalArgumentException("Identity response is illegal length");

        // Protocol version 1
        //[[version=1][timestamp=8][sender_public_key=32][message=140][reply_signature=64]][signature=64]
        try {
            int readIndex          = 0;
            byte[] timestamp       = new byte[Long.SIZE / 8];
            byte[] public_key      = new byte[SodiumShaker.crypto_sign_PUBLICKEYBYTES];
            byte[] body            = new byte[MESSAGE_BODY_LENGTH];
            byte[] alias           = new byte[ALIAS_LENGTH];
            byte[] signature       = new byte[SodiumShaker.crypto_sign_BYTES];
            byte[] replySignature  = new byte[SodiumShaker.crypto_sign_BYTES];
            byte[] signedData      = new byte[MESSAGE_RESPONSE_LENGTH - SodiumShaker.crypto_sign_BYTES];

            readIndex += assertBufferVersion(message, readIndex);
            readIndex += getBytesFromBuffer(message, timestamp, readIndex);
            readIndex += getBytesFromBuffer(message, public_key, readIndex);
            readIndex += getBytesFromBuffer(message, body, readIndex);
            readIndex += getBytesFromBuffer(message, replySignature, readIndex);
            readIndex += getBytesFromBuffer(message, signature, readIndex);

            System.arraycopy(message, 0, signedData, 0, signedData.length);
            boolean validSignature = SodiumShaker.verifySignature(public_key, signature, signedData);
            if (!validSignature)
                throw new IllegalStateException("Message signature does not match content!");

            return new Message(public_key, signature, replySignature, new String(alias, "UTF-8"), getDateFromTimestampBuffer(timestamp), new String(body, "UTF_8"), message);
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
            sTimeStampBuffer.rewind();
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
        byte[] paddedBodyAsBytes = new byte[MESSAGE_BODY_LENGTH];

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

    /**
     * Generate signature for input from the first byte until the offset byte. Append signature to input after offset byte.
     */
    private static int addSignatureToBuffer(@NonNull byte[] secret_key, @NonNull byte[] input, int offset) {
        int bytesToWrite = SodiumShaker.crypto_sign_BYTES;
        assertBufferLength(input, offset + bytesToWrite);

        byte[] signature = SodiumShaker.generateSignatureForMessage(secret_key, input, offset);

        System.arraycopy(signature, 0, input, offset, bytesToWrite);
        return bytesToWrite;
    }

    /** Utility */

    /**
     * Truncates or pads input to fit precisely inside output.
     * After this call output will contain the truncated or padded input
     */
    private static void truncateOrPadTextBuffer(byte[] input, byte[] output) {
        System.arraycopy(input, 0, output, 0, Math.min(input.length, output.length));
        if (input.length < output.length) {
            for (int x = input.length; x < output.length; x++) {
                output[x] = 0x20; // UTF-8 space
            }
        }
    }

    private static void assertBufferLength(byte[] input, int minimumLength) {
        if (input.length < minimumLength)
            throw new IllegalArgumentException(String.format("Operation requires input buffer length %d. Actual: %d", minimumLength, input.length));
    }

    private static int assertBufferVersion(byte[] input, int offset) {
        byte[] version = new byte[1];
        getVersionFromBuffer(input, version, offset);

        if (version[0] != VERSION)
            throw new IllegalStateException(String.format("Response is for an unknown protocol version. Got %d. Expected %d", version[0], VERSION));
        return 1;
    }

    @Nullable
    private static Date getDateFromTimestampBuffer(byte[] timestamp) {
        synchronized (sTimeStampBuffer) {
            sTimeStampBuffer.clear();
            sTimeStampBuffer.put(timestamp);
            sTimeStampBuffer.rewind();
            // TODO: Test if flip needed
            return new Date(sTimeStampBuffer.getLong() * 1000);
        }
    }

    // </editor-fold desc="Private API">
}
