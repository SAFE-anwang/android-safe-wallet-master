package de.schildbach.wallet.ui.safe;

import android.content.*;
import de.schildbach.wallet.db.BaseDaoImpl;
import de.schildbach.wallet.db.IssueData;
import de.schildbach.wallet.db.WalletAssetTx;
import org.bitcoinj.utils.SafeConstant;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

/**
 * 资产数量异步加载
 * @author zhangmiao
 */
public final class AssetAmountLoader extends AsyncTaskLoader<Long> {

    private IssueData selectIssue;

    public AssetAmountLoader(Context context, IssueData selectIssue) {
        super(context);
        this.selectIssue = selectIssue;
        safeForceLoad();
    }

    @Override
    public Long loadInBackground() {
        return getIssueAmount(selectIssue);
    }

    public static long getIssueAmount(IssueData selectIssue) {
        String assetId = selectIssue.assetId;
        BigDecimal totalAmount = new BigDecimal(selectIssue.totalAmount);
        BigDecimal usedAmount = new BigDecimal(selectIssue.firstIssueAmount);
        BaseDaoImpl assetTxDao = new BaseDaoImpl(WalletAssetTx.class);
        try {
            List<WalletAssetTx> assetList = (List<WalletAssetTx>) assetTxDao.query(new String[]{"assetId", "appCommand"}, new Object[]{assetId, SafeConstant.CMD_ADD_ISSUE});
            for (WalletAssetTx item : assetList) {
                usedAmount = usedAmount.add(new BigDecimal(item.amount));
            }
        } catch (SQLException e) {
        }
        return totalAmount.subtract(usedAmount).longValue();
    }

    private void safeForceLoad() {
        try {
            forceLoad();
        } catch (final RejectedExecutionException e) {
        }
    }

}
