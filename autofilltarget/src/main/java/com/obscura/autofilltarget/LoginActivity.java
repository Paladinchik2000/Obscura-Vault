package com.obscura.autofilltarget;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.autofill.AutofillManager;
import android.widget.EditText;
import android.widget.TextView;

/**
 * A login form in an app that has nothing to do with Obscura.
 *
 * The test cannot read a password field — it is hidden from the accessibility tree — and this app
 * is not allowed to talk to Obscura directly: any interaction could give the two packages
 * visibility of each other, which is exactly what the test must not have. So the form is told what
 * it should end up with and shows a verdict on screen, where the test reads it.
 */
public class LoginActivity extends Activity {

    public static final String EXTRA_EXPECTED_USERNAME = "expected_username";
    public static final String EXTRA_EXPECTED_PASSWORD = "expected_password";
    public static final String RESULT_MATCH = "filled: match";
    public static final String RESULT_MISMATCH = "filled: mismatch";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_form);

        final EditText username = findViewById(R.id.username);
        final EditText password = findViewById(R.id.password);
        final TextView result = findViewById(R.id.result);
        final String expectedUsername = stringExtra(EXTRA_EXPECTED_USERNAME);
        final String expectedPassword = stringExtra(EXTRA_EXPECTED_PASSWORD);

        findViewById(R.id.submit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AutofillManager autofill = getSystemService(AutofillManager.class);
                if (autofill != null) {
                    autofill.commit();
                }
            }
        });

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String filledUsername = username.getText().toString();
                String filledPassword = password.getText().toString();
                if (filledUsername.isEmpty() || filledPassword.isEmpty()) {
                    return;
                }
                boolean matches = filledUsername.equals(expectedUsername)
                        && filledPassword.equals(expectedPassword);
                result.setText(matches ? RESULT_MATCH : RESULT_MISMATCH);
            }
        };
        username.addTextChangedListener(watcher);
        password.addTextChangedListener(watcher);

        // Ask for autofill ourselves: on the API 36 emulator focusing a field is not enough to
        // start a session.
        username.post(new Runnable() {
            @Override
            public void run() {
                username.requestFocus();
                AutofillManager autofill = getSystemService(AutofillManager.class);
                if (autofill != null) {
                    autofill.requestAutofill(username);
                }
            }
        });
    }

    private String stringExtra(String name) {
        String value = getIntent().getStringExtra(name);
        return value == null ? "" : value;
    }
}
