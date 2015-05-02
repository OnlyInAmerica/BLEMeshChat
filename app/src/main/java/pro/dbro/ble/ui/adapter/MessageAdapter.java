package pro.dbro.ble.ui.adapter;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import pro.dbro.ble.R;
import pro.dbro.ble.data.DataStore;
import pro.dbro.ble.data.model.DataUtil;
import pro.dbro.ble.data.model.MessageTable;
import pro.dbro.ble.data.model.Peer;

/**
 * Created by davidbrodsky on 10/19/14.
 */
public class MessageAdapter extends RecyclerViewCursorAdapter<MessageAdapter.ViewHolder> {
    public static final String TAG = "MessageAdapter";

    public static interface MessageSelectedListener {
        public void onMessageSelected(View identiconView, View usernameView, int messageId, int peerId);
    }

    private DataStore mDataStore;
    private RecyclerView mHost;
    private MessageSelectedListener mListener;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public View container;
        public TextView senderView;
        public TextView messageView;
        public TextView authoredView;
        SymmetricIdenticon identicon;


        public ViewHolder(View v) {
            super(v);
            container = v;
            senderView = (TextView) v.findViewById(R.id.sender);
            messageView = (TextView) v.findViewById(R.id.messageBody);
            authoredView = (TextView) v.findViewById(R.id.authoredDate);
            identicon = (SymmetricIdenticon) v.findViewById(R.id.identicon);

        }
    }

    /**
     * Recommended constructor.
     *
     * @param context       The context
     * @param dataStore     The data backend
     * @param fromPeer      A Peer to show messages from, or null to show all messages
     * @param flags         Flags used to determine the behavior of the adapter;
     *                Currently it accept {@link #FLAG_REGISTER_CONTENT_OBSERVER}.
     */
    public MessageAdapter(@NonNull Context context,
                          @Nullable Peer fromPeer,
                          @NonNull DataStore dataStore,
                          @Nullable MessageSelectedListener listener,
                          int flags) {
        super(context,
                fromPeer == null ? dataStore.getRecentMessages().getCursor() :
                                   dataStore.getRecentMessagesByPeer(fromPeer).getCursor(), flags);
        mDataStore = dataStore;
        mListener = listener;
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        mHost = recyclerView;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, Cursor cursor) {
        holder.container.setTag(R.id.view_tag_msg_id, cursor.getInt(cursor.getColumnIndex(MessageTable.id)));
        // TODO: cache message sender alias to avoid additional query
        Peer peer = mDataStore.getPeerById(cursor.getInt(cursor.getColumnIndex(MessageTable.peerId)));
        if (peer != null) {
            holder.container.setTag(R.id.view_tag_peer_id, peer.getId());
            holder.senderView.setText(peer.getAlias());
            holder.identicon.show(new String(peer.getPublicKey()));
        } else {
            holder.senderView.setText("?");
            holder.identicon.show(UUID.randomUUID());
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
        changeCursor(mDataStore.getRecentMessages().getCursor());
        mHost.smoothScrollToPosition(0);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int i) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.message_item, parent, false);

        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null)
                    mListener.onMessageSelected(v.findViewById(R.id.identicon),
                                                v.findViewById(R.id.sender),
                                                (Integer) v.getTag(R.id.view_tag_msg_id),
                                                (Integer) v.getTag(R.id.view_tag_peer_id));
            }
        });
        // set the view's size, margins, paddings and layout parameters
        return new ViewHolder(v);
    }
}
