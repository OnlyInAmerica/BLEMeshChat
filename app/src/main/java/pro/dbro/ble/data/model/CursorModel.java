package pro.dbro.ble.data.model;

import android.database.Cursor;
import android.support.annotation.NonNull;

import java.io.Closeable;
import java.io.IOException;

/**
 * Created by davidbrodsky on 10/20/14.
 */
public abstract class CursorModel implements Closeable{

    protected Cursor mCursor;

    /**
     * Use this constructor if you intend to immediately access model data.
     * @param cursor A cursor that is already moved to the row corresponding to the desired model instance
     * @param isCollection whether this model represents a collection of items
     */
    public CursorModel(@NonNull Cursor cursor, boolean isCollection) {
        if (!isCollection && cursor.getCount() != 1)
            throw new IllegalArgumentException("Do not initialize CursorModel with a Cursor representing multiple rows");
        mCursor = cursor;
    }

    @Override
    public void close() throws IOException {
        if (mCursor != null) {
            mCursor.close();
        }
    }
}
