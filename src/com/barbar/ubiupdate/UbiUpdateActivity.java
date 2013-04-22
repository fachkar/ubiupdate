package com.barbar.ubiupdate;

import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class UbiUpdateActivity extends Activity {
    
    public volatile Button mCheckNowButton = null;
    public volatile Context mContext = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ubi_update);
        mContext = this;
        mCheckNowButton = (Button)findViewById(R.id.check_now_button);
        
        if(mCheckNowButton != null){
            mCheckNowButton.setOnClickListener(new View.OnClickListener() {                
                @Override
                public void onClick(View v) {
                    Log.d(null, " -- -- mCheckNowButton.setOnClickListener");
                    startService(new Intent(mContext, UpdateCheckerService.class));               
                }
            });
        }
    }

}
