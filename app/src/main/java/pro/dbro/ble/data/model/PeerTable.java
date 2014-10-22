package pro.dbro.ble.data.model;

import net.simonvt.schematic.annotation.AutoIncrement;
import net.simonvt.schematic.annotation.DataType;
import net.simonvt.schematic.annotation.NotNull;
import net.simonvt.schematic.annotation.PrimaryKey;

import static net.simonvt.schematic.annotation.DataType.Type.BLOB;
import static net.simonvt.schematic.annotation.DataType.Type.INTEGER;
import static net.simonvt.schematic.annotation.DataType.Type.TEXT;

/**
 * Created by davidbrodsky on 7/28/14.
 */
public interface PeerTable {

    /** SQL type        Modifiers                   Reference Name            SQL Column Name */
    @DataType(INTEGER)  @PrimaryKey @AutoIncrement  String id               = "_id";
    @DataType(TEXT)     @NotNull                    String alias            = "alias";
    @DataType(TEXT)     @NotNull                    String lastSeenDate     = "last_seen";
    @DataType(BLOB)     @NotNull                    String pubKey           = "pk";
    @DataType(BLOB)                                 String secKey           = "sk";
    @DataType(BLOB)                                 String rawPkt           = "pkt";

    /**
     * NOTE TO FUTURE SELF
     * REMOVE THIS RAWPACKET GARBAGIO
     * ADD ALL FIELDS REQUIRED TO GENERATE PACKET
     * AND DO THAT.
     *
     * The issue with storing rawPacket is that when constructing
     * an OwnedIdentity we dont have access to this, nor do we
     * necessary have a handle of the current ChatProtocol,
     * which dictates how the packet should be created.
     *
     * Any class that needs access to the packet data should have
     * a handle on a Protocol, which has the serializeIdentity(OwnedIdentity)
     *
     * OK NEW FINAL DECISION TIME
     * For remote peer Identities it makes sense to store and forward the original response
     * since it should be considered immutable.
     * For owned Identities it makes sense to generate it each time as our alias etc. might change
     *
     *
     * SCHEME
     *
     * Messages will always have rawPkt available. When user sends message, store serialized packet
     * immediately.
     *
     * Identities may not have rawPkt (e.g: If they're inferred from a message), but generally
     * we should have the peer's identity before exchanging messages so we run our redundant
     * delivery avoidance algo.
     *
     */
}
