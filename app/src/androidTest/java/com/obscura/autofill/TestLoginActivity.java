package com.obscura.autofill;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.autofill.AutofillManager;
import android.widget.Button;
import android.widget.EditText;

import com.obscura.test.R;

/**
 * A login form that belongs to the test package, not to Obscura: from the service's point of view
 * it is just another app, which is what makes the "not linked" case meaningful.
 *
 * Written in Java on purpose: it runs in the test package's own process, where the Kotlin runtime
 * of the app under test is not loaded.
 *
 * The test cannot read these fields from its own process, and a password field hides its text from
 * the accessibility tree anyway. So the form is told what it should end up with and reports a
 * plain yes/no back, leaving the values here.
 */
public class TestLoginActivity extends Activity {

    public static final String EXTRA_EXPECTED_USERNAME = "expected_username";
    public static final String EXTRA_EXPECTED_PASSWORD = "expected_password";
    public static final String ACTION_FILLED = "com.obscura.test.FILLED";
    public static final String EXTRA_MATCHES = "matches";
    public static final String TARGET_PACKAGE = "com.obscura";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_login_form);

        final EditText username = findViewById(R.id.username);
        final EditText password = findViewById(R.id.password);
        final String expectedUsername = stringExtra(EXTRA_EXPECTED_USERNAME);
        final String expectedPassword = stringExtra(EXTRA_EXPECTED_PASSWORD);

        Button submit = findViewById(R.id.submit);
        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // What a real app does when the user signs in: the autofill context is committed,
                // and the framework decides here whether to offer saving what was typed.
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
                sendBroadcast(new Intent(ACTION_FILLED)
                        .setPackage(TARGET_PACKAGE)
                        .putExtra(EXTRA_MATCHES, matches));
            }
        };
        username.addTextChangedListener(watcher);
        password.addTextChangedListener(watcher);

        // Ask for autofill ourselves: on this emulator focusing a field is not enough to start a
        // session, and an app asking for its own form is an ordinary use of the API.
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
