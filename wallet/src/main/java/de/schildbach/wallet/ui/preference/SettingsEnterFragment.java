package de.schildbach.wallet.ui.preference;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import de.schildbach.wallet.ui.safe.BaseFragment;
import de.schildbach.wallet.R;

/**
 * @author zhangmiao
 */
public final class SettingsEnterFragment extends BaseFragment {

    View mineSetting;
    View mineDiagnostics;

    @Override
    public int getLayoutResId() {
        return R.layout.fragment_settings_enter;
    }

    @Override
    public void initView() {
        super.initView();
        mineSetting = findViewById(R.id.mine_setting);

        mineSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), SettingsActivity.class);
                intent.putExtra(SettingsActivity.CLASS, SettingsFragment.class);
                startActivity(intent);
            }
        });
        mineDiagnostics = findViewById(R.id.mine_diagnostics);
        mineDiagnostics.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), SettingsActivity.class);
                intent.putExtra(SettingsActivity.CLASS, DiagnosticsFragment.class);
                startActivity(intent);
            }
        });
    }

    @Override
    public void initData(Bundle savedInstanceState) {
        super.initData(savedInstanceState);
        getActivity().setTitle(getString(R.string.button_settings));
    }
}
