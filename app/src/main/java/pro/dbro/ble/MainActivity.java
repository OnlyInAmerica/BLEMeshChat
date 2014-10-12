package pro.dbro.ble;

import android.app.Activity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

import butterknife.ButterKnife;
import butterknife.InjectView;
import pro.dbro.ble.adapter.BleClientAdapter;
import pro.dbro.ble.ble.BLECentral;
import pro.dbro.ble.ble.BLEPeripheral;


public class MainActivity extends Activity implements CompoundButton.OnCheckedChangeListener, LogConsumer {

    BLECentral mScanner;
    BLEPeripheral mAdvertiser;

    @InjectView(R.id.scanToggle)      ToggleButton mScanToggle;
    @InjectView(R.id.advertiseToggle) ToggleButton mAdvertiseToggle;
    @InjectView(R.id.recyclerView)    RecyclerView mRecyclerView;
//    @InjectView(R.id.log)             TextView mLog;

    private BleClientAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mScanner = new BLECentral(this);
        mScanner.setLogConsumer(this);
        mAdvertiser = new BLEPeripheral(this);

        ButterKnife.inject(this);
        mScanToggle.setOnCheckedChangeListener(this);
        mAdvertiseToggle.setOnCheckedChangeListener(this);

        mAdapter = new BleClientAdapter(new String[] {"Device1", "Device2"});
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(mAdapter);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_clear) {
//            mLog.setText("");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mScanner.isIsScanning()) mScanner.stop();

        if (mAdvertiser.isAdvertising()) mAdvertiser.stop();
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
        if (compoundButton == mScanToggle)
        {
            if (checked)
                mScanner.start();
            else
                mScanner.stop();
        }
        else if (compoundButton == mAdvertiseToggle)
        {
            if (checked)
                mAdvertiser.start();
            else
                mAdvertiser.stop();
        }
    }

    @Override
    public void onLogEvent(final String event) {
//        mLog.post(new Runnable() {
//            @Override
//            public void run() {
//                mLog.append(event + "\n");
//            }
//        });
    }
}
