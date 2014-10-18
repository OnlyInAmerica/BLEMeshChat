package pro.dbro.ble;

import android.app.Application;
import android.test.ApplicationTestCase;
import android.test.RenamingDelegatingContext;
import android.util.Log;

import java.net.SocketImpl;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

import pro.dbro.ble.crypto.SodiumShaker;
import pro.dbro.ble.model.ChatDatabase;
import pro.dbro.ble.model.Peer;
import pro.dbro.ble.protocol.ChatProtocol;
import pro.dbro.ble.protocol.Identity;
import pro.dbro.ble.protocol.Message;
import pro.dbro.ble.protocol.OwnedIdentity;
import pro.dbro.ble.util.RandomString;

/**
 * Tests of the ChatProtocol and Chat Application.
 * Creating and Recovering ChatProtocol objects from transmission data byte[]
 */
public class ChatAppTest extends ApplicationTestCase<Application> {
    public ChatAppTest() {
        super(Application.class);
    }

    OwnedIdentity mSenderIdentity;
    RenamingDelegatingContext mMockContext;

    protected void setUp() throws Exception {
        super.setUp();

        // TODO: Figure out how to properly mock database
//        mMockContext = new RenamingDelegatingContext(getContext(), new RandomString(5).nextString());
//        boolean didDelete = mMockContext.deleteDatabase(ChatDatabase.class.getName().toLowerCase());

        String username = new RandomString(ChatProtocol.ALIAS_LENGTH).nextString();
        mSenderIdentity = SodiumShaker.generateKeyPairForAlias(username);
    }

    /** Protocol Tests **/

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
        assertEquals(Arrays.equals(parsedMessage.sender.publicKey, mSenderIdentity.publicKey), true);
    }

    /** Application Tests **/

    public void testApplicationIdentityCreation() {
        // Make an Identity for the user. Test successful database entry and retrieval
        /* TODO: Once we can mock the database

        Peer user = ChatApp.getPrimaryIdentity(getContext());
        String username = new RandomString(ChatProtocol.ALIAS_LENGTH).nextString();

        assertEquals(user , null);   // Off the bat there should be no primary identity

        int userId = ChatApp.createNewIdentity(mMockContext, username);

        assertEquals(userId, 1);    // We've created the first entry in the Peer table

        user = ChatApp.getPrimaryIdentity(mMockContext);

        assertEquals(user.getAlias().equals(username), true);   // Username in database matches input

        byte[] userIdentity = ChatApp.getPrimaryIdentityResponse(getContext());

        Peer parsedUser = ChatApp.consumeReceivedIdentity(getContext(), userIdentity);

        assertEquals(Arrays.equals(parsedUser.getKeyPair().publicKey, user.getKeyPair().publicKey), true);

        // Test receiving remote Identity

        byte[] identityResponse = ChatProtocol.createIdentityResponse(mSenderIdentity);
        Peer remotePeer = ChatApp.consumeReceivedIdentity(getContext(), identityResponse);

        assertEquals(Arrays.equals(remotePeer.getKeyPair().publicKey, mSenderIdentity.publicKey), true);

        */
    }
}