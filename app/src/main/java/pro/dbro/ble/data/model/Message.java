package pro.dbro.ble.data.model;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.text.ParseException;
import java.util.Date;

import pro.dbro.ble.data.DataStore;
import pro.dbro.ble.protocol.MessagePacket;

/**
 * A thin model around a {@link android.database.Cursor}
 * that lazy-loads attributes as needed. As such, do
 * not to close the cursor fed to this class's constructor.
 * Instead call {@link #close}
 * <p/>
 * Created by davidbrodsky on 10/12/14.
 */
public class Message extends CursorModel {

    public Message(@NonNull Cursor cursor) {
        super(cursor);

    }

    public int getId() {
        return mCursor.getInt(mCursor.getColumnIndex(MessageTable.id));
    }

    public String getBody() {
        return mCursor.getString(mCursor.getColumnIndex(MessageTable.body));
    }

    public byte[] getPublicKey(DataStore dataStore) {
        return getSender(dataStore).getIdentity().publicKey;
    }

    public byte[] getSignature() {
        return mCursor.getBlob(mCursor.getColumnIndex(MessageTable.signature));
    }

    public byte[] getReplySignature() {
        return mCursor.getBlob(mCursor.getColumnIndex(MessageTable.replySig));
    }

    public byte[] getRawPacket() {
        return mCursor.getBlob(mCursor.getColumnIndex(MessageTable.rawPacket));
    }

    @Nullable
    public Peer getSender(DataStore dataStore) {
        return dataStore.getPeerById(mCursor.getInt(mCursor.getColumnIndex(MessageTable.peerId)));
    }

    @Nullable
    public MessagePacket getProtocolMessage(DataStore dataStore) {
        return new MessagePacket(
                getSender(dataStore).getIdentity(),
                getSignature(),
                getReplySignature(),
                getBody(),
                getRawPacket());

    }

    @Nullable
    public Date getRelativeReceivedDate() {
        try {
            return DataUtil.storedDateFormatter.parse(
                    mCursor.getString(mCursor.getColumnIndex(MessageTable.authoredDate)));
        } catch (ParseException e) {
            return null;
        }
    }
}
