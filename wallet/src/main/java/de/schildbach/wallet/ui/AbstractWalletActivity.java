/*
 * Copyright 2011-2015 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui;

import android.content.Intent;
import android.support.annotation.LayoutRes;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;

import android.view.MenuItem;

import de.schildbach.wallet.ui.safe.MainActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.R;

import android.support.v7.app.AppCompatActivity;
import android.app.ActivityManager.TaskDescription;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

/**
 * @author Andreas Schildbach
 */
public abstract class AbstractWalletActivity extends AppCompatActivity {

    protected static final Logger log = LoggerFactory.getLogger(AbstractWalletActivity.class);

    private WalletApplication application;

    private TextView tvTitle;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        application = (WalletApplication) getApplication();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            setTaskDescription(new TaskDescription(null, null, getResources().getColor(R.color.bg_action_bar)));

        super.onCreate(savedInstanceState);
    }

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(layoutResID);
        initToolbar();
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

    public WalletApplication getWalletApplication() {
        return application;
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
