package pro.dbro.ble.data.model;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;

import pro.dbro.ble.protocol.IdentityPacket;
import pro.dbro.ble.protocol.OwnedIdentityPacket;

/**
 * A thin model around a {@link android.database.Cursor}
 * that lazy-loads attributes as needed. As such, do
 * not to close the cursor fed to this class's constructor.
 * Instead call {@link #close}
 * <p/>
 * Created by davidbrodsky on 10/12/14.
 */
public class Peer extends CursorModel {

    public Peer(@NonNull Cursor cursor) {
        super(cursor);

    }

    public int getId() {
        return mCursor.getInt(mCursor.getColumnIndex(PeerTable.id));
    }

    public String getAlias() {
        return mCursor.getString(mCursor.getColumnIndex(PeerTable.alias));
    }

    @Nullable
    public Date getLastDateSeen() {
        try {
            return DataUtil.storedDateFormatter.parse(mCursor.getString(mCursor.getColumnIndex(PeerTable.lastSeenDate)));
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * @return whether this peer represents the application user.
     * e.g: Do we have a secret key
     */
    public boolean isLocalPeer() {
        byte[] secretKey = mCursor.getBlob(mCursor.getColumnIndex(PeerTable.secKey));
        return secretKey != null && secretKey.length > 0;
    }

    /**
     * @return a {@link pro.dbro.ble.protocol.OwnedIdentityPacket} for this peer,
     * or an {@link pro.dbro.ble.protocol.IdentityPacket} if this peer is not a user-owned peer.
     * <p/>
     * see {@link #isLocalPeer()}
     */
    public IdentityPacket getIdentity() {
        if (!isLocalPeer()) {
            try {
                return new IdentityPacket(
                        mCursor.getBlob(mCursor.getColumnIndex(PeerTable.pubKey)),
                        mCursor.getString(mCursor.getColumnIndex(PeerTable.alias)),
                        DataUtil.storedDateFormatter.parse(mCursor.getString(mCursor.getColumnIndex(PeerTable.lastSeenDate))),
                        mCursor.getBlob(mCursor.getColumnIndex(PeerTable.rawPkt)));
            } catch (ParseException e) {
                throw new IllegalStateException("Unable to create Identity from data");
            }
        } else {
            return new OwnedIdentityPacket(
                    mCursor.getBlob(mCursor.getColumnIndex(PeerTable.secKey)),
                    mCursor.getBlob(mCursor.getColumnIndex(PeerTable.pubKey)),
                    mCursor.getString(mCursor.getColumnIndex(PeerTable.alias)),
                    mCursor.getBlob(mCursor.getColumnIndex(PeerTable.rawPkt)));
        }
    }

    @Override
    public void close() {
        mCursor.close();
    }

}
