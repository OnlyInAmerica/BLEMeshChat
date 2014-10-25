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
     */
    public CursorModel(@NonNull Cursor cursor) {
        mCursor = cursor;
    }

    @Override
    public void close() {
        if (mCursor != null) {
            mCursor.close();
        }
    }
}
