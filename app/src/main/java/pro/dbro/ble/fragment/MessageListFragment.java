package pro.dbro.ble.fragment;


import android.database.Cursor;
import android.os.Bundle;
import android.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import pro.dbro.ble.ChatApp;
import pro.dbro.ble.R;
import pro.dbro.ble.adapter.MessageAdapter;
import pro.dbro.ble.ble.BLEManagerCallback;
import pro.dbro.ble.model.Message;
import pro.dbro.ble.model.Peer;

/**
 * A simple {@link Fragment} subclass.
 */
public class MessageListFragment extends Fragment implements BLEManagerCallback {
    public static final String TAG = "MessageListFragment";

    RecyclerView mRecyclerView;
    MessageAdapter mAdapter;
    EditText mMessageEntry;

    public MessageListFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_message, container, false);
        mMessageEntry = (EditText) root.findViewById(R.id.messageEntry);
        mMessageEntry.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                sendMessage(v.getText().toString());
                v.setText("");
                return false;
            }
        });
        root.findViewById(R.id.sendMessageButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSendMessageButtonClick(v);
            }
        });
        Cursor messages = ChatApp.getMessagesToSend(getActivity());
        mRecyclerView = (RecyclerView) root.findViewById(R.id.recyclerView);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdapter = new MessageAdapter(getActivity(), messages, MessageAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
        mRecyclerView.setAdapter(mAdapter);
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
        Log.i(TAG, "Sending message " + message);
        // For now treat all messsages as public broadcast
        ChatApp.createBroadcastMessageResponseForString(getActivity(), message);
        mRecyclerView.smoothScrollToPosition(mAdapter.getItemCount());
    }
}
