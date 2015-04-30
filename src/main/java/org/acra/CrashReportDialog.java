/**
 *  Copyright 2010 Emmanuel Astier & Kevin Gaudin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *	@edited by Sumeet Gehi
 */
package org.acra;

import static org.acra.ACRA.LOG_TAG;
import static org.acra.ReportField.USER_COMMENT;

import java.io.IOException;
import java.util.Arrays;

import org.acra.collector.CrashReportData;
import org.acra.sender.ReportSenderException;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialog;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This is the dialog Activity used by ACRA to get authorization from the user
 * to send reports. Requires android:launchMode="singleInstance" in your
 * AndroidManifest to work properly.
 * 
 * and add mailTo parameter in Application class for Email with dialog prompt
 * mode
 * 
 * @author Sumeet Gehi
 **/
public class CrashReportDialog extends BaseCrashReportDialog implements
		DialogInterface.OnClickListener, DialogInterface.OnDismissListener {

	private static final String STATE_EMAIL = "email";
	private static final String STATE_COMMENT = "comment";
	private EditText userCommentView;
	private EditText userEmailView;

	AppCompatDialog mDialog;
	ReportingInteractionMode mode;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mode = ACRA.getConfig().mode();
		final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
		final int titleResourceId = ACRA.getConfig().resDialogTitle();
		if (titleResourceId != 0) {
			dialogBuilder.setTitle(titleResourceId);
		}
		final int iconResourceId = ACRA.getConfig().resDialogIcon();
		if (iconResourceId != 0) {
			dialogBuilder.setIcon(iconResourceId);
		}
		dialogBuilder.setView(buildCustomView(savedInstanceState));
		dialogBuilder.setPositiveButton(getText(ACRA.getConfig()
				.resDialogPositiveButtonText()), CrashReportDialog.this);
		dialogBuilder.setNegativeButton(getText(ACRA.getConfig()
				.resDialogNegativeButtonText()), CrashReportDialog.this);

		mDialog = dialogBuilder.create();
		mDialog.setCanceledOnTouchOutside(false);
		mDialog.setOnDismissListener(this);
		mDialog.show();
	}

	protected View buildCustomView(Bundle savedInstanceState) {
		final LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(10, 10, 10, 10);
		root.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.WRAP_CONTENT));
		root.setFocusable(true);
		root.setFocusableInTouchMode(true);

		final ScrollView scroll = new ScrollView(this);
		root.addView(scroll, new LinearLayout.LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1.0f));
		final LinearLayout scrollable = new LinearLayout(this);
		scrollable.setOrientation(LinearLayout.VERTICAL);
		scroll.addView(scrollable);

		final TextView text = new TextView(this);
		final int dialogTextId = ACRA.getConfig().resDialogText();
		if (dialogTextId != 0) {
			text.setText(getText(dialogTextId));
		}
		scrollable.addView(text);

		// Add an optional prompt for user comments
		final int commentPromptId = ACRA.getConfig().resDialogCommentPrompt();
		if (commentPromptId != 0) {
			final TextView label = new TextView(this);
			label.setText(getText(commentPromptId));

			label.setPadding(label.getPaddingLeft(), 10,
					label.getPaddingRight(), label.getPaddingBottom());
			scrollable.addView(label, new LinearLayout.LayoutParams(
					LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

			userCommentView = new EditText(this);
			userCommentView.setLines(2);
			if (savedInstanceState != null) {
				String savedValue = savedInstanceState.getString(STATE_COMMENT);
				if (savedValue != null) {
					userCommentView.setText(savedValue);
				}
			}
			scrollable.addView(userCommentView);
		}

		// Add an optional user email field
		final int emailPromptId = ACRA.getConfig().resDialogEmailPrompt();
		if (emailPromptId != 0) {
			final TextView label = new TextView(this);
			label.setText(getText(emailPromptId));

			label.setPadding(label.getPaddingLeft(), 10,
					label.getPaddingRight(), label.getPaddingBottom());
			scrollable.addView(label);

			userEmailView = new EditText(this);
			userEmailView.setSingleLine();
			userEmailView.setInputType(InputType.TYPE_CLASS_TEXT
					| InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

			String savedValue = null;
			if (savedInstanceState != null) {
				savedValue = savedInstanceState.getString(STATE_EMAIL);
			}
			if (savedValue != null) {
				userEmailView.setText(savedValue);
			} else {
				final SharedPreferences prefs = ACRA.getACRASharedPreferences();
				userEmailView.setText(prefs.getString(
						ACRA.PREF_USER_EMAIL_ADDRESS, ""));
			}
			scrollable.addView(userEmailView);
		}

		return root;
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (which == DialogInterface.BUTTON_POSITIVE) {
			if (mode == ReportingInteractionMode.DIALOG_EMAIL) {

				final String comment = userCommentView != null ? userCommentView
						.getText().toString() : "";
				final String email = userEmailView != null ? userEmailView
						.getText().toString() : "";
				try {
					sendEmail(
							CrashReportDialog.this,
							getCrashReport(CrashReportDialog.this, comment,
									email));
				} catch (ReportSenderException e) {
					Log.e("crashDialog", "sending failed");
					e.printStackTrace();
				}
				if (ACRA.getConfig().resDialogOkToast() != 0)
					Toast.makeText(getBaseContext(),
							ACRA.getConfig().resDialogOkToast(), 0).show();

				mDialog.dismiss();
			} else {
				// Retrieve user comment
				final String comment = userCommentView != null ? userCommentView
						.getText().toString() : "";

				// Store the user email
				final String userEmail;
				final SharedPreferences prefs = ACRA.getACRASharedPreferences();
				if (userEmailView != null) {
					userEmail = userEmailView.getText().toString();
					final SharedPreferences.Editor prefEditor = prefs.edit();
					prefEditor.putString(ACRA.PREF_USER_EMAIL_ADDRESS,
							userEmail);
					prefEditor.commit();
				} else {
					userEmail = prefs.getString(ACRA.PREF_USER_EMAIL_ADDRESS,
							"");
				}
				sendCrash(comment, userEmail);
			}
		} else {
			cancelReports();
		}

		finish();
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		finish();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
	 */
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (userCommentView != null && userCommentView.getText() != null) {
			outState.putString(STATE_COMMENT, userCommentView.getText()
					.toString());
		}
		if (userEmailView != null && userEmailView.getText() != null) {
			outState.putString(STATE_EMAIL, userEmailView.getText().toString());
		}
	}

	/**
	 * show dialog when error occurred and send email
	 * 
	 * @author Sumit
	 */
	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	protected void sendEmail(Context context, final CrashReportData errorContent)
			throws ReportSenderException {
		final String subject = "Crash Report for (" + context.getPackageName()
				+ ")";
		String body = getLogCat(errorContent);
		if (body == null || body.isEmpty())
			body = "crash report empty";
		final Intent emailIntent = new Intent(
				android.content.Intent.ACTION_SENDTO);
		emailIntent.setData(Uri.fromParts("mailto", ACRA.getConfig().mailTo(),
				null));
		emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
		emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, body);
		context.startActivity(emailIntent);

	}

	/**
	 * show dialog when error occurred and send email
	 * 
	 * @author Sumit
	 */
	private String getLogCat(CrashReportData errorContent) {
		ReportField[] fields = ACRA.getConfig().customReportContent();
		if (fields.length == 0) {
			fields = ACRAConstants.DEFAULT_MAIL_REPORT_FIELDS;
		}

		final StringBuilder builder = new StringBuilder();
		for (ReportField field : fields) {
			builder.append(field.toString()).append("=");
			builder.append(errorContent.get(field));
			builder.append('\n');
		}
		return builder.toString();
	}

	/**
	 * get crash report
	 *
	 * @author Sumit
	 */
	private CrashReportData getCrashReport(Context context, String userComment,
			String userEmail) {
		Log.d(LOG_TAG, "#checkAndSendReports - start");
		final CrashReportFinder reportFinder = new CrashReportFinder(context);
		final String[] reportFiles = reportFinder.getCrashReportFiles();
		CrashReportData crashReport = null;
		Arrays.sort(reportFiles);

		int reportsSentCount = 0;

		for (String curFileName : reportFiles) {

			if (reportsSentCount >= ACRAConstants.MAX_SEND_REPORTS) {
				break;
				// send only a few reports to avoid overloading the network
			}

			Log.i(LOG_TAG, "Sending file " + curFileName);
			try {
				final CrashReportPersister persister = new CrashReportPersister(
						context);
				crashReport = persister.load(curFileName);
				crashReport.put(USER_COMMENT, userComment == null ? ""
						: userComment);
				crashReport.put(ReportField.USER_EMAIL, userEmail == null ? ""
						: userEmail);
				// crashReport.put(USER_EMAIL, userEmail == null ? "" :
				// userEmail);
				// persister.store(crashReport, curFileName);
				// sendCrashReport(previousCrashReport);
				deleteFile(context, curFileName);
			} catch (RuntimeException e) {
				Log.e(ACRA.LOG_TAG, "Failed to send crash reports for "
						+ curFileName, e);
				deleteFile(context, curFileName);
				break; // Something really unexpected happened
			} catch (IOException e) {
				Log.e(ACRA.LOG_TAG, "Failed to load crash report for "
						+ curFileName, e);
				deleteFile(context, curFileName);
				break;
			}
			reportsSentCount++;
		}
		Log.d("Acra Report", "#checkAndSendReports - finish");
		return crashReport;
	}

	/**
	 * delete log files that are in data/data/pkg/data/files to save
	 * memory/cache memory
	 *
	 * @author Sumit
	 */
	private boolean deleteFile(Context context, String fileName) {
		return context.deleteFile(fileName);
	}

}