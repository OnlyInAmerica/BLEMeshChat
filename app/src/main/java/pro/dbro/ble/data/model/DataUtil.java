package pro.dbro.ble.data.model;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Utilities for converting between Java and Database friendly types
 *
 * Created by davidbrodsky on 10/13/14.
 */
public class DataUtil {

    public static SimpleDateFormat storedDateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    /**
     * When we query rows by a BLOB column we must
     * convert the BLOB to its String hex form
     * see:
     * http://www.sqlite.org/lang_expr.html#litvalue
     */
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        String rawHex = new String(hexChars);
        String blobLiteral = "X'" + rawHex + "'";
        return blobLiteral;
    }

}
