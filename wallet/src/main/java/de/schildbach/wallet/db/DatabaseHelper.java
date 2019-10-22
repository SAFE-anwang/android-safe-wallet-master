package de.schildbach.wallet.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;

/**
 * 创建数据库
 *
 * @author zhangmiao
 */
public class DatabaseHelper extends OrmLiteSqliteOpenHelper {

    private static int version = 2;

    public DatabaseHelper(Context context) {
        super(context, "safe.db", null, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db, ConnectionSource source) {
        try {
            TableUtils.createTableIfNotExists(source, IssueData.class);
            TableUtils.createTableIfNotExists(source, PutCandyData.class);
            TableUtils.createTableIfNotExists(source, GetCandyData.class);
            TableUtils.createTableIfNotExists(source, WalletAssetTx.class);
            TableUtils.createTableIfNotExists(source, CandyAddrData.class);
            TableUtils.createTableIfNotExists(source, TotalAmountData.class);
            TableUtils.createTableIfNotExists(source, FilterAmountData.class);
            TableUtils.createTableIfNotExists(source, GetCandyAmountData.class);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, ConnectionSource source, int oldVersion, int newVersion) {
        if (oldVersion == 1 && newVersion == 2) {
            try {
                TableUtils.createTableIfNotExists(source, GetCandyAmountData.class);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

}
