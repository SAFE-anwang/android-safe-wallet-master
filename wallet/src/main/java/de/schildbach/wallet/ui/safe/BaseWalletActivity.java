package de.schildbach.wallet.ui.safe;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;

import android.os.Bundle;

import de.schildbach.wallet.ui.AbstractBindServiceActivity;
import de.schildbach.wallet.R;

/**
 * 基础钱包Activity
 * @author zhangmiao
 */
public final class BaseWalletActivity extends AbstractBindServiceActivity {

    public static String CLASS = "CLASS";
    Fragment fragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base);
        Intent intent = getIntent();
        Class className = (Class) intent.getSerializableExtra(CLASS);
        fragment = Fragment.instantiate(this, className.getName(), getIntent().getExtras());
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction ft = fragmentManager.beginTransaction();
        ft.add(R.id.content_view, fragment);
        ft.commit();
    }

    @Override
    public void onBackPressed() {
        if (fragment != null && fragment instanceof BaseFragment) {
            BaseFragment curBase = (BaseFragment) fragment;
            if (!curBase.onBackPressed()) {
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void serviceBinded() {
        if (fragment != null && fragment instanceof BaseFragment) {
            BaseFragment curBase = (BaseFragment) fragment;
            curBase.serviceBinded();
        }
    }

    @Override
    public void dashRejectButtonOk() {
        super.dashRejectButtonOk();
        finish();
    }

}
