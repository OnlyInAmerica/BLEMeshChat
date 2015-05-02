package pro.dbro.ble.ui.fragment;


import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.app.SharedElementCallback;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.transition.Transition;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import java.util.List;

import pro.dbro.ble.R;
import pro.dbro.ble.data.DataStore;
import pro.dbro.ble.ui.adapter.MessageAdapter;

/**
 * A Fragment that currently allows chatting only in the public broadcast mode
 * ala Twitter.
 */
public class MessagingFragment extends Fragment implements MessageAdapter.MessageSelectedListener {
    public static final String TAG = "MessageListFragment";

    public static interface ChatFragmentCallback {
        public void onMessageSendRequested(String message);
        public void onMessageSelected(View identiconView, View usernameView, int messageId, int peerId);
    }

    private ChatFragmentCallback mCallback;
    DataStore mDataStore;
    RecyclerView mRecyclerView;
    MessageAdapter mAdapter;
    EditText mMessageEntry;
    View mRoot;

    public MessagingFragment() {
        // Required empty public constructor
    }

    public void setDataStore(DataStore dataStore) {
        mDataStore = dataStore;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        if (mDataStore == null)
            throw new IllegalStateException("MessageListFragment must be equipped with a DataStore. Did you call #setDataStore");

        // Inflate the layout for this fragment
        mRoot = inflater.inflate(R.layout.fragment_message, container, false);
        mMessageEntry = (EditText) mRoot.findViewById(R.id.messageEntry);
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
        mRoot.findViewById(R.id.sendMessageButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSendMessageButtonClick(v);
            }
        });
        mRecyclerView = (RecyclerView) mRoot.findViewById(R.id.recyclerView);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdapter = new MessageAdapter(getActivity(), null, mDataStore, this, MessageAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
        mRecyclerView.setAdapter(mAdapter);
        return mRoot;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mCallback = (ChatFragmentCallback) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement ChatFragmentCallback");
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
        mCallback.onMessageSendRequested(message);

    }

    @Override
    public void onMessageSelected(View identiconView, View usernameView, int messageId, int peerId) {
        mCallback.onMessageSelected(identiconView, usernameView, messageId, peerId);
    }

    public void animateIn() {
        mRoot.setAlpha(0);
        ObjectAnimator animator = ObjectAnimator.ofFloat(mRoot, "alpha", 0f, 1f)
                .setDuration(300);

        animator.setStartDelay(550);
        animator.start();
    }
}
