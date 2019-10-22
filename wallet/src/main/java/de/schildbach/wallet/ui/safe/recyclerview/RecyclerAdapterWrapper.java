package de.schildbach.wallet.ui.safe.recyclerview;

import android.support.v4.util.SparseArrayCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.View;
import android.view.ViewGroup;
import de.schildbach.wallet.ui.safe.recyclerview.adapter.CommonAdapter;
import de.schildbach.wallet.ui.safe.recyclerview.adapter.ViewHolder;


/**
 * 公用RecyclerView适配器
 * @author zm
 * @param
 */
public class RecyclerAdapterWrapper extends RecyclerView.Adapter<ViewHolder> {

    private static final int TYPE_HEADER_VIEW = Integer.MIN_VALUE;
    private static final int TYPE_FOOTER_VIEW = Integer.MIN_VALUE + 1;

    private SparseArrayCompat<View> mHeaderViews = new SparseArrayCompat<>();
    private SparseArrayCompat<View> mFooterViews = new SparseArrayCompat<>();

    private CommonAdapter adapter;

    public RecyclerAdapterWrapper() {
    }

    public RecyclerAdapterWrapper(CommonAdapter innerAdapter) {
        setAdapter(innerAdapter);
    }

    public void setAdapter(CommonAdapter adapter) {
        if (this.adapter != null) {
            notifyItemRangeRemoved(getHeaderViewsCount(), adapter.getItemCount());
            adapter.unregisterAdapterDataObserver(mDataObserver);
        }

        this.adapter = adapter;
        adapter.registerAdapterDataObserver(mDataObserver);
        notifyItemRangeInserted(getHeaderViewsCount(), adapter.getItemCount());
    }

    public CommonAdapter getAdapter() {
        return adapter;
    }

    public void addHeaderView(View header) {
        if (header == null) {
           return;
        }
        //getItemViewType避免与真实的适配器里面getItemViewType冲突
        mHeaderViews.put(mHeaderViews.size() + TYPE_HEADER_VIEW, header);
        this.notifyDataSetChanged();
    }

    public void addFooterView(View footer) {
        if (footer == null) {
            return;
        }
        //getItemViewType避免与真实的适配器里面getItemViewType冲突
        mFooterViews.put(mHeaderViews.size() + TYPE_FOOTER_VIEW, footer);
        this.notifyDataSetChanged();
    }

    public void removeHeaderView(View view) {
        int index = 0;
        for (int i = 0; i<mHeaderViews.size(); i++){
            if(mHeaderViews.get(i) == view){
                index = i;
                break;
            }
        }
        mHeaderViews.remove(index);
        this.notifyDataSetChanged();
    }

    public void removeFooterView(View view) {
        int index = 0;
        for (int i = 0; i<mFooterViews.size(); i++){
            if(mFooterViews.get(i) == view){
                index = i;
                break;
            }
        }
        mFooterViews.removeAt(index);
        this.notifyDataSetChanged();
    }

    public int getHeaderViewsCount() {
        return mHeaderViews.size();
    }

    public int getFooterViewsCount() {
        return mFooterViews.size();
    }

    public boolean isHeaderViewPos(int position) {
        return position < getHeaderViewsCount();
    }

    public boolean isFooterViewPos(int position) {
        return position >= getHeaderViewsCount() + adapter.getItemCount();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (mHeaderViews.get(viewType) != null) {
            return new ViewHolder(mHeaderViews.get(viewType));
        } else if (mFooterViews.get(viewType) != null) {
            return new ViewHolder(mFooterViews.get(viewType));
        } else {
            return adapter.onCreateViewHolder(parent, viewType);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (isHeaderViewPos(position)){
            return;
        }
        if (isFooterViewPos(position)){
            return;
        }
        adapter.onBindViewHolder(holder, position - getHeaderViewsCount());
    }

    @Override
    public int getItemCount() {
        return getHeaderViewsCount() + getFooterViewsCount() + adapter.getItemCount();
    }

    @Override
    public int getItemViewType(int position) {
        if (isHeaderViewPos(position)) {
            return mHeaderViews.keyAt(position);
        } else if (isFooterViewPos(position)) {
            return mFooterViews.keyAt(position - getHeaderViewsCount() - adapter.getItemCount());
        }
        return adapter.getItemViewType(position - getHeaderViewsCount());
    }

    private RecyclerView.AdapterDataObserver mDataObserver = new RecyclerView.AdapterDataObserver() {

        @Override
        public void onChanged() {
            super.onChanged();
            notifyDataSetChanged();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            super.onItemRangeChanged(positionStart, itemCount);
            notifyItemRangeChanged(positionStart + getHeaderViewsCount(), itemCount);
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            super.onItemRangeInserted(positionStart, itemCount);
            notifyItemRangeInserted(positionStart + getHeaderViewsCount(), itemCount);
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            super.onItemRangeRemoved(positionStart, itemCount);
            notifyItemRangeRemoved(positionStart + getHeaderViewsCount(), itemCount);
        }

        @Override
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            super.onItemRangeMoved(fromPosition, toPosition, itemCount);
            int headerViewsCountCount = getHeaderViewsCount();
            notifyItemRangeChanged(fromPosition + headerViewsCountCount, toPosition + headerViewsCountCount + itemCount);
        }
    };

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) { //处理GridLayoutManager情况
        adapter.onAttachedToRecyclerView(recyclerView);
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager)
        {
            final GridLayoutManager gridLayoutManager = (GridLayoutManager) layoutManager;
            final GridLayoutManager.SpanSizeLookup spanSizeLookup = gridLayoutManager.getSpanSizeLookup();
            gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup()
            {
                @Override
                public int getSpanSize(int position)
                {
                    int viewType = getItemViewType(position);
                    if (mHeaderViews.get(viewType) != null) {
                        return gridLayoutManager.getSpanCount();
                    } else if (mFooterViews.get(viewType) != null) {
                        return gridLayoutManager.getSpanCount();
                    } else{
                        return spanSizeLookup.getSpanSize(position);
                    }
                }
            });
            gridLayoutManager.setSpanCount(gridLayoutManager.getSpanCount());
        }
    }

    @Override
    public void onViewAttachedToWindow(ViewHolder holder) {  //处理StaggeredGridLayoutManager情况
        adapter.onViewAttachedToWindow(holder);
        int position = holder.getLayoutPosition();
        if (isHeaderViewPos(position) || isFooterViewPos(position))
        {
            ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
            if (lp != null && lp instanceof StaggeredGridLayoutManager.LayoutParams) {
                StaggeredGridLayoutManager.LayoutParams p =
                        (StaggeredGridLayoutManager.LayoutParams) lp;
                p.setFullSpan(true);
            }
        }
    }
}
