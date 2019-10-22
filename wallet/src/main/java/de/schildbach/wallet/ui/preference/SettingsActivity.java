package de.schildbach.wallet.ui.preference;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.TextView;

import de.schildbach.wallet.ui.safe.BaseFragment;
import de.schildbach.wallet.ui.safe.MainActivity;
import de.schildbach.wallet.R;

/**
 * @author zhangmiao
 */
public final class SettingsActivity extends AppCompatActivity {

    public static String CLASS = "CLASS";
    Fragment fragment;
    private TextView tvTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        initToolbar();
        Intent intent = getIntent();
        Class className = (Class) intent.getSerializableExtra(CLASS);
        fragment = Fragment.instantiate(this, className.getName(), getIntent().getExtras());
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction ft = fragmentManager.beginTransaction();
        ft.add(R.id.content_view, fragment);
        ft.commit();
    }

    private void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setBackgroundResource(R.drawable.safe_top_bg);
            tvTitle = (TextView) toolbar.findViewById(R.id.tv_title);
            tvTitle.setText(getTitle());
            setSupportActionBar(toolbar);
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setDisplayShowHomeEnabled(true);
                actionBar.setDisplayShowTitleEnabled(false);
            }
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        if (tvTitle != null)
            tvTitle.setText(title);
    }

    @Override
    public void setTitle(int titleId) {
        if (tvTitle != null)
            tvTitle.setText(titleId);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish();
                return false;
            default:
                return super.onOptionsItemSelected(item);
        }
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
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        startActivity(new Intent(this, MainActivity.class));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        setContentView(R.layout.fragment_empty);
    }

}