package pro.dbro.ble;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.test.ApplicationTestCase;

import java.util.Arrays;
import java.util.Date;

import pro.dbro.ble.crypto.SodiumShaker;
import pro.dbro.ble.data.model.ChatContentProvider;
import pro.dbro.ble.data.model.DataUtil;
import pro.dbro.ble.data.model.MessageTable;
import pro.dbro.ble.data.model.Peer;
import pro.dbro.ble.data.model.PeerTable;
import pro.dbro.ble.protocol.BLEProtocol;
import pro.dbro.ble.protocol.Identity;
import pro.dbro.ble.protocol.Message;
import pro.dbro.ble.protocol.OwnedIdentity;
import pro.dbro.ble.util.RandomString;

/**
 * Tests of the ChatProtocol and Chat Application.
 */
public class ChatAppTest extends ApplicationTestCase<Application> {
    public ChatAppTest() {
        super(Application.class);
    }

    OwnedIdentity mSenderIdentity;
    boolean mCreatedNewPrimaryIdentity;

    protected void setUp() throws Exception {
        super.setUp();

        String username = new RandomString(BLEProtocol.ALIAS_LENGTH).nextString();
        mSenderIdentity = SodiumShaker.generateOwnedIdentityForAlias(username);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /** Protocol Tests **/

    /**
     * {@link pro.dbro.ble.protocol.Identity} -> byte[] -> {@link pro.dbro.ble.protocol.Identity}
     */
    public void testCreateAndConsumeIdentityResponse() {
        byte[] identityResponse = BLEProtocol.createIdentityResponse(mSenderIdentity);

        // Parse Identity from sender's identityResponse response byte[]
        Identity parsedIdentity = BLEProtocol.consumeIdentityResponse(identityResponse);

        assertEquals(parsedIdentity.alias, mSenderIdentity.alias);
        assertEquals(Arrays.equals(parsedIdentity.publicKey, mSenderIdentity.publicKey), true);
        assertDateIsRecent(parsedIdentity.dateSeen);
    }

    /**
     * {@link pro.dbro.ble.protocol.Message} -> byte[] -> {@link pro.dbro.ble.protocol.Message}
     */
    public void testCreateAndConsumeMessageResponse() {
        String messageBody = new RandomString(BLEProtocol.MESSAGE_BODY_LENGTH).nextString();

        byte[] messageResponse = BLEProtocol.createPublicMessageResponse(mSenderIdentity, messageBody);

        Message parsedMessage = BLEProtocol.consumeMessageResponse(messageResponse);

        assertEquals(messageBody, parsedMessage.body);
        assertEquals(Arrays.equals(parsedMessage.sender.publicKey, mSenderIdentity.publicKey), true);
        assertDateIsRecent(parsedMessage.authoredDate);
    }

    /** Application Tests **/

    /**
     * Create a {@link pro.dbro.ble.data.model.Peer} for protocol {@link pro.dbro.ble.protocol.Identity},
     * then create a {@link pro.dbro.ble.data.model.Message} for protocol {@link pro.dbro.ble.protocol.Message}.
     */
    public void testApplicationIdentityCreationAndMessageConsumption() {
        // Get or create new primary identity. This Identity serves as the app user
        Peer user = getOrCreatePrimaryPeerIdentity();

        // User discovers a peer
        Peer remotePeer = ChatApp.consumeReceivedIdentity(getContext(), BLEProtocol.createIdentityResponse(mSenderIdentity));
        // Assert Identity response parsed successfully
        assertEquals(Arrays.equals(remotePeer.getIdentity().publicKey, mSenderIdentity.publicKey), true);

        // Craft a mock message from remote peer
        String mockReceivedMessageBody = new RandomString(BLEProtocol.MESSAGE_BODY_LENGTH).nextString();
        byte[] mockReceivedMessage = BLEProtocol.createPublicMessageResponse(mSenderIdentity, mockReceivedMessageBody);

        // User receives mock message from remote peer
        pro.dbro.ble.data.model.Message parsedMockReceivedMessage = ChatApp.consumeReceivedBroadcastMessage(getContext(), mockReceivedMessage);
        assertEquals(mockReceivedMessageBody.equals(parsedMockReceivedMessage.getBody()), true);

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

        numDeleted = getContext().getContentResolver().delete(ChatContentProvider.Peers.PEERS,
                PeerTable.id + " = ?",
                new String[] {String.valueOf(remotePeer.getId())});

        assertEquals(numDeleted, 1);
        numDeleted = 0;

        numDeleted = getContext().getContentResolver().delete(ChatContentProvider.Messages.MESSAGES,
                MessageTable.id + " = ?",
                new String[] {String.valueOf(parsedMockReceivedMessage.getId())});
        assertEquals(numDeleted, 1);
        numDeleted = 0;
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

    private Peer getOrCreatePrimaryPeerIdentity() {
        Peer user = ChatApp.getPrimaryLocalPeer(getContext());
        if (user == null) {
            mCreatedNewPrimaryIdentity = true;
            int userId = ChatApp.createLocalPeer(getContext(), new RandomString(BLEProtocol.ALIAS_LENGTH).nextString());
            user = ChatApp.getPrimaryLocalPeer(getContext());

            assertEquals(userId, user.getId());
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