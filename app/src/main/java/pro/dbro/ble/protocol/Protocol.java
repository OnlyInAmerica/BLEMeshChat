package pro.dbro.ble.protocol;

import android.support.annotation.NonNull;

/**
 * Created by davidbrodsky on 10/20/14.
 */
public interface Protocol {

    /** Outgoing
     *
     * Serialize Protocol Objects to raw transmission data
    **/

    // TODO Decide on a consistent API here
    public byte[] serializeIdentity(@NonNull OwnedIdentity ownedIdentity);

    public Message serializeMessage(@NonNull OwnedIdentity ownedIdentity, String body);

    /** Incoming
     *
     * Deserialize raw transmission data into Protocol Objects
     */

    public Identity deserializeIdentity(@NonNull byte[] identity);

    public Message deserializeMessage(@NonNull byte[] message);

}
