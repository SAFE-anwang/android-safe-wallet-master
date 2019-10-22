package de.schildbach.wallet.db;

import com.j256.ormlite.dao.Dao;
import de.schildbach.wallet.WalletApplication;

import java.sql.SQLException;

/**
 * 实现增删改查
 * @author zhangmiao
 */
@SuppressWarnings("hiding")
public class BaseDaoImpl<T, Integer> extends BaseDao<T, Integer> {

	private Class<T> clazz;

	public BaseDaoImpl(Class<T> clazz) {
		this.clazz = clazz;
	}

	@Override
	public Dao<T, Integer> getDao() throws SQLException {
		return WalletApplication.getInstance().getDatabaseHelper().getDao(clazz);
	}

}
