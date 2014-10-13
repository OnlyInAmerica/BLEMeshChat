package pro.dbro.ble.model;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A thin model around a {@link android.database.Cursor}
 * that lazy-loads attributes as needed. As such, do
 * not to close the cursor fed to this class's constructor.
 * Instead call {@link #close}
 *
 * Created by davidbrodsky on 10/12/14.
 */
public class Message implements Closeable {

    private Cursor mCursor;

    public Message(@NonNull Cursor cursor) {
        mCursor = cursor;
    }

    public String getBody() {
        return mCursor.getString(mCursor.getColumnIndex(MessageTable.body));
    }

    @Nullable
    public Date getRelativeReceivedDate() {
        try {
            return DateUtil.storedDateFormatter.parse(
                    mCursor.getString(mCursor.getColumnIndex(MessageTable.receivedDate)));
        } catch (ParseException e) {
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        mCursor.close();
    }
}
