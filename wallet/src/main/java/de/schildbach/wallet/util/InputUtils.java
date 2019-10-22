package de.schildbach.wallet.util;

import android.content.Context;
import android.text.*;
import android.util.Log;
import android.widget.EditText;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InputUtils {

    private static Pattern emoji = Pattern.compile("[\ud83c\udc00-\ud83c\udfff]|[\ud83d\udc00-\ud83d\udfff]|[\u2600-\u27ff]", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);

    public static void filterChart(final EditText editText, final int length) {
        InputFilter inputFilter = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                try {
                    if (emoji.matcher(source).find()) {
                        return "";
                    }
                    return source;
                } catch (Exception e) {
                    return "";
                }
            }
        };
        List<InputFilter> listFilter = new ArrayList<>();
        listFilter.add(inputFilter);
        InputFilter[] oldFilters = editText.getFilters();
        if (oldFilters != null) {
            for (InputFilter item : oldFilters) {
                listFilter.add(item);
            }
        }
        InputFilter[] newFilters = new InputFilter[listFilter.size()];
        editText.setFilters(listFilter.toArray(newFilters));

        //限制问题内容输入字符长度
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    //转换成中文字符集的长度
                    String src = editText.getText().toString();
                    int srcLen = src.getBytes("UTF-8").length;
                    if (srcLen > length) {
                        String result = byteSubString(src, length);
                        editText.setText(result);
                        editText.setSelection(result.length());
                    }
                } catch (Exception e) {
                }
            }
        });
    }

    public static void filterSign(final EditText editText) {
        InputFilter inputFilter = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                String speChat="[`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
                Pattern pattern = Pattern.compile(speChat);
                Matcher matcher = pattern.matcher(source.toString());
                if(matcher.find())return "";
                else return null;
            }
        };
        List<InputFilter> listFilter = new ArrayList<>();
        listFilter.add(inputFilter);
        InputFilter[] oldFilters = editText.getFilters();
        if (oldFilters != null) {
            for (InputFilter item : oldFilters) {
                listFilter.add(item);
            }
        }
        InputFilter[] newFilters = new InputFilter[listFilter.size()];
        editText.setFilters(listFilter.toArray(newFilters));
    }

    private static String byteSubString(String str, int num) throws Exception {
        int srcLen = str.getBytes("UTF-8").length;
        if (srcLen > num) {
            int subSize = (srcLen - num) / 3 > 1 ? (srcLen - num) / 3 : 1;
            str = str.substring(0, str.length() - subSize);
            str = byteSubString(str, num);
        }
        return str;
    }

    public static boolean isStartNumeric(String str) {
        Pattern pattern = Pattern.compile("[0-9\\s]*");
        Matcher isNum = pattern.matcher(str);
        if (!isNum.matches()) {
            return false;
        }
        return true;
    }

}
