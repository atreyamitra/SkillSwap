package com.skillswap.app.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.skillswap.app.databinding.ActivitySessionScheduleBinding;
import com.skillswap.app.firebase.AuthManager;
import com.skillswap.app.firebase.SessionRepository;
import com.skillswap.app.models.Session;
import com.skillswap.app.utils.SessionReminderWorker;

import java.util.Calendar;
import java.util.Locale;

/**
 * Lets either party of an ACCEPTED swap pick a date and time (via the standard
 * platform DatePickerDialog / TimePickerDialog) and schedules a session, which in
 * turn schedules a local reminder notification via WorkManager.
 */
public class SessionScheduleActivity extends AppCompatActivity {

    public static final String EXTRA_REQUEST_ID = "extra_request_id";
    public static final String EXTRA_OTHER_NAME = "extra_other_name";
    public static final String EXTRA_OTHER_UID = "extra_other_uid";

    private ActivitySessionScheduleBinding binding;
    private final SessionRepository sessionRepository = new SessionRepository();
    private final Calendar calendar = Calendar.getInstance();
    private boolean dateSet = false;
    private boolean timeSet = false;

    private String requestId;
    private String otherName;
    private String otherUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySessionScheduleBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        requestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);
        otherName = getIntent().getStringExtra(EXTRA_OTHER_NAME);
        otherUid = getIntent().getStringExtra(EXTRA_OTHER_UID);

        binding.toolbarInclude.tvToolbarTitle.setText("Schedule Session");
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());
        binding.tvWith.setText("Schedule a session with " + (otherName == null ? "your swap partner" : otherName));

        binding.btnPickDate.setOnClickListener(v -> showDatePicker());
        binding.btnPickTime.setOnClickListener(v -> showTimePicker());
        binding.btnConfirm.setOnClickListener(v -> confirmSession());
    }

    private void showDatePicker() {
        Calendar now = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            dateSet = true;
            updateSelectedText();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(now.getTimeInMillis() - 1000);
        dialog.show();
    }

    private void showTimePicker() {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            timeSet = true;
            updateSelectedText();
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show();
    }

    private void updateSelectedText() {
        if (dateSet && timeSet) {
            binding.tvSelectedDateTime.setText(String.format(Locale.getDefault(),
                    "%te %tB %tY at %tI:%tM %tp", calendar, calendar, calendar, calendar, calendar, calendar));
        } else if (dateSet) {
            binding.tvSelectedDateTime.setText(String.format(Locale.getDefault(),
                    "%te %tB %tY — now pick a time", calendar, calendar, calendar));
        } else if (timeSet) {
            binding.tvSelectedDateTime.setText("Time selected — now pick a date");
        }
    }

    private void confirmSession() {
        if (!dateSet || !timeSet) {
            Snackbar.make(binding.getRoot(), "Please pick both a date and a time", Snackbar.LENGTH_SHORT).show();
            return;
        }
        String currentUid = AuthManager.getInstance().getCurrentUid();
        if (currentUid == null || requestId == null) return;

        String sessionId = sessionRepository.newSessionId();
        String dateText = String.format(Locale.getDefault(), "%te %tB %tY", calendar, calendar, calendar);
        String timeText = String.format(Locale.getDefault(), "%tI:%tM %tp", calendar, calendar, calendar);

        Session session = new Session(sessionId, requestId, currentUid, otherUid, dateText, timeText,
                calendar.getTimeInMillis());

        setLoading(true);
        sessionRepository.scheduleSession(session, new SessionRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                setLoading(false);
                long leadTimeMinutes = 60; // remind 1 hour before
                SessionReminderWorker.scheduleReminder(SessionScheduleActivity.this,
                        calendar.getTimeInMillis(), leadTimeMinutes,
                        otherName == null ? "your swap partner" : otherName, dateText, timeText);
                Snackbar.make(binding.getRoot(), "Session scheduled!", Snackbar.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Snackbar.make(binding.getRoot(), "Failed: " + message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnConfirm.setEnabled(!loading);
    }
}
