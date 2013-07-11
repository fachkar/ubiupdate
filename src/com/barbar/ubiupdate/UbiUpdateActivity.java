package com.barbar.ubiupdate;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.Calendar;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

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
    public volatile Button mDownloadNowButton = null;
    public volatile Button mInstallNowButton = null;
    public volatile Context mContext = null;
    public volatile SharedPreferences mUpdtPrefrns = null;
    public volatile TextView mUpdateStatusTextView = null;
    public volatile TextView mUpdateStatusTitleTextView = null;
    public volatile String mLastServerD8TimeCheck = null;
    public volatile String mLastServerMD5 = null;
    public volatile long mServerDate, mServerTime;
    public volatile CountDownTimer mCountDownTimer = null;
    public volatile ProgressBar mProgressBar = null;
    public volatile boolean mIndeterminate = false;
    public volatile int mPrimaryProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(null, " -- -- onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ubi_update);
        mContext = this;
        mHandler = new Handler();
        mUpdateStatusTextView = (TextView) findViewById(R.id.update_status_text);
        mUpdateStatusTitleTextView = (TextView) findViewById(R.id.update_status_title);
        mProgressBar = (ProgressBar) findViewById(R.id.update_status_progress_bar);
        mUpdtPrefrns = getSharedPreferences("updatePrefs", Context.MODE_PRIVATE);
        Thread workingthread = new Thread(new UbiUpdateActivityCreateThread());
        workingthread.start();
    }

    @Override
    protected void onResume() {
        Log.d(null, " -- -- onResume");
        super.onResume();
        Thread workingthread = new Thread(new UbiUpdateActivityResumeThread());
        workingthread.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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

                mDownloadNowButton = (Button) findViewById(R.id.download_now_button);
                if (mDownloadNowButton != null) {
                    mDownloadNowButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Thread workingthread = new Thread(new DownloadServerIncrementalThread());
                            workingthread.start();

                        }
                    });
                }

                mInstallNowButton = (Button) findViewById(R.id.install_now_button);
                if (mInstallNowButton != null) {
                    mInstallNowButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Thread workingthread = new Thread(new InstallUpdateThread());
                            workingthread.start();

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
                    mServerDate = mUpdtPrefrns.getLong("srvrD8", 20130101);
                    mServerTime = mUpdtPrefrns.getLong("srvrTime", 000000);
                    mLastServerD8TimeCheck = mUpdtPrefrns.getString("lastCheckKey", "unknown");
                    mLastServerMD5 = mUpdtPrefrns.getString("lastMD5", "unknown");

                    File dwnldfile = new File(getResources().getString(R.string.download_path));

                    if (dwnldfile.exists() && !mLastServerMD5.isEmpty() && mLastServerMD5 != "unkown") {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mUpdateStatusTitleTextView.setText("Update Available");
                                mUpdateStatusTextView.setText("New downloaded update available .. checking MD5 checksum ..");
                                mCheckNowButton.setVisibility(View.GONE);
                                mDownloadNowButton.setVisibility(View.GONE);
                                mInstallNowButton.setVisibility(View.VISIBLE);
                                mInstallNowButton.setEnabled(false);
                            }
                        });

                        if (MD5.checkMd5(mLastServerMD5, getResources().getString(R.string.download_path))) {
                            Log.d(this.getClass().getName(), "md5 check ok");

                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mUpdateStatusTextView.setText("MD5 checksum complete .. ready to install");
                                    mInstallNowButton.setVisibility(View.VISIBLE);
                                    mInstallNowButton.setEnabled(true);
                                }
                            });

                        } else {
                            Log.e(this.getClass().getName(), "failed md5");
                            mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Update File Error",
                                            " Downloaded update file failed md5 checksum, most likely error occurred while downloading .. \n\n'Download now' button will be re-enabled in 10 seconds"));
                        }
                    } else {
                        String tmpIncremental = "eng.firas.20130418.140319"; // Build.VERSION.INCREMENTAL;
                        if (tmpIncremental.length() > 16) {
                            String tmpDtIncremental = tmpIncremental.substring(tmpIncremental.length() - 15);
                            long tmpDateIncr = Long.parseLong(tmpDtIncremental.substring(0, 8));
                            long tmptimeIncr = Long.parseLong(tmpDtIncremental.substring(9, 15));

                            if (mServerDate >= tmpDateIncr) {
                                if (mServerTime > tmptimeIncr) {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            mUpdateStatusTitleTextView.setText("Update Available");
                                            mUpdateStatusTextView.setText("New update " + String.valueOf(mServerDate) + "." + String.valueOf(mServerTime) + " .. Last checked for update " + mLastServerD8TimeCheck);
                                            mCheckNowButton.setVisibility(View.GONE);
                                            mInstallNowButton.setVisibility(View.GONE);
                                            mDownloadNowButton.setVisibility(View.VISIBLE);
                                            mDownloadNowButton.setEnabled(true);
                                        }
                                    });
                                } else {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            mUpdateStatusTitleTextView.setText("Your system is currently up to date");
                                            mUpdateStatusTextView.setText("Last checked for update " + mLastServerD8TimeCheck);
                                        }
                                    });
                                }
                            } else {
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        mUpdateStatusTitleTextView.setText("Your system is currently up to date");
                                        mUpdateStatusTextView.setText("Last checked for update " + mLastServerD8TimeCheck);
                                    }
                                });
                            }

                        } else {

                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mUpdateStatusTitleTextView.setText("System Error");
                                    mUpdateStatusTextView.setText("Failed to get current system version ..");
                                    mDownloadNowButton.setVisibility(View.GONE);
                                    mInstallNowButton.setVisibility(View.GONE);
                                    mCheckNowButton.setVisibility(View.VISIBLE);
                                    mCheckNowButton.setEnabled(false);
                                }
                            });

                        }
                    }

                } else {
                    Log.e(this.getClass().getName(), "UbiUpdateActivityResumeThread, mUpdtPrefrns = null ..");
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mUpdateStatusTitleTextView.setText("System Error");
                            mUpdateStatusTextView.setText("Failed to get current system version ..");
                            mDownloadNowButton.setVisibility(View.GONE);
                            mInstallNowButton.setVisibility(View.GONE);
                            mCheckNowButton.setVisibility(View.VISIBLE);
                            mCheckNowButton.setEnabled(false);
                        }
                    });
                }

            } catch (Exception e) {
                Log.e(this.getClass().getName(), "Exception", e);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mUpdateStatusTitleTextView.setText("System Error");
                        mUpdateStatusTextView.setText("Failed to get current system version ..");
                        mDownloadNowButton.setVisibility(View.GONE);
                        mInstallNowButton.setVisibility(View.GONE);
                        mCheckNowButton.setVisibility(View.VISIBLE);
                        mCheckNowButton.setEnabled(false);
                    }
                });
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
                        mInstallNowButton.setVisibility(View.GONE);
                        mDownloadNowButton.setVisibility(View.GONE);
                        mCheckNowButton.setVisibility(View.VISIBLE);
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
                                socket = new Socket("10.0.0.66", Integer.parseInt("9998"));
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

                                            Calendar mCalendar = Calendar.getInstance();
                                            mCalendar.setTimeInMillis(System.currentTimeMillis());
                                            Log.d(null, "data now is :" + mCalendar.getTime());

                                            Editor updatePrefsEditor = mUpdtPrefrns.edit();
                                            updatePrefsEditor.putLong("srvrD8", tmpServerDateIncr);
                                            updatePrefsEditor.putLong("srvrTime", tmpServertimeIncr);
                                            mLastServerD8TimeCheck = mCalendar.getTime().toString();
                                            updatePrefsEditor.putString("lastCheckKey", mLastServerD8TimeCheck);
                                            updatePrefsEditor.commit();

                                            if (mCountDownTimer != null) {
                                                mCountDownTimer.cancel();
                                                mCountDownTimer = null;
                                            }

                                            mHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mUpdateStatusTitleTextView.setText("Update Available");
                                                    mUpdateStatusTextView
                                                                    .setText("New update " + String.valueOf(mServerDate) + "." + String.valueOf(mServerTime) + " .. Last checked for update " + mLastServerD8TimeCheck);
                                                    mCheckNowButton.setVisibility(View.GONE);
                                                    mInstallNowButton.setVisibility(View.GONE);
                                                    mDownloadNowButton.setVisibility(View.VISIBLE);
                                                }

                                            });

                                            mIndeterminate = false;

                                            return;

                                        } else {
                                            Log.d(null, " -- -- there is no newer version of:" + tmpServerDateIncr + "." + tmpServertimeIncr);
                                            Calendar mCalendar = Calendar.getInstance();
                                            mCalendar.setTimeInMillis(System.currentTimeMillis());
                                            Log.d(null, "data now is :" + mCalendar.getTime());

                                            Editor updatePrefsEditor = mUpdtPrefrns.edit();
                                            updatePrefsEditor.putLong("srvrD8", tmpServerDateIncr);
                                            updatePrefsEditor.putLong("srvrTime", tmpServertimeIncr);
                                            mLastServerD8TimeCheck = mCalendar.getTime().toString();
                                            updatePrefsEditor.putString("lastCheckKey", mLastServerD8TimeCheck);
                                            updatePrefsEditor.commit();

                                            if (mCountDownTimer != null) {
                                                mCountDownTimer.cancel();
                                                mCountDownTimer = null;
                                            }

                                            mHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mUpdateStatusTitleTextView.setText("Your system is currently up to date");
                                                    mUpdateStatusTextView.setText("Last checked for update " + mLastServerD8TimeCheck);
                                                    mInstallNowButton.setVisibility(View.GONE);
                                                    mDownloadNowButton.setVisibility(View.GONE);
                                                    mCheckNowButton.setVisibility(View.VISIBLE);
                                                    mCheckNowButton.setEnabled(true);
                                                }

                                            });

                                            mIndeterminate = false;

                                            return;
                                        }
                                    } else {
                                        Log.d(null, " -- -- there is no newer version of:" + tmpServerDateIncr + "." + tmpServertimeIncr);
                                        Calendar mCalendar = Calendar.getInstance();
                                        mCalendar.setTimeInMillis(System.currentTimeMillis());
                                        Log.d(null, "data now is :" + mCalendar.getTime());

                                        Editor updatePrefsEditor = mUpdtPrefrns.edit();
                                        updatePrefsEditor.putLong("srvrD8", tmpServerDateIncr);
                                        updatePrefsEditor.putLong("srvrTime", tmpServertimeIncr);
                                        mLastServerD8TimeCheck = mCalendar.getTime().toString();
                                        updatePrefsEditor.putString("lastCheckKey", mLastServerD8TimeCheck);
                                        updatePrefsEditor.commit();

                                        if (mCountDownTimer != null) {
                                            mCountDownTimer.cancel();
                                            mCountDownTimer = null;
                                        }

                                        mHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                mUpdateStatusTitleTextView.setText("Your system is currently up to date");
                                                mUpdateStatusTextView.setText("Last checked for update " + mLastServerD8TimeCheck);

                                                mInstallNowButton.setVisibility(View.GONE);
                                                mDownloadNowButton.setVisibility(View.GONE);
                                                mCheckNowButton.setVisibility(View.VISIBLE);
                                                mCheckNowButton.setEnabled(true);
                                            }

                                        });

                                        mIndeterminate = false;

                                        return;
                                    }
                                } else {
                                    Log.e(this.getClass().getName(), "output.isEmpty()");
                                    mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Server Connection Error",
                                                    " Server is either overloaded or unreachable at the moment .. \n\n'Check now' button will be re-enabled in 10 seconds"));
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

                            } else {
                                Log.e(this.getClass().getName(), "mot wifi or wimax");
                                mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Internet Connection Error",
                                                "This action can only be performed via Wifi, WiMax or Ethernet connection. Please Enable either of ther previously mentioned connection types and try again .. \n\n'Download now' button will be re-enabled in 10 seconds"));
                            }
                        } else {
                            Log.e(this.getClass().getName(), "mnetInfo is null");
                            mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Internet Connection Error",
                                            "This action can only be performed via Wifi, WiMax or Ethernet connection. Please Enable either of ther previously mentioned connection types and try again .. \n\n'Check now' button will be re-enabled in 10 seconds"));
                        }
                    } else {
                        Log.e(this.getClass().getName(), "mConctMgr is null");
                        mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Internet Connection Error", "Connectivity Service error  .. \n\n'Check now' button will be re-enabled in 10 seconds"));
                    }
                } else {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mUpdateStatusTitleTextView.setText("System Error");
                            mUpdateStatusTextView.setText("Failed to get current system version ..");
                            mInstallNowButton.setVisibility(View.GONE);
                            mDownloadNowButton.setVisibility(View.GONE);
                            mCheckNowButton.setVisibility(View.VISIBLE);
                            mCheckNowButton.setEnabled(false);
                        }
                    });
                }

            } catch (ConnectException e) {
                Log.e(this.getClass().getName(), "NumberFormatException", e);
                mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Connection Exception", "Communication exception occurred. " + e.getLocalizedMessage()
                                + " .. \n\n'Check now' button will be re-enabled in 10 seconds"));
            } catch (NumberFormatException e) {
                Log.e(this.getClass().getName(), "NumberFormatException", e);
                mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Connection Exception", "Communication exception occurred. " + e.getLocalizedMessage()
                                + " .. \n\n'Check now' button will be re-enabled in 10 seconds"));
            } catch (Exception e) {
                mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Connection Exception", "General exception occurred. " + e.getLocalizedMessage() + " .. \n\n'Check now' button will be re-enabled in 10 seconds"));
            }
        }
    }

    class DownloadServerIncrementalThread implements Runnable {

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
                        mInstallNowButton.setVisibility(View.GONE);
                        mCheckNowButton.setVisibility(View.GONE);
                        mDownloadNowButton.setVisibility(View.VISIBLE);
                        mDownloadNowButton.setEnabled(false);
                        mUpdateStatusTextView.setText("Checking please wait .. ");
                    }

                });

                mIndeterminate = true;
                Thread updateProgressthread = new Thread(new UpdateindeterminateThread());
                updateProgressthread.start();

                String tmpIncremental = "eng.firas.20130418.140319"; // Build.VERSION.INCREMENTAL;
                if (tmpIncremental.length() > 16) {
                    mConctMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                    if (mConctMgr != null) {
                        NetworkInfo netInfo = mConctMgr.getActiveNetworkInfo();

                        if (netInfo != null) {
                            int netType = netInfo.getType();
                            if (netInfo.isConnected() && (netType == ConnectivityManager.TYPE_ETHERNET || netType == ConnectivityManager.TYPE_WIFI || netType == ConnectivityManager.TYPE_WIMAX)) {
                                socket = new Socket("10.0.0.66", Integer.parseInt("9998"));
                                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                                // send input terminated with \n
                                String input = "getdownloadincremental:" + tmpIncremental;
                                writer.write(input + "\n", 0, input.length() + 1);
                                writer.flush();

                                // read back output
                                output = reader.readLine();

                                if (!output.isEmpty()) {
                                    Log.d(null, " -- -- got getdownload link:" + output + ", of length:" + output.length());
                                    StringTokenizer stok = new StringTokenizer(output, ";");
                                    if (stok != null) {
                                        int toknmbr = stok.countTokens();
                                        if (toknmbr > 2) {
                                            try {
                                                String dwnlodVersion = stok.nextToken();
                                                URL url = new URL(stok.nextToken());
                                                String md5 = stok.nextToken();

                                                String tmpServerDtIncremental = dwnlodVersion.substring(dwnlodVersion.length() - 15);
                                                long tmpServerDateIncr = Long.parseLong(tmpServerDtIncremental.substring(0, 8));
                                                long tmpServertimeIncr = Long.parseLong(tmpServerDtIncremental.substring(9, 15));
                                                Editor updatePrefsEditor = mUpdtPrefrns.edit();
                                                updatePrefsEditor.putLong("srvrD8", tmpServerDateIncr);
                                                updatePrefsEditor.putLong("srvrTime", tmpServertimeIncr);
                                                updatePrefsEditor.commit();

                                                RandomAccessFile mRandomAccessFile = new RandomAccessFile(getResources().getString(R.string.download_path), "rw");
                                                byte[] buf = new byte[1024 * 8];
                                                HttpURLConnection cn = (HttpURLConnection) url.openConnection();
                                                long mFileSize = (long) cn.getContentLength();
                                                if (mFileSize > 0) {
                                                    mRandomAccessFile.setLength(mFileSize);
                                                    mRandomAccessFile.seek(0);
                                                    cn = (HttpURLConnection) url.openConnection();
                                                    cn.setRequestProperty("Range", "bytes=" + 0 + "-" + mFileSize);
                                                    long mCountSize = 0;
                                                    BufferedInputStream bis = new BufferedInputStream(cn.getInputStream());
                                                    int len;
                                                    mIndeterminate = false;
                                                    mHandler.post(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            mUpdateStatusTextView.setText("Starting Download .. ");
                                                        }

                                                    });

                                                    while ((len = bis.read(buf)) > 0) {
                                                        synchronized (mRandomAccessFile) {
                                                            mRandomAccessFile.write(buf, 0, len);
                                                            mCountSize += len;
                                                            mHandler.post(new UpdateDownloadProgressThread((int) (mCountSize * 100 / mFileSize)));
                                                        }
                                                    }

                                                    mHandler.post(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            mUpdateStatusTextView.setText("Download complete .. now checking md5 ");
                                                        }

                                                    });

                                                    if (MD5.checkMd5(md5, getResources().getString(R.string.download_path))) {
                                                        Log.d(this.getClass().getName(), "md5 check ok");
                                                        mLastServerMD5 = md5;
                                                        updatePrefsEditor.putString("lastMD5", mLastServerMD5);
                                                        updatePrefsEditor.commit();

                                                        mHandler.post(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                mUpdateStatusTextView.setText("MD5 checksum complete .. ready to install");
                                                                mCheckNowButton.setVisibility(View.GONE);
                                                                mDownloadNowButton.setVisibility(View.GONE);
                                                                mInstallNowButton.setVisibility(View.VISIBLE);
                                                                mInstallNowButton.setEnabled(true);
                                                            }

                                                        });

                                                    } else {
                                                        Log.e(this.getClass().getName(), "failed md5");
                                                        mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Update File Error",
                                                                        " Downloaded update file failed md5 checksum, most likely error occurred while downloading .. \n\n'Download now' button will be re-enabled in 10 seconds"));
                                                    }

                                                } else {
                                                    mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Server Connection Error",
                                                                    " Server is either overloaded or unreachable at the moment .. \n\n'Download now' button will be re-enabled in 10 seconds"));
                                                }

                                            } catch (NoSuchElementException e) {
                                                Log.e(this.getClass().getName(), "tokenizer does not contain dwnlodVersion");
                                                mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Server Connection Error",
                                                                " Server is either overloaded or unreachable at the moment .. \n\n'Download now' button will be re-enabled in 10 seconds"));
                                            }

                                        } else {
                                            Log.e(this.getClass().getName(), "tokenizer count is less than");
                                            mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Server Connection Error",
                                                            " Server is either overloaded or unreachable at the moment .. \n\n'Download now' button will be re-enabled in 10 seconds"));
                                        }
                                    }

                                } else {
                                    Log.e(this.getClass().getName(), "output.isEmpty()!");
                                    mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Server Connection Error",
                                                    " Server is either overloaded or unreachable at the moment .. \n\n'Download now' button will be re-enabled in 10 seconds"));
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

                            } else {
                                Log.e(this.getClass().getName(), "no wifi or wimax !");
                                mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Internet Connection Error",
                                                "This action can only be performed via Wifi, WiMax or Ethernet connection. Please Enable either of ther previously mentioned connection types and try again .. \n\n'Download now' button will be re-enabled in 10 seconds"));
                            }
                        } else {
                            Log.e(this.getClass().getName(), "netInfo is null!");
                            mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Internet Connection Error",
                                            "This action can only be performed via Wifi, WiMax or Ethernet connection. Please Enable either of ther previously mentioned connection types and try again .. \n\n'Download now' button will be re-enabled in 10 seconds"));
                        }
                    } else {
                        Log.e(this.getClass().getName(), "mConctMgr is null!");
                        mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Internet Connection Error", "Connectivity Service error  .. \n\n'Download now' button will be re-enabled in 10 seconds"));
                    }
                } else {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mUpdateStatusTitleTextView.setText("System Error");
                            mUpdateStatusTextView.setText("Failed to get current system version ..");
                            mInstallNowButton.setVisibility(View.GONE);
                            mDownloadNowButton.setVisibility(View.GONE);
                            mCheckNowButton.setVisibility(View.VISIBLE);
                            mCheckNowButton.setEnabled(false);
                        }
                    });
                }

            } catch (ConnectException e) {
                Log.e(this.getClass().getName(), "NumberFormatException", e);
                mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Connection Exception", "Communication exception occurred. " + e.getLocalizedMessage()
                                + " .. \n\n'Check now' button will be re-enabled in 10 seconds"));
            } catch (NumberFormatException e) {
                Log.e(this.getClass().getName(), "NumberFormatException", e);
                mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Connection Exception", "Communication exception occurred. " + e.getLocalizedMessage()
                                + " .. \n\n'Check now' button will be re-enabled in 10 seconds"));
            } catch (Exception e) {
                mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Connection Exception", "General exception occurred. " + e.getLocalizedMessage() + " .. \n\n'Download now' button will be re-enabled in 10 seconds"));
            }
        }
    }

    class InstallUpdateThread implements Runnable {

        @Override
        public void run() {
            try {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {

                        mCheckNowButton.setVisibility(View.GONE);
                        mDownloadNowButton.setVisibility(View.GONE);
                        mInstallNowButton.setVisibility(View.VISIBLE);
                        mInstallNowButton.setEnabled(false);
                        mUpdateStatusTextView.setText("Installing please wait .. ");
                    }

                });

            } catch (Exception e) {
                mHandler.post(new UpdateOnlyErrorStatusTitleMsgThread("Connection Exception", "General exception occurred. " + e.getLocalizedMessage() + " .. \n\n'Download now' button will be re-enabled in 10 seconds"));
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
                        String tmpIncremental = "eng.firas.20130418.140319"; // Build.VERSION.INCREMENTAL;
                        if (tmpIncremental.length() > 16) {
                            String tmpDtIncremental = tmpIncremental.substring(tmpIncremental.length() - 15);
                            long tmpDateIncr = Long.parseLong(tmpDtIncremental.substring(0, 8));
                            long tmptimeIncr = Long.parseLong(tmpDtIncremental.substring(9, 15));

                            if (mServerDate >= tmpDateIncr) {
                                if (mServerTime > tmptimeIncr) {
                                    mUpdateStatusTitleTextView.setText("Update Available");
                                    mUpdateStatusTextView.setText("New update " + String.valueOf(mServerDate) + "." + String.valueOf(mServerTime) + ". Last checked for update " + mLastServerD8TimeCheck);
                                    mInstallNowButton.setVisibility(View.GONE);
                                    mCheckNowButton.setVisibility(View.GONE);
                                    mDownloadNowButton.setVisibility(View.VISIBLE);
                                    mDownloadNowButton.setEnabled(true);

                                } else {
                                    mUpdateStatusTitleTextView.setText("Your system is currently up to date");
                                    mUpdateStatusTextView.setText("Last checked for update " + mLastServerD8TimeCheck);
                                    mDownloadNowButton.setVisibility(View.GONE);
                                    mInstallNowButton.setVisibility(View.GONE);
                                    mCheckNowButton.setVisibility(View.VISIBLE);
                                    mCheckNowButton.setEnabled(true);

                                }
                            } else {
                                mUpdateStatusTitleTextView.setText("Your system is currently up to date");
                                mUpdateStatusTextView.setText("Last checked for update " + mLastServerD8TimeCheck);
                                mDownloadNowButton.setVisibility(View.GONE);
                                mInstallNowButton.setVisibility(View.GONE);
                                mCheckNowButton.setVisibility(View.VISIBLE);
                                mCheckNowButton.setEnabled(true);

                            }

                        } else {
                            mUpdateStatusTitleTextView.setText("System Error");
                            mUpdateStatusTextView.setText("Failed to get current system version ..");
                            mDownloadNowButton.setVisibility(View.GONE);
                            mInstallNowButton.setVisibility(View.GONE);
                            mCheckNowButton.setVisibility(View.VISIBLE);
                            mCheckNowButton.setEnabled(false);
                        }

                        mIndeterminate = false;

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

    class UpdateDownloadProgressThread implements Runnable {
        int mProgress;

        UpdateDownloadProgressThread(int progres) {
            mProgress = progres;
        }

        @Override
        public void run() {
            try {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mProgressBar.setProgress(0);
                        mProgressBar.setSecondaryProgress(mProgress);
                    }

                });

            } catch (Exception e) {
                Log.e(this.getClass().getName(), "Exception", e);
            }
        }
    }

}
