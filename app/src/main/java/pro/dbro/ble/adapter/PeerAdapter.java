package pro.dbro.ble.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

import pro.dbro.ble.R;
import pro.dbro.ble.model.Message;
import pro.dbro.ble.model.Peer;

/**
 * Created by davidbrodsky on 10/12/14.
 */
public class PeerAdapter extends RecyclerView.Adapter<PeerAdapter.ViewHolder> {
    private Context mContext;
    private ArrayList<Peer> mPeers;

    // Provide a reference to the type of views that you are using
    // (custom viewholder)
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView mTextView;
        public ViewHolder(View v) {
            super(v);
            mTextView = (TextView) v.findViewById(R.id.text);
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public PeerAdapter(Context context, ArrayList<Peer> peers) {
        mPeers = peers;
        mContext = context;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public PeerAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                   int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.peer_item, parent, false);
        // set the view's size, margins, paddings and layout parameters
        ViewHolder vh = new ViewHolder(v);
//        vh.mSenderView.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                // TODO
//            }
//        });
        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        holder.mTextView.setText(mPeers.get(position).getAlias());

    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mPeers.size();
    }

    public void notifyPeerAdded(Peer peer) {
        mPeers.add(peer);
        notifyItemInserted(mPeers.size()-1);
    }

    public void notifyPeerRemoved(Peer peer) {
        int idx = mPeers.indexOf(peer);
        if (idx != -1) {
            mPeers.remove(idx);
            notifyItemRemoved(idx);
        }
    }

    public void notifyMessageReceived(Message message) {
        Peer peer = message.getSender(mContext);
        if (peer != null) {
            int oldIdx = mPeers.indexOf(peer);
            if (oldIdx != -1 ) {
                mPeers.remove(peer);
                mPeers.add(0, peer);
                notifyItemMoved(oldIdx, 0);
            }
        }
    }
}