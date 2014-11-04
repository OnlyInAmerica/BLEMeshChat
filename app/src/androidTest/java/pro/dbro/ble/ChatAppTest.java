package pro.dbro.ble;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.test.ApplicationTestCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;

import pro.dbro.ble.crypto.KeyPair;
import pro.dbro.ble.crypto.SodiumShaker;
import pro.dbro.ble.data.ContentProviderStore;
import pro.dbro.ble.data.model.ChatContentProvider;
import pro.dbro.ble.data.model.DataUtil;
import pro.dbro.ble.data.model.Peer;
import pro.dbro.ble.data.model.PeerTable;
import pro.dbro.ble.protocol.BLEProtocol;
import pro.dbro.ble.protocol.IdentityPacket;
import pro.dbro.ble.protocol.MessagePacket;
import pro.dbro.ble.protocol.OwnedIdentityPacket;
import pro.dbro.ble.util.RandomString;

/**
 * Tests of the ChatProtocol and Chat Application.
 */
public class ChatAppTest extends ApplicationTestCase<Application> {
    public ChatAppTest() {
        super(Application.class);
    }

    ChatApp mApp;
    OwnedIdentityPacket mSenderIdentity;
    boolean mCreatedNewPrimaryIdentity;
    BLEProtocol bleProtocol = new BLEProtocol();
    ContentProviderStore dataStore;

    protected void setUp() throws Exception {
        super.setUp();

        mApp = new ChatApp(getContext());
        dataStore = new ContentProviderStore(getContext());
        String username = new RandomString(BLEProtocol.ALIAS_LENGTH).nextString();
        KeyPair keyPair =  SodiumShaker.generateKeyPair();
        mSenderIdentity = new OwnedIdentityPacket(keyPair.secretKey, keyPair.publicKey, username, null);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /** Protocol Tests **/

    /**
     * {@link pro.dbro.ble.protocol.IdentityPacket} -> byte[] -> {@link pro.dbro.ble.protocol.IdentityPacket}
     */
    public void testCreateAndConsumeIdentityResponse() {
        byte[] identityResponse = bleProtocol.serializeIdentity(mSenderIdentity);

        // Parse Identity from sender's identityResponse response byte[]
        IdentityPacket parsedIdentityPacket = bleProtocol.deserializeIdentity(identityResponse);

        assertEquals(parsedIdentityPacket.alias, mSenderIdentity.alias);
        assertEquals(Arrays.equals(parsedIdentityPacket.publicKey, mSenderIdentity.publicKey), true);
        assertDateIsRecent(parsedIdentityPacket.dateSeen);
    }

    /**
     * {@link pro.dbro.ble.protocol.MessagePacket} -> byte[] -> {@link pro.dbro.ble.protocol.MessagePacket}
     */
    public void testCreateAndConsumeMessageResponse() {
        String messageBody = new RandomString(BLEProtocol.MESSAGE_BODY_LENGTH).nextString();

        MessagePacket messageResponse = bleProtocol.serializeMessage(mSenderIdentity, messageBody);

        MessagePacket parsedMessagePacket = bleProtocol.deserializeMessage(messageResponse.rawPacket);

        assertEquals(messageBody, parsedMessagePacket.body);
        assertEquals(Arrays.equals(parsedMessagePacket.sender.publicKey, mSenderIdentity.publicKey), true);
        assertDateIsRecent(parsedMessagePacket.authoredDate);
    }

    /** Application Tests **/

    /**
     * Create a {@link pro.dbro.ble.data.model.Peer} for protocol {@link pro.dbro.ble.protocol.IdentityPacket},
     * then create a {@link pro.dbro.ble.data.model.Message} for protocol {@link pro.dbro.ble.protocol.MessagePacket}.
     */
    public void testApplicationIdentityCreationAndMessageConsumption() throws IOException {
        // TODO : Rewrite for new API
        // Get or create new primary identity. This Identity serves as the app user
        Peer user = getOrCreatePrimaryPeerIdentity();

        // User discovers a peer

        IdentityPacket remotePeer = bleProtocol.deserializeIdentity(bleProtocol.serializeIdentity(mSenderIdentity));
        // Assert Identity response parsed successfully
        assertEquals(Arrays.equals(remotePeer.publicKey, mSenderIdentity.publicKey), true);

        // Craft a mock message from remote peer
        String mockReceivedMessageBody = new RandomString(BLEProtocol.MESSAGE_BODY_LENGTH).nextString();
        MessagePacket mockReceivedMessage = bleProtocol.serializeMessage(mSenderIdentity, mockReceivedMessageBody);

        // User receives mock message from remote peer
//        pro.dbro.ble.data.model.Message parsedMockReceivedMessage = mApp.consumeReceivedBroadcastMessage(getContext(), mockReceivedMessage);
//        assertEquals(mockReceivedMessageBody.equals(parsedMockReceivedMessage.getBody()), true);

        // Cleanup
        // TODO: Should mock database
        int numDeleted = 0;
        if (mCreatedNewPrimaryIdentity) {
            numDeleted = getContext().getContentResolver().delete(ChatContentProvider.Peers.PEERS,
                    PeerTable.id + " = ?",
                    new String[]{String.valueOf(user.getId())});
            assertEquals(numDeleted, 1);
            numDeleted = 0;
        }

//        numDeleted = getContext().getContentResolver().delete(ChatContentProvider.Peers.PEERS,
//                PeerTable.id + " = ?",
//                new String[] {String.valueOf(remotePeer.getId())});
//
//        assertEquals(numDeleted, 1);
//        numDeleted = 0;
//
//        numDeleted = getContext().getContentResolver().delete(ChatContentProvider.Messages.MESSAGES,
//                MessageTable.id + " = ?",
//                new String[] {String.valueOf(parsedMockReceivedMessage.getId())});
//        assertEquals(numDeleted, 1);
//        numDeleted = 0;
    }

    /**
     * Test database lookups by BLOB column
     */
    public void testDatabaseQueryByBlob() {
        byte[] fakePubKey = new byte[] { (byte) 0x01 };
        ContentValues stubPeer = new ContentValues();
        stubPeer.put(PeerTable.alias, "test");
        stubPeer.put(PeerTable.lastSeenDate, DataUtil.storedDateFormatter.format(new Date()));
        stubPeer.put(PeerTable.pubKey, fakePubKey);
        Uri stubPeerUri = getContext().getContentResolver().insert(ChatContentProvider.Peers.PEERS, stubPeer);

        int stubPeerId = Integer.parseInt(stubPeerUri.getLastPathSegment());

        Cursor result = getContext().getContentResolver().query(ChatContentProvider.Peers.PEERS,
                null,
                PeerTable.id + " = ?",
                new String[] {
                        String.valueOf(stubPeerId)
                },
                null);

        assertEquals(result != null, true);
        assertEquals(result.moveToFirst(), true);

        byte[] resultBlob = result.getBlob(result.getColumnIndex(PeerTable.pubKey));

        assertEquals(Arrays.equals(resultBlob, fakePubKey), true);
        result.close();

        result = getContext().getContentResolver().query(ChatContentProvider.Peers.PEERS,
                null,
                "quote(" + PeerTable.pubKey + ") = ?",
                new String[] {
                  "X'01'"
                },
                null);

        assertEquals(result != null, true);
        assertEquals(result.moveToFirst(), true);

        // Cleanup

        int numDeleted = getContext().getContentResolver().delete((ChatContentProvider.Peers.PEERS),
                PeerTable.id + " = ?",
                new String[] {
                        String.valueOf(stubPeerId)
                });
        assertEquals(numDeleted ,1);
    }
    /** Utility **/

    private Peer getOrCreatePrimaryPeerIdentity() throws IOException {
        Peer user = mApp.getPrimaryIdentity();
        if (user == null) {
            mCreatedNewPrimaryIdentity = true;
            user =  mApp.createPrimaryIdentity(new RandomString(BLEProtocol.ALIAS_LENGTH).nextString());
            Peer testUser = mApp.getPrimaryIdentity();
            assertEquals(testUser.getId(), user.getId());
            testUser.close();
        }
        return user;
    }

    private void assertDateIsRecent(Date mustBeRecent) {
        long now = new Date().getTime();
        long oneSecondAgo = now - 1000;

        if ( (mustBeRecent.getTime() > now) ){
            throw new IllegalStateException("Parsed Identity time is from the future " + mustBeRecent);

        } else if (mustBeRecent.getTime() < oneSecondAgo) {
            throw new IllegalStateException("Parsed Identity time is from more than 500ms ago " + mustBeRecent);
        }
    }
}