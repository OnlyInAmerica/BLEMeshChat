package pro.dbro.ble.ui.adapter;

import android.content.Context;
import android.database.Cursor;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.ParseException;
import java.util.UUID;

import im.delight.android.identicons.SymmetricIdenticon;
import pro.dbro.ble.ChatApp;
import pro.dbro.ble.R;
import pro.dbro.ble.data.ContentProviderStore;
import pro.dbro.ble.data.DataStore;
import pro.dbro.ble.data.model.DataUtil;
import pro.dbro.ble.data.model.MessageCollection;
import pro.dbro.ble.data.model.MessageTable;
import pro.dbro.ble.data.model.Peer;

/**
 * Created by davidbrodsky on 10/19/14.
 */
public class MessageAdapter extends RecyclerViewCursorAdapter<MessageAdapter.ViewHolder> {
    public static final String TAG = "MessageAdapter";

    private ChatApp mApp;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView senderView;
        public TextView messageView;
        public TextView authoredView;
        SymmetricIdenticon mIdenticon;


        public ViewHolder(View v) {
            super(v);
            senderView = (TextView) v.findViewById(R.id.sender);
            messageView = (TextView) v.findViewById(R.id.messageBody);
            authoredView = (TextView) v.findViewById(R.id.authoredDate);
            mIdenticon = (SymmetricIdenticon) v.findViewById(R.id.identicon);

        }
    }

    /**
     * Recommended constructor.
     *
     * @param context       The context
     * @param app
     * @param flags         Flags used to determine the behavior of the adapter;
     *                Currently it accept {@link #FLAG_REGISTER_CONTENT_OBSERVER}.
     */
    public MessageAdapter(Context context, ChatApp app, int flags) {
        super(context, app.getRecentMessagesFeed().getCursor(), flags);
        mApp = app;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, Cursor cursor) {
        // TODO: cache message sender alias to avoid additional query

        Peer peer = mApp.getDataStore().getPeerById(cursor.getInt(cursor.getColumnIndex(MessageTable.peerId)));
        if (peer != null) {
            holder.senderView.setText(peer.getAlias());
            holder.mIdenticon.show(new String(peer.getPublicKey()));
        } else {
            holder.senderView.setText("?");
            holder.mIdenticon.show(UUID.randomUUID());
        }
        holder.messageView.setText(cursor.getString(cursor.getColumnIndex(MessageTable.body)));
        try {
            holder.authoredView.setText(DateUtils.getRelativeTimeSpanString(
                    DataUtil.storedDateFormatter.parse(cursor.getString(cursor.getColumnIndex(MessageTable.authoredDate))).getTime()));
        } catch (ParseException e) {
            holder.authoredView.setText("");
            e.printStackTrace();
        }
    }

    @Override
    protected void onContentChanged() {
        Log.i(TAG, "onContentChanged");
        changeCursor(mApp.getRecentMessagesFeed().getCursor());
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int i) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.message_item, parent, false);
        // set the view's size, margins, paddings and layout parameters
        return new ViewHolder(v);
    }
}
