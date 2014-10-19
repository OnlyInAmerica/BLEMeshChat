package pro.dbro.ble.model;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;

import pro.dbro.ble.protocol.Identity;
import pro.dbro.ble.protocol.OwnedIdentity;

/**
 * A thin model around a {@link android.database.Cursor}
 * that lazy-loads attributes as needed. As such, do
 * not to close the cursor fed to this class's constructor.
 * Instead call {@link #close}
 * <p/>
 * Created by davidbrodsky on 10/12/14.
 */
public class Peer implements Closeable {

    private Cursor mCursor;

    public Peer(Context context, int id) {
        Cursor result = context.getContentResolver().query(ChatContentProvider.Peers.PEERS,
                null,
                PeerTable.id + "= ?",
                new String[]{String.valueOf(id)},
                null);
        if (result != null && result.moveToFirst()) {
            init(result);
        } else {
            throw new IllegalArgumentException("No peer found with given id");
        }
    }

    public Peer(@NonNull Cursor cursor) {
        init(cursor);
    }

    private void init(Cursor cursor) {
        if (cursor.getCount() != 1)
            throw new IllegalArgumentException("Do not initialize Peer with a Cursor representing multiple rows");
        mCursor = cursor;
    }

    public int getId() {
        return mCursor.getInt(mCursor.getColumnIndex(PeerTable.id));
    }

    public String getAlias() {
        return mCursor.getString(mCursor.getColumnIndex(PeerTable.alias));
    }

    /**
     * @return whether this peer is owned by the application.
     * e.g: Do we have a secret key
     */
    public boolean isUser() {
        byte[] secretKey = mCursor.getBlob(mCursor.getColumnIndex(PeerTable.secKey));
        return secretKey != null && secretKey.length > 0;
    }

    /**
     * @return a {@link pro.dbro.ble.protocol.OwnedIdentity} for this peer,
     * or an {@link pro.dbro.ble.protocol.Identity} if this peer is not a user-owned peer.
     * <p/>
     * see {@link #isUser()}
     */
    public Identity getKeyPair() {
        if (!isUser()) {
            try {
                return new Identity(
                        mCursor.getBlob(mCursor.getColumnIndex(PeerTable.pubKey)),
                        mCursor.getString(mCursor.getColumnIndex(PeerTable.alias)),
                        DataUtil.storedDateFormatter.parse(mCursor.getString(mCursor.getColumnIndex(PeerTable.lastSeenDate))));
            } catch (ParseException e) {
                throw new IllegalStateException("Unable to create Identity from data");
            }
        } else {
            return new OwnedIdentity(
                    mCursor.getBlob(mCursor.getColumnIndex(PeerTable.secKey)),
                    mCursor.getBlob(mCursor.getColumnIndex(PeerTable.pubKey)),
                    mCursor.getString(mCursor.getColumnIndex(PeerTable.alias)));
        }
    }

    @Override
    public void close() throws IOException {
        mCursor.close();
    }
}
