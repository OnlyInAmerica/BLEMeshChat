package pro.dbro.ble.data.model;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.text.ParseException;
import java.util.Date;
import java.util.Objects;

import pro.dbro.ble.protocol.IdentityPacket;
import pro.dbro.ble.protocol.OwnedIdentityPacket;

/**
 * Created by davidbrodsky on 10/12/14.
 */
public class Peer {

    private int mId;
    private byte[] mPublicKey;
    private byte[] mSecretKey;
    private String mAlias;
    private Date mLastSeen;

    private byte[] mRawPkt;


    public Peer(@NonNull Cursor cursor) {
        mId = cursor.getInt(cursor.getColumnIndex(PeerTable.id));
        mPublicKey = cursor.getBlob(cursor.getColumnIndex(PeerTable.pubKey));
        mSecretKey = cursor.getBlob(cursor.getColumnIndex(PeerTable.secKey));
        mAlias = cursor.getString(cursor.getColumnIndex(PeerTable.alias));
        mRawPkt = cursor.getBlob(cursor.getColumnIndex(PeerTable.rawPkt));

        try {
            mLastSeen = DataUtil.storedDateFormatter.parse(cursor.getString(cursor.getColumnIndex(PeerTable.lastSeenDate)));
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public int getId() {
       return mId;
    }

    public byte[] getPublicKey() {
        return mPublicKey;
    }

    public String getAlias() {
        return mAlias;
    }

    @Nullable
    public Date getLastDateSeen() {
        return mLastSeen;
    }
    /**
     * @return whether this peer represents the application user.
     * e.g: Do we have a secret key
     */
    public boolean isLocalPeer() {
        return mSecretKey != null && mSecretKey.length > 0;
    }

    /**
     * @return a {@link pro.dbro.ble.protocol.OwnedIdentityPacket} for this peer,
     * or an {@link pro.dbro.ble.protocol.IdentityPacket} if this peer is not a user-owned peer.
     * <p/>
     * see {@link #isLocalPeer()}
     */
    public IdentityPacket getIdentity() {
        if (!isLocalPeer()) {
            return new IdentityPacket(mPublicKey, mAlias, mLastSeen, mRawPkt);
        } else {
            return new OwnedIdentityPacket(mSecretKey, mPublicKey, mAlias, mRawPkt);
        }
    }

    @Override
    public boolean equals(Object obj) {

        if(obj == this) return true;
        if(obj == null) return false;

        if (getClass().equals(obj.getClass()))
        {
            final Peer other = (Peer) obj;

            return mId == other.mId;
        }

        return false;
    }
}
