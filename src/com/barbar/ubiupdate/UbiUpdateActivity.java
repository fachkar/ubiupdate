package com.barbar.ubiupdate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.util.Calendar;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class UbiUpdateActivity extends Activity {
    public volatile ConnectivityManager mConctMgr = null;
    public volatile Handler mHandler = null;
    public volatile Button mCheckNowButton = null;
    public volatile Context mContext = null;
    public volatile String mPrefFile = "updatePrefs";
    public volatile SharedPreferences mUpdtPrefrns = null;
    public volatile TextView mUpdateStatusTextView = null;
    public volatile TextView mUpdateStatusTitleTextView = null;
    public volatile Long mServerDate, mServerTime;
    public volatile CountDownTimer mCountDownTimer = null;
    public volatile ProgressBar mProgressBar = null;
    public volatile boolean mIndeterminate = false;
    public volatile int mPrimaryProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ubi_update);
        mContext = this;
        mHandler = new Handler();
        mUpdateStatusTextView = (TextView) findViewById(R.id.update_status_text);
        mUpdateStatusTitleTextView = (TextView) findViewById(R.id.update_status_title);
        mProgressBar = (ProgressBar) findViewById(R.id.update_status_progress_bar);
        mUpdtPrefrns = getSharedPreferences(mPrefFile, Context.MODE_PRIVATE);
        Thread workingthread = new Thread(new UbiUpdateActivityCreateThread());
        workingthread.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Thread workingthread = new Thread(new UbiUpdateActivityResumeThread());
        workingthread.start();
    }

    @Override
    protected void onDestroy() {
        mIndeterminate = false;
        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
            mCountDownTimer = null;
        }
    }

    class UbiUpdateActivityCreateThread implements Runnable {

        @Override
        public void run() {
            try {
                mCheckNowButton = (Button) findViewById(R.id.check_now_button);
                if (mCheckNowButton != null) {
                    mCheckNowButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Thread workingthread = new Thread(new CheckServerIncrementalThread());
                            workingthread.start();

                            // startService(new Intent(mContext,
                            // UpdateCheckerService.class));
                        }
                    });
                }

            } catch (Exception e) {
                Log.e(this.getClass().getName(), "Exception", e);
            }
        }
    }

    class UbiUpdateActivityResumeThread implements Runnable {

        @Override
        public void run() {
            try {
                if (mUpdtPrefrns != null) {
                    mServerDate = Long.parseLong(mUpdtPrefrns.getString("srvrD8", "20130101"));
                    mServerTime = Long.parseLong(mUpdtPrefrns.getString("srvrTime", "000000"));

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mUpdateStatusTitleTextView.setText(mUpdtPrefrns.getString("titleKey", "Your System is currently up to date"));
                            mUpdateStatusTextView.setText(mUpdtPrefrns.getString("msgKey", "Last checked for update unknown."));
                        }

                    });

                } else {
                    Log.e(this.getClass().getName(), "UbiUpdateActivityResumeThread, mUpdtPrefrns = null ..");
                }

            } catch (Exception e) {
                Log.e(this.getClass().getName(), "Exception", e);
            }
        }
    }

    class CheckServerIncrementalThread implements Runnable {

        @Override
        public void run() {
            Socket socket = null;
            BufferedWriter writer = null;
            BufferedReader reader = null;
            String output = null;

            if (mCountDownTimer != null) {
                mCountDownTimer.cancel();
                mCountDownTimer = null;
            }

            try {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCheckNowButton.setEnabled(false);
                        mUpdateStatusTextView.setText("Checking please wait .. ");
                    }

                });

                mIndeterminate = true;
                Thread updateProgressthread = new Thread(new UpdateindeterminateThread());
                updateProgressthread.start();

                String tmpIncremental = "eng.firas.20130418.140319"; // Build.VERSION.INCREMENTAL;
                if (tmpIncremental.length() > 16) {
                    String tmpDtIncremental = tmpIncremental.substring(tmpIncremental.length() - 15);
                    long tmpDateIncr = Long.parseLong(tmpDtIncremental.substring(0, 8));
                    long tmptimeIncr = Long.parseLong(tmpDtIncremental.substring(9, 15));

                    mConctMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                    if (mConctMgr != null) {
                        NetworkInfo netInfo = mConctMgr.getActiveNetworkInfo();

                        if (netInfo != null) {
                            int netType = netInfo.getType();
                            if (netInfo.isConnected() && (netType == ConnectivityManager.TYPE_ETHERNET || netType == ConnectivityManager.TYPE_WIFI || netType == ConnectivityManager.TYPE_WIMAX)) {
                                socket = new Socket("10.0.0.165", Integer.parseInt("9998"));
                                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                                // send input terminated with \n
                                String input = "getincremental";
                                writer.write(input + "\n", 0, input.length() + 1);
                                writer.flush();

                                // read back output
                                output = reader.readLine();

                                if (!output.isEmpty()) {
                                    Log.d(null, " -- -- got incremental:" + output + ", of length:" + output.length());
                                    String tmpServerDtIncremental = output.substring(output.length() - 15);
                                    long tmpServerDateIncr = Long.parseLong(tmpServerDtIncremental.substring(0, 8));
                                    long tmpServertimeIncr = Long.parseLong(tmpServerDtIncremental.substring(9, 15));

                                    if (tmpServerDateIncr >= tmpDateIncr) {
                                        if (tmpServertimeIncr > tmptimeIncr) {
                                            Log.d(null, " -- -- there is newer version:" + tmpServerDateIncr + "." + tmpServertimeIncr);

                                            mIndeterminate = false;

                                        } else {
                                            Log.d(null, " -- -- there is no newer version of:" + tmpServerDateIncr + "." + tmpServertimeIncr);
                                            Calendar mCalendar = Calendar.getInstance();
                                            mCalendar.setTimeInMillis(System.currentTimeMillis());
                                            Log.d(null, "data now is :" + mCalendar.getTime());

                                            Thread workingthread = new Thread(new StoreStatusTitleMsgThread("Your System is currently up to date", "Last checked for update " + mCalendar.getTime()));
                                            workingthread.start();
                                        }
                                    } else {
                                        Log.d(null, " -- -- there is no newer version of:" + tmpServerDateIncr + "." + tmpServertimeIncr);
                                        Calendar mCalendar = Calendar.getInstance();
                                        mCalendar.setTimeInMillis(System.currentTimeMillis());
                                        Log.d(null, "data now is :" + mCalendar.getTime());

                                        Thread workingthread = new Thread(new StoreStatusTitleMsgThread("Your System is currently up to date", "Last checked for update " + mCalendar.getTime()));
                                        workingthread.start();
                                    }
                                } else {
                                    mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Server Connection Error",
                                                    " Server is either overloaded or unreachable at the moment .. 'Check now' button will be re-enabled in 10 seconds"));
                                }

                                try {
                                    writer.close();
                                } catch (IOException e) {
                                    Log.e(this.getClass().getName(), "Exception", e);
                                }
                                try {
                                    reader.close();
                                } catch (IOException e) {
                                    Log.e(this.getClass().getName(), "Exception", e);
                                }
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(this.getClass().getName(), "Exception", e);
                                }

                            }
                        } else {
                            mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Internet Connection Error",
                                            "This action can only be performed via Wifi, WiMax or Ethernet connection. Please Enable either of ther previously mentioned connection types and try again .. 'Check now' button will be re-enabled in 10 seconds"));
                        }
                    } else {
                        mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Internet Connection Error", "Connectivity Service error  .. 'Check now' button will be re-enabled in 10 seconds"));
                    }
                } else {
                    mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("System Error", "Failed to get current system version .. 'Check now' button will be re-enabled in 10 seconds"));
                }

            } catch (ConnectException e) {
                Log.e(this.getClass().getName(), "NumberFormatException", e);
                mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Connection Exception", "Communication exception occurred. " + e.getLocalizedMessage() + " .. 'Check now' button will be re-enabled in 10 seconds"));
            } catch (NumberFormatException e) {
                Log.e(this.getClass().getName(), "NumberFormatException", e);
                mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Connection Exception", "Communication exception occurred. " + e.getLocalizedMessage() + " .. 'Check now' button will be re-enabled in 10 seconds"));
            } catch (Exception e) {
                mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Connection Exception", "General exception occurred. " + e.getLocalizedMessage() + " .. 'Check now' button will be re-enabled in 10 seconds"));
            }
        }
    }

    class StoreStatusTitleMsgThread implements Runnable {
        String mUpdtStatsTitlStr, mUpdtStatsStr;

        public StoreStatusTitleMsgThread(String sUpdtStatsTitlStr, String sUpdtStatsStr) {
            mUpdtStatsTitlStr = sUpdtStatsTitlStr;
            mUpdtStatsStr = sUpdtStatsStr;
        }

        @Override
        public void run() {
            try {
                Log.d(null, "StoreStatusTitleMsgThread, mUpdtStatsTitlStr:" + mUpdtStatsTitlStr + ", mUpdtStatsStr:" + mUpdtStatsStr);

                mIndeterminate = false;
                Editor updatePrefsEditor = mUpdtPrefrns.edit();
                updatePrefsEditor.putString("titleKey", mUpdtStatsTitlStr);
                updatePrefsEditor.putString("msgKey", mUpdtStatsStr);
                updatePrefsEditor.commit();

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mUpdateStatusTitleTextView.setText(mUpdtStatsTitlStr);
                        mUpdateStatusTextView.setText(mUpdtStatsStr);
                        mCheckNowButton.setEnabled(true);
                    }

                });
            } catch (Exception e) {
                Log.e(this.getClass().getName(), "Exception", e);
            }
        }
    }

    class UpdateOnlyErrorStatusTitleMsgThread implements Runnable {
        String mUpdtStatsTitlStr, mUpdtStatsStr;

        public UpdateOnlyErrorStatusTitleMsgThread(String sUpdtStatsTitlStr, String sUpdtStatsStr) {
            mUpdtStatsTitlStr = sUpdtStatsTitlStr;
            mUpdtStatsStr = sUpdtStatsStr;
        }

        @Override
        public void run() {
            try {
                Log.d(null, "UpdateOnlyErrorStatusTitleMsgThread, mUpdtStatsTitlStr:" + mUpdtStatsTitlStr + ", mUpdtStatsStr:" + mUpdtStatsStr);

                mIndeterminate = false;
                mUpdateStatusTitleTextView.setText(mUpdtStatsTitlStr);
                mUpdateStatusTextView.setText(mUpdtStatsStr);

                if (mCountDownTimer != null) {
                    mCountDownTimer.cancel();
                    mCountDownTimer = null;
                }

                mCountDownTimer = new CountDownTimer(10000, 30000) {

                    public void onTick(long millisUntilFinished) {
                        return;
                    }

                    public void onFinish() {
                        mUpdateStatusTitleTextView.setText(mUpdtPrefrns.getString("titleKey", "Your System is currently up to date"));
                        mUpdateStatusTextView.setText(mUpdtPrefrns.getString("msgKey", "Last checked for update unknown."));
                        mCheckNowButton.setEnabled(true);

                    }
                }.start();

            } catch (Exception e) {
                Log.e(this.getClass().getName(), "Exception", e);
            }
        }
    }

    class UpdateindeterminateThread implements Runnable {
        @Override
        public void run() {
            try {
                mPrimaryProgress = 20;
                while (mIndeterminate) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mProgressBar.setProgress(mPrimaryProgress - 20);
                            mProgressBar.setSecondaryProgress(mPrimaryProgress);
                        }

                    });

                    Thread.sleep(200);

                    mPrimaryProgress += 5;
                    if (mPrimaryProgress > 100) {
                        mPrimaryProgress = 20;
                    }

                }

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mProgressBar.setProgress(0);
                        mProgressBar.setSecondaryProgress(0);
                    }

                });

            } catch (Exception e) {
                Log.e(this.getClass().getName(), "Exception", e);
            }
        }
    }

}
