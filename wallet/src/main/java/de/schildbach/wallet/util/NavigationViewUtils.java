package de.schildbach.wallet.util;

import android.support.design.internal.BottomNavigationItemView;
import android.support.design.internal.BottomNavigationMenuView;
import android.support.design.widget.BottomNavigationView;

import java.lang.reflect.Field;

public class NavigationViewUtils {

    public static BottomNavigationItemView getBottomNavigationItemView(BottomNavigationView navigationView, int position) {
        BottomNavigationMenuView menuView = (BottomNavigationMenuView) navigationView.getChildAt(0);
        return (BottomNavigationItemView) menuView.getChildAt(position);
    }

}