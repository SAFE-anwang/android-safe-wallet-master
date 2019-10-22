package de.schildbach.wallet.ui.safe.utils;

import android.text.*;
import android.widget.EditText;
import android.widget.TextView;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 按钮状态监控管理器
 *
 *  @author zhangmiao
 */
public class InputManager {

	private List<SoftReference<TextView>> mInputs;
	private SoftReference<TextView> mSubmits;
	private OnOuterCheckListener listener;

	public static InputManager checkEmptyListener(List<TextView> inputs, TextView submit){
		return new InputManager(inputs, submit, null);
	}

	public static InputManager checkEmptyListener(List<TextView> inputs, TextView submit, OnOuterCheckListener listener){
		return new InputManager(inputs, submit, listener);
	}

	public InputManager(List<TextView> inputs, TextView submit, OnOuterCheckListener listener) {
		super();
		this.listener = listener;
		mInputs = new ArrayList<>();
		for (TextView mTextView : inputs) {
			filterTrim(mTextView);
			mInputs.add(new SoftReference<>(mTextView));
		}
		submit.setEnabled(false);
		mSubmits = new SoftReference<>(submit);
		for (int i = 0; i < inputs.size(); i++) {
			TextWatcher watcher = new TextWatcher() {
				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {

				}
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {

				}
				@Override
				public void afterTextChanged(Editable s) {
					innerCheck();
				}
			};
			inputs.get(i).addTextChangedListener(watcher);
		}
		innerCheck();
	}
	
	//添加输入框监听
	public void addTextViews(TextView[] TextViews){
		for (TextView mTextView : TextViews) {
			mInputs.add(new SoftReference<>(mTextView));
		}
		for (int i = 0; i < TextViews.length; i++) {
			TextWatcher watcher = new TextWatcher() {

				@Override
				public void onTextChanged(CharSequence s, int start,
                                          int before, int count) {

				}

				@Override
				public void beforeTextChanged(CharSequence s, int start,
                                              int count, int after) {

				}

				@Override
				public void afterTextChanged(Editable s) {
					innerCheck();
				}
			};
			TextViews[i].addTextChangedListener(watcher);
		}
		innerCheck();
	}

	public void addTextView(TextView mTextView){
		mInputs.add(new SoftReference<>(mTextView));
		innerCheck();
	}
	
	//移除输入框监听
	public void removeTextViews(TextView[] TextViews){
		for (TextView mTextView : TextViews) {
			for (int i = 0; i < mInputs.size(); i++) {
				if (mInputs.get(i).get().equals(mTextView)) {
					mInputs.remove(i);
				}
			}
		}
		innerCheck();
	}

	/**
	 * 检查格式
	 * */
	public void innerCheck() {
		if (isInputEmpty()) {
			setTextView(false);
		} else {
			if(listener != null){
				setTextView(listener.onOuterCheck());
			} else {
				setTextView(true);
			}
		}
	}

	private void setTextView(boolean b) {
		mSubmits.get().setEnabled(b);
	}

	/**
	 * 如果TextView为空返回true,否则返回false
	 * */
	public boolean isInputEmpty() {
		for (SoftReference<TextView> softTextView : mInputs) {
			TextView textView = softTextView.get();
			if (textView != null) {
				if (TextUtils.isEmpty(textView.getText())) {
					return true;
				}
			}
		}
		return false;
	}

	public interface OnOuterCheckListener{
		boolean onOuterCheck();
	}

	public void filterTrim(TextView editText){
//		InputFilter filter = new InputFilter() {
//			public CharSequence filter(CharSequence source, int start, int end,
//									   Spanned dest, int dstart, int dend) {
//				if (source.equals(" ") || source.toString().contentEquals("\n")) return "";
//				else return null;
//			}
//		};
//		List<InputFilter> listFilter = new ArrayList<>();
//		listFilter.add(filter);
//		InputFilter[] oldFilters = editText.getFilters();
//		if (oldFilters != null) {
//			for (InputFilter item: oldFilters) {
//				listFilter.add(item);
//			}
//		}
//        InputFilter[] newFilters = new InputFilter[listFilter.size()];
//		editText.setFilters(listFilter.toArray(newFilters));
	}

	public static void limitTrim(EditText editText){
		InputFilter filter = new InputFilter() {
			public CharSequence filter(CharSequence source, int start, int end,
									   Spanned dest, int dstart, int dend) {
				if (source.equals(" ") || source.toString().contentEquals("\n")) return "";
				else return null;
			}
		};
		List<InputFilter> listFilter = new ArrayList<>();
		listFilter.add(filter);
		InputFilter[] oldFilters = editText.getFilters();
		if (oldFilters != null) {
			for (InputFilter item: oldFilters) {
				listFilter.add(item);
			}
		}
        InputFilter[] newFilters = new InputFilter[listFilter.size()];
		editText.setFilters(listFilter.toArray(newFilters));
	}

}
