package pro.dbro.ble.ui.activities;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.Toolbar;
import android.transition.Slide;
import android.transition.TransitionSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.nispok.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;

import butterknife.ButterKnife;
import butterknife.InjectView;
import hugo.weaving.DebugLog;
import im.delight.android.identicons.SymmetricIdenticon;
import pro.dbro.airshare.app.AirShareService;
import pro.dbro.airshare.app.ui.AirShareFragment;
import pro.dbro.ble.ChatClient;
import pro.dbro.ble.ChatPeerFlow;
import pro.dbro.ble.PrefsManager;
import pro.dbro.ble.R;
import pro.dbro.ble.data.model.Peer;
import pro.dbro.ble.protocol.OwnedIdentityPacket;
import pro.dbro.ble.ui.Notification;
import pro.dbro.ble.ui.adapter.StatusArrayAdapter;
import pro.dbro.ble.ui.fragment.MessagingFragment;
import pro.dbro.ble.ui.fragment.ProfileFragment;
import pro.dbro.ble.ui.fragment.WelcomeFragment;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements LogConsumer,
        WelcomeFragment.WelcomeFragmentCallback,
        AirShareFragment.AirShareCallback,
        MessagingFragment.ChatFragmentCallback, ChatClient.Callback {

    public static final String TAG = "MainActivity";

    private ActionBarDrawerToggle mDrawerToggle;
    private MessagingFragment mMessagingFragment;
    private OwnedIdentityPacket mUserIdentity;

    private ChatClient mClient;
    private AirShareFragment mAirShareFragment;

    private Palette mPalette;

//    private PeerAdapter mPeerAdapter;

    @InjectView(R.id.status_spinner)
    Spinner mStatusSpinner;

    @InjectView(R.id.log)
    TextView mLogView;

//    @InjectView(R.id.peer_recyclerview)
//    RecyclerView mPeerRecyclerView;

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
                switch (position) {

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

        setSupportActionBar(mToolbar);
        setTitle(getString(R.string.public_feed));
        mToolbar.setTitleTextColor(getResources().getColor(android.R.color.white));
//        mToolbar.setNavigationIcon(R.drawable.ic_drawer);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawer,
                mToolbar, R.string.drawer_open, R.string.drawer_close) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                refreshProfileStats();
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };

        // Override ActionB
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getSupportFragmentManager().getBackStackEntryCount() == 0)
                    mDrawer.openDrawer(Gravity.START);
                else
                    getSupportFragmentManager().popBackStack();
            }
        });

        mDrawer.setDrawerListener(mDrawerToggle);
        mDrawerToggle.syncState();

        if (mAirShareFragment == null) {
            mAirShareFragment = AirShareFragment.newInstance(this);
            Timber.d("Adding airshare frag");
            getSupportFragmentManager().beginTransaction()
                    .add(mAirShareFragment, "airshare")
                    .commit();
        }

//        mPeerAdapter = new PeerAdapter(this, new ArrayList<Peer>());
//        mPeerRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
//        mPeerRecyclerView.setAdapter(mPeerAdapter);

        getSupportFragmentManager().addOnBackStackChangedListener(new android.support.v4.app.FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                int numEntries = getSupportFragmentManager().getBackStackEntryCount();
                if (numEntries == 0) {
                    mMessagingFragment.animateIn();
                    tintSystemBars(mPalette.getVibrantColor(R.color.primary), mPalette.getDarkVibrantColor(R.color.primaryDark),
                            getResources().getColor(R.color.primary), getResources().getColor(R.color.primaryDark));

                    // Hack animate the drawer icon
                    ValueAnimator drawerAnimator = ValueAnimator.ofFloat(1f, 0f);
                    drawerAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            mDrawerToggle.onDrawerSlide(null, (Float) animation.getAnimatedValue());
                        }
                    });
                    drawerAnimator.start();
                    setTitle(getString(R.string.public_feed));
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
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, mMessagingFragment, "messaging")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit();

        mProfileIdenticon.show(new String(mUserIdentity.publicKey));
        ((TextView) findViewById(R.id.profile_name)).setText(mUserIdentity.alias);
    }

    private void refreshProfileStats() {
        mPeersMetCount.setText(String.valueOf(Math.max(0, mClient.getDataStore().countPeers() - 1))); //ignore self
        mMessagesPassedCount.setText(String.valueOf(mClient.getDataStore().countMessagesPassed()));
    }

    /**
     * LogConsumer interface
     */

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

            mToolbar.setVisibility(View.GONE);
            getWindow().setStatusBarColor(getResources().getColor(R.color.welcome_status_bar));
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, new WelcomeFragment())
                    .commit();

//            View dialogView = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE))
//                    .inflate(R.layout.dialog_welcome, null);
//            final EditText aliasEntry = ((EditText) dialogView.findViewById(R.id.aliasEntry));
//
//            AlertDialog.Builder builder = new AlertDialog.Builder(this);
//            final AlertDialog dialog = builder.setTitle(getString(R.string.dialog_welcome_greeting))
//                    .setView(dialogView)
//                    .setPositiveButton(getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
//                        @Override
//                        public void onClick(DialogInterface dialogInterface, int i) {
//                            mNewUsername = aliasEntry.getText().toString();
//                        }
//                    })
//                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
//                        @Override
//                        public void onDismiss(DialogInterface dialog) {
//                            if (mNewUsername != null) {
//                                mClient.createPrimaryIdentity(mNewUsername);
//                                mAirShareFragment.registerUserForService(aliasEntry.getText().toString(), ChatClient.AIRSHARE_SERVICE_NAME);
//                                mNewUsername = null;
//                            }
//
//                            // TODO If user didn't select a username we should respond appropriately
//                        }
//                    })
//                    .show();
//
//            aliasEntry.setOnEditorActionListener(new TextView.OnEditorActionListener() {
//                @Override
//                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
//                    mNewUsername = textView.getText().toString();
//                    dialog.dismiss();
//                    return false;
//                }
//            });
        }
    }

    @Override
    public void onServiceReady(AirShareService.ServiceBinder serviceBinder) {
        mUserIdentity = (OwnedIdentityPacket) mClient.getPrimaryLocalPeer().getIdentity();

        mClient.setAirShareServiceBinder(serviceBinder);
        mClient.setCallback(this);
        mClient.makeAvailable();
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

        setTitle(peer.getAlias());

//        identictionView.setTransitionName(getString(R.string.identicon_transition_name));
//        usernameView.setTransitionName(getString(R.string.username_transition_name));

        Fragment profileFragment = ProfileFragment.createForPeer(mClient.getDataStore(), peer);

//        final TransitionSet sharedElementTransition = new TransitionSet();
//        sharedElementTransition.addTransition(new ChangeBounds());
//        sharedElementTransition.addTransition(new ChangeTransform());
//        sharedElementTransition.setInterpolator(new AccelerateDecelerateInterpolator());
//        sharedElementTransition.setDuration(200);

        final TransitionSet slideTransition = new TransitionSet();
        slideTransition.addTransition(new Slide());
        slideTransition.setInterpolator(new AccelerateDecelerateInterpolator());
        slideTransition.setDuration(300);

        profileFragment.setEnterTransition(slideTransition);
        profileFragment.setReturnTransition(slideTransition);
//        profileFragment.setSharedElementEnterTransition(sharedElementTransition);
        profileFragment.setAllowEnterTransitionOverlap(false);
        profileFragment.setAllowReturnTransitionOverlap(false);

        // Message fragment performs an exit when Profile is added, and an enter when profile is popped
//        getFragmentManager().findFragmentByTag("messaging").setReenterTransition(slideTransition);
//        getFragmentManager().findFragmentByTag("messaging").setExitTransition(slideTransition);
//        getFragmentManager().findFragmentByTag("messaging").setSharedElementEnterTransition(sharedElementTransition);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, profileFragment)
                .addToBackStack("profile")
//                .addSharedElement(identictionView, getString(R.string.identicon_transition_name))
//                .addSharedElement(usernameView, getString(R.string.username_transition_name))
                .commit();

        Bitmap bitmap = Notification.loadBitmapFromView(identictionView, 100, 100);
        Palette.generateAsync(bitmap, new Palette.PaletteAsyncListener() {
            public void onGenerated(Palette p) {
                mPalette = p;
                tintSystemBars(getResources().getColor(R.color.primary), getResources().getColor(R.color.primaryDark),
                        p.getVibrantColor(R.color.primary), p.getDarkVibrantColor(R.color.primaryDark));

            }
        });

        // Hack animate the drawer icon
        ValueAnimator drawerAnimator = ValueAnimator.ofFloat(0, 1f);
        drawerAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mDrawerToggle.onDrawerSlide(null, (Float) animation.getAnimatedValue());
            }
        });
        drawerAnimator.start();
    }

    @Override
    public void onAppPeerStatusUpdated(@NonNull Peer remotePeer, @NonNull ChatPeerFlow.Callback.ConnectionStatus status) {
        Snackbar.with(getApplicationContext())
                .position(Snackbar.SnackbarPosition.TOP)
                .text(String.format("%s %s",
                        remotePeer.getAlias(),
                        status == ChatPeerFlow.Callback.ConnectionStatus.CONNECTED ? "connected" : "disconnected"))
                .show((ViewGroup) findViewById(R.id.container));

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

    private void tintSystemBars(final int toolbarFromColor, final int statusbarFromColor,
                                final int toolbarToColor, final int statusbarToColor) {

        ValueAnimator toolbarAnim = ValueAnimator.ofArgb(toolbarFromColor, toolbarToColor);
        ValueAnimator statusbarAnim = ValueAnimator.ofArgb(statusbarFromColor, statusbarToColor);

        statusbarAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                getWindow().setStatusBarColor((Integer) animation.getAnimatedValue());
            }
        });

        toolbarAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                getSupportActionBar().setBackgroundDrawable(new ColorDrawable((Integer) animation.getAnimatedValue()));
            }
        });

        toolbarAnim.setDuration(500).start();
        statusbarAnim.setDuration(500).start();
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onNameChosen(String name) {
        mToolbar.setVisibility(View.VISIBLE);
        getWindow().setStatusBarColor(getResources().getColor(R.color.primaryDark));
        mClient.createPrimaryIdentity(name);
        mAirShareFragment.registerUserForService(name, ChatClient.AIRSHARE_SERVICE_NAME);
    }
}
