package de.schildbach.wallet.util;

import android.content.Context;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import de.schildbach.wallet.R;

/**
 * Created by zhangmiao on 2017/9/14.
 */

public class AddSubEditText extends LinearLayout {

    private View sub;
    private EditText num;
    private View add;
    public static final int MAX_VALUE = 120;

    private OnNumChangeListener listener;

    public void setNumChangeListener(OnNumChangeListener listener) {
        this.listener = listener;
    }

    public AddSubEditText(Context context) {
        super(context);
        initView();
    }

    public AddSubEditText(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public void initView() {
        inflate(getContext(), R.layout.edittext_add_sub, this);
        sub = findViewById(R.id.sub);
        num = (EditText) findViewById(R.id.num);
        add = findViewById(R.id.add);

        setValue(1);
        sub.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                int value = getNumValue();
                if (value > 1) {
                    value--;
                    setValue(value);
                    if (listener != null) {
                        listener.onNumChange(value);
                    }
                }
            }
        });

        add.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                int value = getNumValue();
                if(value < MAX_VALUE){
                    value++;
                    setValue(value);
                    if (listener != null) {
                        listener.onNumChange(value);
                    }
                }

            }
        });
    }

    public void setValue(int value) {
        num.removeTextChangedListener(mWatcher);
        num.setText("" + value);
        num.addTextChangedListener(mWatcher);
    }

    public void setSelection(int index){
        num.setSelection(index);
    }

    public Integer getNumValue() {
        if (TextUtils.isEmpty(num.getText())) {
            return 0;
        } else {
            return Integer.parseInt(num.getText().toString());
        }
    }

    public EditText getNum(){
        return num;
    }

    public interface OnNumChangeListener {
        void onNumChange(Integer num);
    }

    TextWatcher mWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            int value = getNumValue();
            if (listener != null) {
                listener.onNumChange(value);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (s.toString().startsWith("0") || s.toString().indexOf(".") == 0) {
                if (s.toString().length() > 1 && s.toString().startsWith("0")){
                    s.delete(0, s.toString().indexOf("0")+1);
                }
                return;
            }
        }
    };
}