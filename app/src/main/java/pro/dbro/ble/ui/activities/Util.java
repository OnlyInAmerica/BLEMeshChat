package pro.dbro.ble.ui.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import pro.dbro.ble.ChatClient;
import pro.dbro.ble.R;

/**
 * Created by davidbrodsky on 10/13/14.
 */
public class Util {

    public static void showWelcomeDialog(@NonNull final ChatClient app, @NonNull final Context context, DialogInterface.OnDismissListener dismissListener) {
        View dialogView = ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                            .inflate(R.layout.dialog_welcome, null);
        final EditText aliasEntry = ((EditText) dialogView.findViewById(R.id.aliasEntry));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final AlertDialog dialog = builder.setTitle(context.getString(R.string.dialog_welcome_greeting))
                .setView(dialogView)
                .setPositiveButton(context.getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        app.createPrimaryIdentity(aliasEntry.getText().toString());
                    }
                })
                .setOnDismissListener(dismissListener)
                .show();
        aliasEntry.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                app.createPrimaryIdentity(textView.getText().toString());
                dialog.dismiss();
                return false;
            }
        });
    }
}
