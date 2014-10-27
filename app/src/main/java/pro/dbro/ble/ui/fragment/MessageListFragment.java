package pro.dbro.ble.ui.fragment;


import android.app.Fragment;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import im.delight.android.identicons.SymmetricIdenticon;
import pro.dbro.ble.ChatApp;
import pro.dbro.ble.R;
import pro.dbro.ble.data.model.Message;
import pro.dbro.ble.data.model.MessageCollection;
import pro.dbro.ble.data.model.Peer;
import pro.dbro.ble.transport.ble.BLETransportCallback;
import pro.dbro.ble.ui.activities.MainActivity;
import pro.dbro.ble.ui.adapter.MessageAdapter;

/**
 * A simple {@link Fragment} subclass.
 */
public class MessageListFragment extends Fragment implements BLETransportCallback {
    public static final String TAG = "MessageListFragment";

    ChatApp mApp;
    RecyclerView mRecyclerView;
    MessageAdapter mAdapter;
    EditText mMessageEntry;

    public MessageListFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mApp = ((MainActivity) getActivity()).mApp;
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_message, container, false);
        mMessageEntry = (EditText) root.findViewById(R.id.messageEntry);
        mMessageEntry.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendMessage(v.getText().toString());
                    v.setText("");
                    return true;
                }
                return false;
            }
        });
        root.findViewById(R.id.sendMessageButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSendMessageButtonClick(v);
            }
        });
        mRecyclerView = (RecyclerView) root.findViewById(R.id.recyclerView);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdapter = new MessageAdapter(getActivity(), mApp, MessageAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.scrollToPosition(mAdapter.getItemCount());
        return root;
    }

    @Override
    public void onPeerStatusChange(Peer peer, PeerStatus status) {

    }

    @Override
    public void onMessageReceived(Message incomingMsg) {
        Log.i(TAG, "message received: " + incomingMsg.getBody());
        // Cursor observer should handle this, unless
        // no messages were available onCreateView
        if (mRecyclerView == null) {

        }
    }

    public void onSendMessageButtonClick(View v) {
        sendMessage(mMessageEntry.getText().toString());
        mMessageEntry.setText("");
    }

    private void sendMessage(String message) {
        if (message.length() == 0) return;
        Log.i(TAG, "Sending message " + message);
        // For now treat all messsages as public broadcast
        mApp.sendPublicMessageFromPrimaryIdentity(message);
        mRecyclerView.smoothScrollToPosition(mAdapter.getItemCount());
    }
}
