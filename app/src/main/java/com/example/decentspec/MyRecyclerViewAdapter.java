package com.example.decentspec;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

// copied from https://stackoverflow.com/questions/40584424/simple-android-recyclerview-example
public class MyRecyclerViewAdapter extends RecyclerView.Adapter<MyRecyclerViewAdapter.ViewHolder> {

    private List<String> mData;
    private LayoutInflater mInflater;
    private int selectedPos = RecyclerView.NO_POSITION; // for select highlight

    // data is passed into the constructor
    MyRecyclerViewAdapter(Context context, List<String> data) {
        this.mInflater = LayoutInflater.from(context);
        this.mData = data;
    }

    public void rstSelect() {
        selectedPos = RecyclerView.NO_POSITION;
    }
    public int getSelect() {
        return selectedPos;
    }

    // inflates the row layout from xml when needed
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.miner_list_cell, parent, false);
        return new ViewHolder(view);
    }

    // binds the data to the TextView in each row
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        String content = mData.get(position);
        holder.myTextView.setText(content);
        holder.itemView.setSelected(selectedPos == position); // for select highlight
    }

    // total number of rows
    @Override
    public int getItemCount() {
        return mData.size();
    }

    // stores and recycles views as they are scrolled off screen
    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView myTextView;

        ViewHolder(View itemView) {
            super(itemView);
            myTextView = itemView.findViewById(R.id.singleCell);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            notifyItemChanged(selectedPos);
            int next = getAdapterPosition();
            if (selectedPos == next) {
                selectedPos = RecyclerView.NO_POSITION;
            } else {
                selectedPos = next;
            }
            notifyItemChanged(selectedPos); // for select highlight
        }
    }

    // convenience method for getting data at click position
    String getItem(int id) {
        return mData.get(id);
    }

}