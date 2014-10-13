package pro.dbro.ble.model;

import android.database.Cursor;
import android.support.annotation.NonNull;

import java.io.Closeable;
import java.io.IOException;

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

    public Peer(@NonNull Cursor cursor) {
        mCursor = cursor;
    }

    public String getAlias() {
        return mCursor.getString(mCursor.getColumnIndex(PeerTable.alias));
    }

    @Override
    public void close() throws IOException {
        mCursor.close();
    }
}
