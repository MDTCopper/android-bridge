package copper.bridge.art;

import android.app.*;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.*;

import copper.bridge.annotation.*;
import copper.bridge.gen.*;

/**
 * The text input dialog. Runs on the main thread and keeps nothing: everything the answer needs is captured by
 * the dialog's own listeners, so two dialogs cannot answer for each other. Only the dialog lives here -
 * raising the system keyboard belongs to the activity, because it hangs off the activity's view.
 */
public class TextInput {
    private final Activity activity;

    public TextInput(Activity activity) {
        this.activity = activity;
    }

    /** Shows the text input dialog. */
    @ArtPostHandler(callbacks = {"result(String)", "canceled()"})
    public void textInput(long request, String title, String message, String text, boolean numeric,
                          boolean multiline, int maxLength, boolean allowEmpty) {
        EditText field = new EditText(activity);
        field.setText(text == null ? "" : text);
        if (numeric)
            field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | InputType.TYPE_NUMBER_FLAG_SIGNED);
        if (multiline)
            field.setInputType(field.getInputType() | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        if (maxLength > 0)
            field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});

        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setView(field)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null);
        if (title != null && !title.isEmpty())
            builder.setTitle(title);
        if (message != null && !message.isEmpty())
            builder.setMessage(message);

        AlertDialog dialog = builder.create();
        final boolean[] reported = {false};
        // Back and a tap outside are cancellations like any other, and they are the only paths that
        // do not go through one of the buttons.
        dialog.setOnCancelListener(d -> reportTextInput(reported, request, null));
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = field.getText().toString();
            // An empty answer is only accepted when the caller allowed it; otherwise the dialog stays
            // open, which is the only way to tell the user that something has to be typed.
            if (value.isEmpty() && !allowEmpty)
                return;
            reportTextInput(reported, request, value);
            dialog.dismiss();
        });
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
            reportTextInput(reported, request, null);
            dialog.dismiss();
        });
    }

    /** Reports a text input outcome once, whichever way the dialog ended. */
    private void reportTextInput(boolean[] reported, long request, String value) {
        if (reported[0])
            return;
        reported[0] = true;
        if (value == null)
            ArtCall.textInputCanceled(request);
        else
            ArtCall.textInputResult(request, value);
    }
}
