package pro.dbro.ble.model;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;

import pro.dbro.ble.ChatApp;

/**
 * A thin model around a {@link android.database.Cursor}
 * that lazy-loads attributes as needed. As such, do
 * not to close the cursor fed to this class's constructor.
 * Instead call {@link #close}
 * <p/>
 * Created by davidbrodsky on 10/12/14.
 */
public class Message implements Closeable {

    private Cursor mCursor;

    public Message(@NonNull Cursor cursor) {
        if (cursor.getCount() != 1)
            throw new IllegalArgumentException("Do not initialize Message with a Cursor representing multiple rows");
        mCursor = cursor;
    }

    public int getId() {
        return mCursor.getInt(mCursor.getColumnIndex(MessageTable.id));
    }

    public String getBody() {
        return mCursor.getString(mCursor.getColumnIndex(MessageTable.body));
    }

    public byte[] getSignature() {
        return mCursor.getBlob(mCursor.getColumnIndex(MessageTable.signature));
    }

    @Nullable
    public Peer getSender(Context context) {
        Cursor sender = ChatApp.getPeerById(context, mCursor.getInt(mCursor.getColumnIndex(MessageTable.peerId)));
        if (sender != null && sender.moveToFirst()) {
            return new Peer(sender);
        }
        return null;
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

    @Override
    public void close() throws IOException {
        mCursor.close();
    }
}
