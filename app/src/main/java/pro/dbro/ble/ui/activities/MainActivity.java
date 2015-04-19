package pro.dbro.ble.ui.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;

import butterknife.ButterKnife;
import butterknife.InjectView;
import im.delight.android.identicons.SymmetricIdenticon;
import pro.dbro.airshare.app.AirShareService;
import pro.dbro.airshare.app.ui.AirShareFragment;
import pro.dbro.ble.ChatClient;
import pro.dbro.ble.ChatPeerFlow;
import pro.dbro.ble.R;
import pro.dbro.ble.data.model.Peer;
import pro.dbro.ble.protocol.OwnedIdentityPacket;
import pro.dbro.ble.ui.adapter.PeerAdapter;
import pro.dbro.ble.ui.fragment.MessageListFragment;
import timber.log.Timber;

public class MainActivity extends Activity implements LogConsumer,
                                                      AirShareFragment.AirShareCallback,
                                                      MessageListFragment.ChatFragmentCallback, ChatClient.Callback {

    public static final String TAG = "MainActivity";

    private MessageListFragment mMessageListFragment;
    private OwnedIdentityPacket mUserIdentity;

    private ChatClient mClient;
    private AirShareFragment mAirShareFragment;

    private PeerAdapter mPeerAdapter;

    @InjectView(R.id.online_switch)
    Switch mOnlineSwitch;

    @InjectView(R.id.log)
    TextView mLogView;

    @InjectView(R.id.peer_recyclerview)
    RecyclerView mPeerRecyclerView;

    @InjectView(R.id.bg_mode_switch)
    CheckBox mBgModeCheckbox;

    private String mNewUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);

        mClient = new ChatClient(this);

//        mLogView.setOnLongClickListener(new View.OnLongClickListener() {
//            @Override
//            public boolean onLongClick(View view) {
//                mLogView.setText("");
//                return false;
//            }
//        });

        mBgModeCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mAirShareFragment.setShouldServiceContinueInBackground(isChecked);
            }
        });

        mOnlineSwitch.setEnabled(false);
        mOnlineSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                if (!checked) {
                    mClient.makeUnavailable();
                    mPeerAdapter.clearPeers();
                } else
                    mClient.makeAvailable();
            }
        });

        DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.my_drawer_layout);
        drawerLayout.setStatusBarBackground(R.color.primaryDark);

        if (mAirShareFragment == null) {
            mAirShareFragment = AirShareFragment.newInstance(this);
            Timber.d("Adding airshare frag");
            getFragmentManager().beginTransaction()
                                .add(mAirShareFragment, "airshare")
                                .commit();
        }

        mPeerAdapter = new PeerAdapter(this, new ArrayList<Peer>());
        mPeerRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        mPeerRecyclerView.setAdapter(mPeerAdapter);
    }

    /**
     * Adds the message list fragment and populates
     * the profile navigation drawer with the user profile
     */
    private void revealChatViews() {
        mMessageListFragment = new MessageListFragment();
        mMessageListFragment.setDataStore(mClient.getDataStore());
        getFragmentManager().beginTransaction()
                .add(R.id.container, mMessageListFragment)
                .commit();

        ((SymmetricIdenticon) findViewById(R.id.profile_identicon)).show(new String(mUserIdentity.publicKey));
        ((TextView) findViewById(R.id.profile_name)).setText(mUserIdentity.alias);
    }

    /** LogConsumer interface */

    @Override
    public void onLogEvent(final String event) {
        /*
        mLogView.post(new Runnable() {
            @Override
            public void run() {
                mLogView.append(event + "\n");

            }
        });
        */
    }

    @Override
    public void registrationRequired() {
        Peer localPeer = mClient.getPrimaryLocalPeer();
        if (localPeer != null) {

            // TODO : No reason (besides debugging) to expose application username to AirShare in this case. Add API endpoint excluding alias
            mAirShareFragment.registerUserForService(localPeer.getAlias(), ChatClient.AIRSHARE_SERVICE_NAME);

        } else {

            View dialogView = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                    .inflate(R.layout.dialog_welcome, null);
            final EditText aliasEntry = ((EditText) dialogView.findViewById(R.id.aliasEntry));

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            final AlertDialog dialog = builder.setTitle(getString(R.string.dialog_welcome_greeting))
                    .setView(dialogView)
                    .setPositiveButton(getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            mNewUsername = aliasEntry.getText().toString();
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            if (mNewUsername != null) {
                                mClient.createPrimaryIdentity(mNewUsername);
                                mAirShareFragment.registerUserForService(aliasEntry.getText().toString(), ChatClient.AIRSHARE_SERVICE_NAME);
                                mNewUsername = null;
                            }

                            // TODO If user didn't select a username we should respond appropriately
                        }
                    })
                    .show();

            aliasEntry.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    mNewUsername = textView.getText().toString();
                    dialog.dismiss();
                    return false;
                }
            });
        }
    }

    @Override
    public void onServiceReady(AirShareService.ServiceBinder serviceBinder) {
        mUserIdentity = (OwnedIdentityPacket) mClient.getPrimaryLocalPeer().getIdentity();

        mClient.setAirShareServiceBinder(serviceBinder);
        mClient.makeAvailable();
        mClient.setCallback(this);
        mOnlineSwitch.setChecked(true);
        mOnlineSwitch.setEnabled(true);
        revealChatViews();
    }

    @Override
    public void onFinished(Exception exception) {

    }

    @Override
    public void onMessageSendRequested(String message) {
        mClient.sendPublicMessageFromPrimaryIdentity(message);
    }

    @Override
    public void onAppPeerStatusUpdated(@NonNull Peer remotePeer, @NonNull ChatPeerFlow.Callback.ConnectionStatus status) {
//        Snackbar.with(getApplicationContext())
//                .text(String.format("%s %s",
//                                    remotePeer.getAlias(),
//                                    status == ChatPeerFlow.Callback.ConnectionStatus.CONNECTED ? "connected" : "disconnected"))
//        .show(this);

        switch (status) {
            case CONNECTED:
                mPeerAdapter.notifyPeerAdded(remotePeer);
                break;

            case DISCONNECTED:
                mPeerAdapter.notifyPeerRemoved(remotePeer);
                break;
        }
    }
}
