package com.example.decentspec_v3;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.decentspec_v3.database.SampleFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.example.decentspec_v3.database.SampleFile.STAGE_RECEIVED;
import static com.example.decentspec_v3.database.SampleFile.STAGE_RECEIVING;
import static com.example.decentspec_v3.database.SampleFile.STAGE_TRAINED;
import static com.example.decentspec_v3.database.SampleFile.STAGE_TRAINING;

// with reference to https://medium.com/@atifmukhtar/recycler-view-with-mvvm-livedata-a1fd062d2280

public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    Activity context;
    List<SampleFile> fileArrayList;

    public RecyclerViewAdapter(Activity context, List<SampleFile> fileArrayList) {
        this.context = context;
//        this.fileArrayList = fileArrayList.subList(0, fileArrayList.size());
//        Collections.reverse(this.fileArrayList);
        this.fileArrayList = fileArrayList;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View rootView = LayoutInflater.from(context).inflate(R.layout.database_cell, parent, false);
        return new RecyclerViewViewHolder(rootView);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {

        SampleFile file = fileArrayList.get(position);
        RecyclerViewViewHolder viewHolder= (RecyclerViewViewHolder) holder;

        viewHolder.nameText.setText(file.fileName);
        viewHolder.stageText.setText(stage2String(file.stage));
    }

    @Override
    public int getItemCount() {
        return fileArrayList.size();
    }

    class RecyclerViewViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        TextView stageText;

        public RecyclerViewViewHolder(View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.col1);
            stageText = itemView.findViewById(R.id.col2);
        }
    }

    // utility methods
    private String stage2String(int stage) {
        switch (stage) {
            case STAGE_RECEIVING: return "Receiving ...";
            case STAGE_RECEIVED: return "Received";
            case STAGE_TRAINING: return "Training ...";
            case STAGE_TRAINED: return "Trained";
            default: return "unknown: " + stage;
        }
    }
}