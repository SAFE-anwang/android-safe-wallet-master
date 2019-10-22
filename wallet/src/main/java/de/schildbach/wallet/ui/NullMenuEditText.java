package de.schildbach.wallet.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import de.schildbach.wallet.R;

@SuppressLint("AppCompatCustomView")
public class NullMenuEditText extends EditText {

    boolean canPaste() {
        return false;
    }

    boolean canCut() {
        return false;
    }

    boolean canCopy() {
        return false;
    }

    boolean canSelectAllText() {
        return false;
    }

    boolean canSelectText() {
        return false;
    }

    boolean textCanBeSelected() {
        return false;
    }

    public NullMenuEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLongClickable(false);
        setTextIsSelectable(false);
        setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {

            }
        });

    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        return true;
    }
}
