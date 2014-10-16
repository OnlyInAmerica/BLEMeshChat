package pro.dbro.ble.model;

import android.net.Uri;

import net.simonvt.schematic.annotation.ContentProvider;
import net.simonvt.schematic.annotation.ContentUri;
import net.simonvt.schematic.annotation.TableEndpoint;

/**
 * ContentProvider definition. This defines a familiar API
 * for Android framework components to utilize.
 *
 * Created by davidbrodsky on 7/28/14.
 */
@ContentProvider(authority = ChatContentProvider.AUTHORITY, database = PeerDatabase.class)
public final class ChatContentProvider {

    public static final String AUTHORITY      = "pro.dbro.ble.chatprovider";
    private static final Uri BASE_CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    private static Uri buildUri(String... paths) {
        Uri.Builder builder = BASE_CONTENT_URI.buildUpon();
        for (String path : paths) {
            builder.appendPath(path);
        }
        return builder.build();
    }

    /** Peer API **/

    @TableEndpoint(table = PeerDatabase.PEERS)
    public static class Peers {

        private static final String ENDPOINT = "peers";

        @ContentUri(
                path = ENDPOINT,
                type = "vnd.android.cursor.dir/list",
                defaultSort = PeerTable.alias + " ASC")
        public static final Uri PEERS = buildUri(ENDPOINT);
    }

    /** Messages API **/

    @TableEndpoint(table = PeerDatabase.MESSAGES)
    public static class Messages {

        private static final String ENDPOINT = "camps";

        @ContentUri(
                path = ENDPOINT,
                type = "vnd.android.cursor.dir/list",
                defaultSort = MessageTable.authoredDate + " ASC")
        public static final Uri MESSAGES = buildUri(ENDPOINT);

    }
}
