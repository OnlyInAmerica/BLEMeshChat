package pro.dbro.ble.ui.adapter;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

import pro.dbro.ble.R;
import timber.log.Timber;

/**
 * Created by davidbrodsky on 4/20/15.
 */
public class StatusArrayAdapter extends ArrayAdapter<String> {

    public StatusArrayAdapter(Context context, ArrayList<String> statuses) {
        super(context, android.R.layout.simple_spinner_dropdown_item, statuses);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getCustomView(position, convertView, parent);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getCustomView(position, convertView, parent);
    }

    public View getCustomView(int position, View convertView, ViewGroup parent) {

        Context context = parent.getContext();

        // Get the data item for this position
        String status = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
            ((TextView) convertView).setCompoundDrawablePadding((int) dipToPixels(context, 8));
        }

        TextView statusLabel = (TextView) convertView;
        statusLabel.setText(status);

        String[] choices = context.getResources().getStringArray(R.array.status_options);
        if (status.equals(choices[0])) { // Always online
            statusLabel.setCompoundDrawablesWithIntrinsicBounds(context.getDrawable(R.drawable.status_always_online), null, null, null);
        }
        else if (status.equals(choices[1])) { // Online when using app
            statusLabel.setCompoundDrawablesWithIntrinsicBounds(context.getDrawable(R.drawable.status_online_in_foreground), null, null, null);
        } else if (status.equals(choices[2])) { // Offline
            statusLabel.setCompoundDrawablesWithIntrinsicBounds(context.getDrawable(R.drawable.status_offline), null, null, null);
        } else {
            Timber.e("Unknown status. Cannot set adapter view correctly");
            statusLabel.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        }

        return convertView;
    }

    public static float dipToPixels(Context context, float dipValue) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics);
    }
}
