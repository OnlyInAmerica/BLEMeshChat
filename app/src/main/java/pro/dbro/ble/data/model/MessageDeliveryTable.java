package pro.dbro.ble.data.model;

import net.simonvt.schematic.annotation.AutoIncrement;
import net.simonvt.schematic.annotation.DataType;
import net.simonvt.schematic.annotation.NotNull;
import net.simonvt.schematic.annotation.PrimaryKey;

import static net.simonvt.schematic.annotation.DataType.Type.INTEGER;

/**
 * Used to avoid sending a single messages to a particular client multiple times
 *
 * Created by davidbrodsky on 7/28/14.
 */
public interface MessageDeliveryTable {

    /** SQL type        Modifiers                   Reference Name            SQL Column Name */
    @DataType(INTEGER)  @PrimaryKey @AutoIncrement  String messageId           = "m_id";
    @DataType(INTEGER)  @NotNull                    String peerId              = "p_id";
}
