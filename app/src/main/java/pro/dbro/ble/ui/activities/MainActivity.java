package pro.dbro.ble.ui.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.transition.ChangeBounds;
import android.transition.ChangeTransform;
import android.transition.Slide;
import android.transition.TransitionSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toolbar;

import com.nispok.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;

import butterknife.ButterKnife;
import butterknife.InjectView;
import im.delight.android.identicons.SymmetricIdenticon;
import pro.dbro.airshare.app.AirShareService;
import pro.dbro.airshare.app.ui.AirShareFragment;
import pro.dbro.ble.ChatClient;
import pro.dbro.ble.ChatPeerFlow;
import pro.dbro.ble.PrefsManager;
import pro.dbro.ble.R;
import pro.dbro.ble.data.model.Peer;
import pro.dbro.ble.protocol.OwnedIdentityPacket;
import pro.dbro.ble.ui.adapter.PeerAdapter;
import pro.dbro.ble.ui.adapter.StatusArrayAdapter;
import pro.dbro.ble.ui.fragment.MessagingFragment;
import pro.dbro.ble.ui.fragment.ProfileFragment;
import timber.log.Timber;

public class MainActivity extends Activity implements LogConsumer,
                                                      AirShareFragment.AirShareCallback,
                                                      MessagingFragment.ChatFragmentCallback, ChatClient.Callback {

    public static final String TAG = "MainActivity";

    private MessagingFragment mMessagingFragment;
    private OwnedIdentityPacket mUserIdentity;

    private ChatClient mClient;
    private AirShareFragment mAirShareFragment;

//    private PeerAdapter mPeerAdapter;

    @InjectView(R.id.status_spinner)
    Spinner mStatusSpinner;

    @InjectView(R.id.log)
    TextView mLogView;

    @InjectView(R.id.peer_recyclerview)
    RecyclerView mPeerRecyclerView;

    @InjectView(R.id.toolbar)
    Toolbar mToolbar;

    @InjectView(R.id.my_drawer_layout)
    DrawerLayout mDrawer;

    @InjectView(R.id.msg_pass_count)
    TextView mMessagesPassedCount;

    @InjectView(R.id.peers_met_count)
    TextView mPeersMetCount;

    @InjectView(R.id.profile_identicon)
    SymmetricIdenticon mProfileIdenticon;

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

        mStatusSpinner.setAdapter(new StatusArrayAdapter(this, new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.status_options)))));
        mStatusSpinner.setEnabled(false);
        mStatusSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch(position) {

                    case 0: // Always online
                        mClient.makeAvailable();
                        mAirShareFragment.setShouldServiceContinueInBackground(true);
                        break;

                    case 1: // Online when using app
                        mClient.makeAvailable();
                        mAirShareFragment.setShouldServiceContinueInBackground(false);
                        break;

                    case 2: // Offline
                        mClient.makeUnavailable();
//                        mPeerAdapter.clearPeers();
                        break;
                }
                PrefsManager.setStatus(MainActivity.this, position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });

        mToolbar.setNavigationIcon(R.drawable.ic_drawer);
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mDrawer.openDrawer(Gravity.START);
            }
        });

        mDrawer.setDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                // do nothing
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                refreshProfileStats();
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                // do nothing
            }

            @Override
            public void onDrawerStateChanged(int newState) {
                // do nothing
            }
        });

        if (mAirShareFragment == null) {
            mAirShareFragment = AirShareFragment.newInstance(this);
            Timber.d("Adding airshare frag");
            getFragmentManager().beginTransaction()
                                .add(mAirShareFragment, "airshare")
                                .commit();
        }

//        mPeerAdapter = new PeerAdapter(this, new ArrayList<Peer>());
//        mPeerRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
//        mPeerRecyclerView.setAdapter(mPeerAdapter);

        getFragmentManager().addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                int numEntries = getFragmentManager().getBackStackEntryCount();
                if (numEntries == 0) {
                    Log.d(TAG, "Animating in message fragment");
                    mMessagingFragment.animateIn();
                }
            }
        });
    }

    /**
     * Adds the message list fragment and populates
     * the profile navigation drawer with the user profile
     */
    private void revealChatViews() {
        mMessagingFragment = new MessagingFragment();
        mMessagingFragment.setDataStore(mClient.getDataStore());
        getFragmentManager().beginTransaction()
                .add(R.id.container, mMessagingFragment, "messaging")
                .commit();

        mProfileIdenticon.show(new String(mUserIdentity.publicKey));
        ((TextView) findViewById(R.id.profile_name)).setText(mUserIdentity.alias);
    }

    private void refreshProfileStats() {
        mPeersMetCount.setText(String.valueOf(Math.max(0, mClient.getDataStore().countPeers() - 1))); //ignore self
        mMessagesPassedCount.setText(String.valueOf(mClient.getDataStore().countMessagesPassed()));
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
        mClient.setCallback(this);
        mStatusSpinner.setEnabled(true);
        mStatusSpinner.setSelection(PrefsManager.getStatus(this));
        revealChatViews();
        refreshProfileStats();
    }

    @Override
    public void onFinished(Exception exception) {

    }

    @Override
    public void onMessageSendRequested(String message) {
        mClient.sendPublicMessageFromPrimaryIdentity(message);
    }

    @Override
    public void onMessageSelected(View identictionView, View usernameView, int messageId, int peerId) {
        // Create new fragment to add (Fragment B)
        Peer peer = mClient.getDataStore().getPeerById(peerId);
        if (peer == null) {
            Log.w(TAG, "Could not lookup peer. Cannot show profile");
            return;
        }

        identictionView.setTransitionName(getString(R.string.identicon_transition_name));
        usernameView.setTransitionName(getString(R.string.username_transition_name));

        Fragment profileFragment = ProfileFragment.createForPeer(mClient.getDataStore(), peer);

        final TransitionSet sharedElementTransition = new TransitionSet();
        sharedElementTransition.addTransition(new ChangeBounds());
        sharedElementTransition.addTransition(new ChangeTransform());
        sharedElementTransition.setInterpolator(new AccelerateDecelerateInterpolator());
        sharedElementTransition.setDuration(200);

        final TransitionSet slideTransition = new TransitionSet();
        slideTransition.addTransition(new Slide());
        slideTransition.setInterpolator(new AccelerateDecelerateInterpolator());
        slideTransition.setDuration(300);

        profileFragment.setEnterTransition(slideTransition);
        profileFragment.setReturnTransition(slideTransition);
        profileFragment.setSharedElementEnterTransition(sharedElementTransition);
        profileFragment.setAllowEnterTransitionOverlap(false);
        profileFragment.setAllowReturnTransitionOverlap(false);

        // Message fragment performs an exit when Profile is added, and an enter when profile is popped
//        getFragmentManager().findFragmentByTag("messaging").setReenterTransition(slideTransition);
//        getFragmentManager().findFragmentByTag("messaging").setExitTransition(slideTransition);
        getFragmentManager().findFragmentByTag("messaging").setSharedElementEnterTransition(sharedElementTransition);

        getFragmentManager().beginTransaction()
                .replace(R.id.container, profileFragment)
                .addToBackStack("profile")
                .addSharedElement(identictionView, getString(R.string.identicon_transition_name))
                .addSharedElement(usernameView, getString(R.string.username_transition_name))
                .commit();
    }

    @Override
    public void onAppPeerStatusUpdated(@NonNull Peer remotePeer, @NonNull ChatPeerFlow.Callback.ConnectionStatus status) {
        Snackbar.with(getApplicationContext())
                .text(String.format("%s %s",
                                    remotePeer.getAlias(),
                                    status == ChatPeerFlow.Callback.ConnectionStatus.CONNECTED ? "connected" : "disconnected"))
        .show(this);

//        switch (status) {
//            case CONNECTED:
//                mPeerAdapter.notifyPeerAdded(remotePeer);
//                break;
//
//            case DISCONNECTED:
//                mPeerAdapter.notifyPeerRemoved(remotePeer);
//                break;
//        }
    }
}
