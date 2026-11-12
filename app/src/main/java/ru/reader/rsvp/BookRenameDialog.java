package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputFilter;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;
import java.util.function.Predicate;

/** Shared rename UI for the reader library, audio/comic library and downloads. */
final class BookRenameDialog {
    private BookRenameDialog() { }

    /** The callback returns true only when the label was saved successfully. */
    static void show(Activity activity, String current, Predicate<String> save) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(120) });
        int padding = (int) (18 * activity.getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);
        input.setText(current == null ? "" : current);
        input.setSelectAllOnFocus(true);
        input.requestFocus();

        AlertDialog dialog = new SwipeDialogs.Builder(activity)
            .setTitle("\u041f\u0435\u0440\u0435\u0438\u043c\u0435\u043d\u043e\u0432\u0430\u0442\u044c \u043a\u043d\u0438\u0433\u0443")
            .setView(input)
            .setPositiveButton("\u0421\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c", null)
            .setNegativeButton("\u041e\u0442\u043c\u0435\u043d\u0430", null)
            .create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = BookTitleStore.clean(input.getText().toString());
            if (value.isEmpty()) {
                input.setError("\u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u043d\u0430\u0437\u0432\u0430\u043d\u0438\u0435");
                return;
            }
            if (save.test(value)) dialog.dismiss();
            else Toast.makeText(activity, "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0441\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c \u043d\u0430\u0437\u0432\u0430\u043d\u0438\u0435", Toast.LENGTH_LONG).show();
        });
    }
}
