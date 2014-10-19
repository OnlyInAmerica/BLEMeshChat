package pro.dbro.ble.activities;

import android.app.Activity;
import android.app.ActionBar;
import android.app.Fragment;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;

import pro.dbro.ble.ChatApp;
import pro.dbro.ble.R;
import pro.dbro.ble.ble.BLEManagerCallback;
import pro.dbro.ble.ble.BLEMeshManager;
import pro.dbro.ble.fragment.PeerFragment;
import pro.dbro.ble.model.Message;
import pro.dbro.ble.model.Peer;

public class MainActivity extends Activity implements PeerFragment.PeerFragmentListener, BLEManagerCallback {

    private BLEMeshManager mMeshManager;
    private PeerFragment mPeerFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            mPeerFragment = new PeerFragment();
            getFragmentManager().beginTransaction()
                    .add(R.id.container, mPeerFragment)
                    .commit();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mMeshManager == null) {
            mMeshManager = new BLEMeshManager(this, ChatApp.getPrimaryIdentity(this));
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

    @Override
    public void onFragmentInteraction(Uri uri) {

    }

    @Override
    public void onPeerStatusChange(Peer peer, PeerStatus status) {
        if (mPeerFragment != null) {
            mPeerFragment.onPeerStatusChange(peer, status);
        }
    }

    @Override
    public void onMessageReceived(Message incomingMsg) {
        if (mPeerFragment != null) {
            mPeerFragment.onMessageReceived(incomingMsg);
        }
    }
}
