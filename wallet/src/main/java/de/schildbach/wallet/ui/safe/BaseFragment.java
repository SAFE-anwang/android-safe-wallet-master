package de.schildbach.wallet.ui.safe;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import de.schildbach.wallet.ui.WalletAddressDialogFragment;
import de.schildbach.wallet.R;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基础Fragment
 * @author zhangmiao
 */
public abstract class BaseFragment extends Fragment {

    private View rootView = null;
    public View getRootView() {
        return rootView;
    }
    private Handler mHander = new Handler();
    
    public static final Logger log = LoggerFactory.getLogger(BaseFragment.class);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(getLayoutResId(), container, false);
        rootView.setBackgroundResource(R.color.white);
        rootView.setClickable(true);
        initView();
        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initData(savedInstanceState);
    }

    /**
     * 界面布局resId
     * @return 布局resId
     */
    public abstract int getLayoutResId();

    /**
     *  初始化界面
     */
    public void initView(){}

    /**
     * 初始化界面数据
     */
    public void initData(Bundle savedInstanceState) {}


    /**
     * 通过Id查找View， 为了和Activity统一。
     * @param id
     * @return view
     */
    public View findViewById(int id) {
        if (getRootView() == null) {
            return null;
        }
        return rootView.findViewById(id);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rootView = null;
    }

    public Handler getHander() {
        return mHander;
    }

    public boolean onBackPressed(){
        return false;
    }

    protected void serviceBinded(){

    }

    public void setBackgroundResource(int res){
        rootView.setBackgroundResource(res);
    }
}

