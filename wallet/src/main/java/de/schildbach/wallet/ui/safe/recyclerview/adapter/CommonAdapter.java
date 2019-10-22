package de.schildbach.wallet.ui.safe.recyclerview.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * 公用RecyclerView适配器
 *
 * @author zm
 * @param <T>
 */
public abstract class CommonAdapter<T> extends RecyclerView.Adapter<ViewHolder> {

    protected int layoutResId;

    protected final List<T> data;

    protected CommonAdapter(int layoutResId) {
        this(layoutResId, null);
    }

    protected CommonAdapter(int layoutResId, List<T> data) {
        this.data = data == null ? new ArrayList<T>() : data;
        this.layoutResId = layoutResId;
    }

    @Override
    public int getItemCount() {
        return data.size();
    }


    public T getItem(int position) {
        if (position >= data.size()) return null;
        return data.get(position);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(layoutResId, viewGroup, false);
        ViewHolder viewHolder = new ViewHolder(view);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, final int position) {
        final T item = getItem(position);
        convert(viewHolder, item, position);
        viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onItemClick(viewHolder.itemView, item, position);
            }
        });
    }

    protected abstract void convert(ViewHolder viewHolder, T item, int position);

    protected abstract void onItemClick(View view, T item, int position);

    public void add(T elem) {
        data.add(elem);
        notifyDataSetChanged();
    }

    public void addAll(List<T> elem) {
        data.addAll(elem);
        notifyItemRangeInserted(data.size(), elem.size());
    }

    public void set(T oldElem, T newElem) {
        set(data.indexOf(oldElem), newElem);
    }

    public void set(int index, T elem) {
        data.set(index, elem);
        notifyItemInserted(index);
    }

    public void remove(T elem) {
        data.remove(elem);
        notifyDataSetChanged();
    }

    public void remove(int index) {
        data.remove(index);
        notifyItemRemoved(index);
    }

    public void removeAll(List<T> elem) {
        data.removeAll(elem);
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

    public List<T> getData(){
        return data;
    }

}