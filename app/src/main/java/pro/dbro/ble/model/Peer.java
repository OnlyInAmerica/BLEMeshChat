package pro.dbro.ble.model;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import pro.dbro.ble.crypto.KeyPair;

/**
 * A thin model around a {@link android.database.Cursor}
 * that lazy-loads attributes as needed. As such, do
 * not to close the cursor fed to this class's constructor.
 * Instead call {@link #close}
 *
 * Created by davidbrodsky on 10/12/14.
 */
public class Peer implements Closeable{

    private Cursor mCursor;

    public Peer(Context context, int id) {
        Cursor result = context.getContentResolver().query(ChatContentProvider.Peers.PEERS,
                null,
                PeerTable.id + "= ?",
                new String[] {String.valueOf(id)},
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
        mCursor = cursor;
    }

    public String getAlias() {
        return mCursor.getString(mCursor.getColumnIndex(PeerTable.alias));
    }

    /**
     * @return whether this peer is owned by the application.
     * e.g: Do we have a secret key
     */
    public boolean isUser() {
        String secretKey = mCursor.getString(mCursor.getColumnIndex(PeerTable.secKey));
        return secretKey != null && secretKey.length() > 0;
    }

    /**
     * @return a {@link pro.dbro.ble.crypto.KeyPair} for this peer,
     * or null if this peer is not a user-owned peer.
     *
     * see {@link #isUser()}
     */
    public KeyPair getKeyPair() {
        if (!isUser()) return null;
        try {
            return new KeyPair(
                    mCursor.getString(mCursor.getColumnIndex(PeerTable.secKey)).getBytes("UTF-8"),
                    mCursor.getString(mCursor.getColumnIndex(PeerTable.pubKey)).getBytes("UTF-8"),
                    mCursor.getString(mCursor.getColumnIndex(PeerTable.alias)));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        mCursor.close();
    }
}
