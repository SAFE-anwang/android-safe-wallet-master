package de.schildbach.wallet.ui;

import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.db.GetCandyData;
import de.schildbach.wallet.ui.safe.utils.SafeUtils;
import de.schildbach.wallet.util.Qr;
import de.schildbach.wallet.R;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.wallet.Wallet;

import java.math.BigDecimal;
import java.util.Date;

public class ReceiveDetailActivity extends AppCompatActivity {

    private GetCandyData getCandyData;
    private TextView fee_title,count_tv,address_tv,tx_id,height,timeView;
    private ImageView addressQrView;
    private Configuration config;
    private Transaction tx;
    private WalletApplication application;
    private Wallet wallet;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_detail);
        getCandyData= (GetCandyData) getIntent().getSerializableExtra("candy");
        this.application = (WalletApplication) getApplication();
        this.config = application.getConfiguration();
        this.wallet = application.getWallet();
        tx = wallet.getTransaction(Sha256Hash.wrap(getCandyData.txId));
        initView();
        initData();
    }

    private void initView() {
        fee_title= (TextView) findViewById(R.id.fee_title);
        count_tv= (TextView) findViewById(R.id.count_tv);
        address_tv= (TextView) findViewById(R.id.address_tv);
        tx_id= (TextView) findViewById(R.id.tx_id);
        height= (TextView) findViewById(R.id.height);
        timeView= (TextView) findViewById(R.id.time);
        addressQrView= (ImageView) findViewById(R.id.address_qr);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setBackgroundResource(R.drawable.safe_top_bg);
            setSupportActionBar(toolbar);
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setDisplayShowHomeEnabled(true);
            }
            actionBar.setTitle(getResources().getString(R.string.receive_detail_str));
        }
        findViewById(R.id.browse).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handerBrowseTx();
            }
        });
    }

    private void initData() {
        fee_title.setText(getCandyData.assetName);
        BigDecimal decimal = new BigDecimal(getCandyData.candyAmount);
        BigDecimal amountDecimal = decimal.divide(new BigDecimal(SafeUtils.getRealDecimals(getCandyData.decimals)));
        count_tv.setText(amountDecimal.toString());
        address_tv.setText(getCandyData.address);
        tx_id.setText(getCandyData.txId);
        int chainHeight = 0;
        final TransactionConfidence confidence = tx.getConfidence();
        final TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
        if (confidenceType != TransactionConfidence.ConfidenceType.BUILDING) {
            height.setText(getString(R.string.safe_no_data));
        } else {
            chainHeight = confidence.getAppearedAtChainHeight();
            height.setText("" + chainHeight);
        }
        final Date time = tx.getUpdateTime();
        timeView.setText(DateUtils.formatDateTime(this, time.getTime(),
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME));
        Uri uri = Uri.withAppendedPath(config.getBlockExplorer(), "tx/" + tx.getHashAsString());
        BitmapDrawable addressQrBitmap = new BitmapDrawable(getResources(), Qr.bitmap(uri.toString()));
        addressQrBitmap.setFilterBitmap(false);
        addressQrView.setImageDrawable(addressQrBitmap);

    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish();
                return false;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void handerBrowseTx() {
        startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.withAppendedPath(config.getBlockExplorer(), "tx/" + getCandyData.txId)));
    }

}
