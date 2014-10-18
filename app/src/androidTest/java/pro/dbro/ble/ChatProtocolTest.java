package pro.dbro.ble;

import android.app.Application;
import android.test.ApplicationTestCase;
import android.util.Log;

import java.net.SocketImpl;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

import pro.dbro.ble.crypto.SodiumShaker;
import pro.dbro.ble.protocol.ChatProtocol;
import pro.dbro.ble.protocol.Identity;
import pro.dbro.ble.protocol.Message;
import pro.dbro.ble.protocol.OwnedIdentity;
import pro.dbro.ble.util.RandomString;

/**
 * Tests of the ChatProtocol.
 * Creating and Recovering ChatProtocol objects from transmission data byte[]
 */
public class ChatProtocolTest extends ApplicationTestCase<Application> {
    public ChatProtocolTest() {
        super(Application.class);
    }

    OwnedIdentity mSenderIdentity;

    protected void setUp() throws Exception {
        super.setUp();

        String username = new RandomString(ChatProtocol.ALIAS_LENGTH).nextString();
        mSenderIdentity = SodiumShaker.generateKeyPairForAlias(username);
    }

    /**
     * {@link pro.dbro.ble.protocol.Identity} -> byte[] -> {@link pro.dbro.ble.protocol.Identity}
     */
    public void testCreateAndConsumeIdentityResponse() {
        byte[] identityResponse = ChatProtocol.createIdentityResponse(mSenderIdentity);

        // Parse Identity from sender's identityResponse response byte[]
        Identity parsedIdentity = ChatProtocol.consumeIdentityResponse(identityResponse);

        assertEquals(parsedIdentity.alias, mSenderIdentity.alias);
        assertEquals(Arrays.equals(parsedIdentity.publicKey, mSenderIdentity.publicKey), true);
    }

    /**
     * {@link pro.dbro.ble.protocol.Message} -> byte[] -> {@link pro.dbro.ble.protocol.Message}
     */
    public void testCreateAndConsumeMessageResponse() {
        String messageBody = new RandomString(ChatProtocol.MESSAGE_BODY_LENGTH).nextString();

        byte[] messageResponse = ChatProtocol.createPublicMessageResponse(mSenderIdentity, messageBody);

        Message parsedMessage = ChatProtocol.consumeMessageResponse(messageResponse);

        assertEquals(messageBody, parsedMessage.body);
    }
}