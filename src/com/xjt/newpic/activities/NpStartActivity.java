
package com.xjt.newpic.activities;

import com.xjt.newpic.preference.GlobalPreference;
import com.xjt.newpic.utils.PackageUtils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class NpStartActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int code = PackageUtils.getVersionCode(this);
        if (GlobalPreference.getLastGuideCode(this) < code) {
            GlobalPreference.setLastGuideCode(this, code);
            Intent intent = new Intent();
            intent.setClass(this, NpGuideActivity.class);
            startActivity(intent);
        } else {
            Intent intent = new Intent();
            intent.setClass(this, NpMainActivity.class);
            startActivity(intent);
        }
        finish();
    }

}
