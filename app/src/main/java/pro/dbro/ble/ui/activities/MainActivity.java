package pro.dbro.ble.ui.activities;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import pro.dbro.ble.ChatApp;
import pro.dbro.ble.R;
import pro.dbro.ble.data.model.Message;
import pro.dbro.ble.data.model.Peer;
import pro.dbro.ble.transport.ble.BLETransportOld;
import pro.dbro.ble.transport.ble.BLETransportCallback;
import pro.dbro.ble.ui.fragment.MessageListFragment;

public class MainActivity extends Activity implements /* PeerListFragment.PeerFragmentListener,*/ BLETransportCallback {

    private BLETransportOld mMeshManager;
    //private PeerListFragment mPeerListFragment;
    private MessageListFragment mMessageListFragment;
    private Peer mUserIdentity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayShowTitleEnabled(false);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            /*
            mPeerListFragment = new PeerListFragment();
            getFragmentManager().beginTransaction()
                    .add(R.id.container, mPeerListFragment)
                    .commit();
            */
            mUserIdentity = ChatApp.getPrimaryLocalPeer(this);
            if (mUserIdentity == null) {
                Util.showWelcomeDialog(this, new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        mUserIdentity = ChatApp.getPrimaryLocalPeer(MainActivity.this);
                        addMessageListFragment();
                    }
                });
            } else {
                addMessageListFragment();
            }

        }
    }

    private void addMessageListFragment() {
        mMessageListFragment = new MessageListFragment();
        getFragmentManager().beginTransaction()
                .add(R.id.container, mMessageListFragment)
                .commit();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mMeshManager == null) {
            mMeshManager = new BLETransportOld(this, ChatApp.getPrimaryLocalPeer(this));
            mMeshManager.setMeshCallback(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mMeshManager != null) {
            mMeshManager.stop();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

//    @Override
//    public void onFragmentInteraction(Uri uri) {
//
//    }

    @Override
    public void onPeerStatusChange(Peer peer, PeerStatus status) {
//        if (mPeerListFragment != null) {
//            mPeerListFragment.onPeerStatusChange(peer, status);
//        }
    }

    @Override
    public void onMessageReceived(Message incomingMsg) {
//        if (mPeerListFragment != null) {
//            mPeerListFragment.onMessageReceived(incomingMsg);
//        }

    }
}
