/*
 * Copyright 2013-2015 the original author or authors.
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

import android.widget.RadioGroup;
import de.schildbach.wallet.util.ViewPagerTabs;
import de.schildbach.wallet.R;

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.MenuItem;

/**
 * @author Andreas Schildbach
 */
public final class NetworkMonitorActivity extends AbstractBindServiceActivity {
    private PeerListFragment peerListFragment;
    private BlockListFragment blockListFragment;

    private ViewPager pager;
    private RadioGroup group;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.network_monitor_onepane);

        peerListFragment = new PeerListFragment();
        blockListFragment = new BlockListFragment();

        pager = (ViewPager) findViewById(R.id.network_monitor_pager);

        group = (RadioGroup) findViewById(R.id.network_monitor_group);
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.peer) {
                    pager.setCurrentItem(0);
                } else {
                    pager.setCurrentItem(1);
                }
            }
        });

        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    group.check(R.id.peer);
                } else {
                    group.check(R.id.block);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        PagerAdapter pagerAdapter = new PagerAdapter(getFragmentManager());

        pager.setAdapter(pagerAdapter);
        pager.setPageMargin(2);
        pager.setPageMarginDrawable(R.color.bg_less_bright);


    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class PagerAdapter extends FragmentStatePagerAdapter {
        public PagerAdapter(final FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public Fragment getItem(final int position) {
            if (position == 0)
                return peerListFragment;
            else
                return blockListFragment;
        }
    }

    @Override
    public void serviceBinded() {

    }


}
