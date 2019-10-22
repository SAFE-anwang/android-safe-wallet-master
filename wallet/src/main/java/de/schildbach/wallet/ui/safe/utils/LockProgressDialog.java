package de.schildbach.wallet.ui.safe.utils;

import android.app.Dialog;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import de.schildbach.wallet.R;

/**
 * 公用锁屏对话框。
 * 
 * @author zm
 * 
 */
public class LockProgressDialog extends Dialog {

	private TextView loadingText;

	public LockProgressDialog(Context context) {
		super(context, R.style.My_Theme_NoTitleDialog);
		setContentView(R.layout.progress_dialog);
		setCanceledOnTouchOutside(false);
		initView();
	}

	private void initView() {
		loadingText = (TextView) findViewById(R.id.loading_text);
	}

	public void setTitle(CharSequence text) {
		if (TextUtils.isEmpty(text)) {
			loadingText.setVisibility(View.GONE);
		} else {
			loadingText.setVisibility(View.VISIBLE);
			loadingText.setText(text);
		}
	}

	public void show() {
		try {
			super.show();
		} catch (Exception e) {
		}
	}

	public void dismiss() {
		try {
			super.dismiss();
		} catch (Exception e) {
		}
	}

}
