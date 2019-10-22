package de.schildbach.wallet.ui.safe.utils;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.TextView;

/**
 * 判断小数点格式
 * @author zhangmiao
 */
public class EditTextJudgeNumber implements TextWatcher {

    private TextView textView;
    private int frontCount;
    private int behindCount;
    private boolean canLessThan1 = false;//是否能小于1

    public EditTextJudgeNumber(TextView textView, int frontCount, int behindCount, boolean canLessThan1) {
        this.textView = textView;
        this.frontCount = frontCount;
        this.behindCount = behindCount;
        this.canLessThan1 = canLessThan1;
    }

    public EditTextJudgeNumber(TextView textView, int frontCount, int behindCount) {
        this.textView = textView;
        this.frontCount = frontCount;
        this.behindCount = behindCount;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable editable) {
        judgeNumber(editable);
    }


    public void judgeNumber(Editable edt) {
        String content = edt.toString();
        if (!canLessThan1) {
            if (content.startsWith("0") || content.indexOf(".") == 0) {
                if (content.length() > 1 && content.startsWith("0")) {
                    edt.delete(0, content.indexOf("0") + 1);
                } else if (content.length() > 0 && content.indexOf(".") == 0) {
                    edt.delete(0, content.indexOf(".") + 1);
                } else {
                    edt.delete(0, content.length());
                }
                return;
            }
        } else {
            if (content.startsWith(".")) {
                edt.delete(0, 1);
            }
        }
        int posDot = content.indexOf(".");
        int index = textView.getSelectionStart(); //获取光标位置
        if (posDot < 0) { //不包含小数点
            if (content.length() <= frontCount) {
                return; //小于frontCount数直接返回
            } else {
                if(index > 0){
                    edt.delete(index - 1, index);
                }
                return;
            }
        }
        if (posDot > frontCount) {
            if (index > 0) {
                edt.delete(index - 1, index);
            }
            return;
        }
        if (content.length() - posDot - 1 > behindCount) {
            if (index > 0) {
                edt.delete(index - 1, index);
            }
            return;
        }
    }
}