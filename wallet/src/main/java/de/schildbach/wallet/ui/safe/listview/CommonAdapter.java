package de.schildbach.wallet.ui.safe.listview;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

/**
 * 公用ListView适配器
 * 
 * @author zm
 * @param <T>
 */
public abstract class CommonAdapter<T> extends BaseAdapter {

	protected Context context;

	protected int layoutResId;

	protected List<T> data;

	public CommonAdapter(Context context, int layoutResId) {
		this(context, layoutResId, null);
	}

	public CommonAdapter(Context context, int layoutResId, List<T> data) {
		this.data = data == null ? new ArrayList<T>() : new ArrayList<T>(data);
		this.context = context;
		this.layoutResId = layoutResId;
	}

	@Override
	public int getCount() {
		return data.size();
	}

	@Override
	public T getItem(int position) {
		if (position >= data.size())
			return null;
		return data.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, final ViewGroup parent) {
		ViewHolder viewHolder = ViewHolder.createViewHolder(context, convertView, parent, layoutResId, position);
		convert(viewHolder, getItem(position), position);
		return viewHolder.getView();

	}

	public void add(T elem) {
		data.add(elem);
		notifyDataSetChanged();
	}

	public void addAll(List<T> elem) {
		data.addAll(elem);
		notifyDataSetChanged();
	}

	public void set(T oldElem, T newElem) {
		set(data.indexOf(oldElem), newElem);
	}

	public void set(int index, T elem) {
		data.set(index, elem);
		notifyDataSetChanged();
	}

	public void remove(T elem) {
		data.remove(elem);
		notifyDataSetChanged();
	}

	public void remove(int index) {
		data.remove(index);
		notifyDataSetChanged();
	}

	public void replaceAll(List<T> elem) {
		data.clear();
		data.addAll(elem);
		notifyDataSetChanged();
	}

	public boolean contains(T elem) {
		return data.contains(elem);
	}

	public void clear() {
		data.clear();
		notifyDataSetChanged();
	}

	public List<T> getData() {
		return data;
	}

	public void setData(List<T> elem){
		this.data = elem;
	}

	protected abstract void convert(ViewHolder viewHolder, T item, int position);

}
