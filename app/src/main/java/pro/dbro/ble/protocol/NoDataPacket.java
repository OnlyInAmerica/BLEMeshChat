package pro.dbro.ble.protocol;

import android.support.annotation.NonNull;

import java.util.Date;

/**
 * Created by davidbrodsky on 10/15/14.
 *
 */
public class NoDataPacket {
    public static final byte TYPE = 0x03;

    final public byte[] publicKey;
    final public Date authoredDate;
    final public byte[] signature;
    final public byte[] rawPacket;

    public NoDataPacket(@NonNull final byte[] publicKey,
                        @NonNull Date authoredDate,
                        @NonNull byte[] signature,
                        @NonNull byte[] rawPacket) {

        this.publicKey    = publicKey;
        this.signature    = signature;
        this.rawPacket    = rawPacket;
        this.authoredDate = authoredDate;
    }
}
