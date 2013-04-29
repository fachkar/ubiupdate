package com.barbar.ubiupdate;

import com.barbar.ubiupdate.IUbiUpdate;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class UpdateCheckerService extends Service {
    public volatile UpdateCheckerService mUpdateCheckerService = null;
    public volatile ConnectivityManager mConctMgr = null;
    public final IUbiUpdate.Stub mBinder = new IUbiUpdate.Stub(){
        public String getLatestUpdateVersion() throws RemoteException {
            return "null";
         }
    };
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(null, " -- -- UpdateCheckerService::onCreate");
        mUpdateCheckerService = this;
    }
    
    @Override
    public void onDestroy() {
        Log.d(null, " -- -- UpdateCheckerService::onDestroy");
        mUpdateCheckerService = null;
        mConctMgr = null;
    }
    
    @Override
    public IBinder onBind(Intent arg0) {        
        return mBinder;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Thread workingthread = new Thread(new OnStartServiceThread());
        workingthread.start();
        return START_STICKY;
    }
    
    class StopSelfThread implements Runnable{

        @Override
        public void run() {
            try {
                Thread.sleep(3000);
                mUpdateCheckerService.stopSelf();
            } catch (InterruptedException e) {
                Log.e(this.getClass().getName(), " InterruptedException ", e);
            }catch (Exception e) {
                Log.e(this.getClass().getName(), "Exception", e);
            }            
        }        
    }
    
    class OnStartServiceThread implements Runnable{

        @Override
        public void run() {
            try {

                mConctMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if(mConctMgr != null){
                    NetworkInfo netInfo = mConctMgr.getActiveNetworkInfo();
                    
                    if(netInfo != null){
                        int netType = netInfo.getType();
                        if(netInfo.isConnected() && (netType == ConnectivityManager.TYPE_ETHERNET || netType == ConnectivityManager.TYPE_WIFI || netType == ConnectivityManager.TYPE_WIMAX) ){
                            Log.d(null, "-- -- connected " + netType);
                        }
                    }
                }
                
                
                Thread workingthread = new Thread(new StopSelfThread());
                workingthread.start();
            } catch (Exception e) {
                Log.e(this.getClass().getName(), "Exception", e);
            }            
        }        
    }

}
