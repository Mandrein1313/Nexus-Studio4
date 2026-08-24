package com.dev.ministudio;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.dev.ministudio.model.ProjectModel;
import java.io.File;

public class TabAdapter extends RecyclerView.Adapter<TabAdapter.TabViewHolder> {

    private ProjectModel projectModel;
    private OnTabInterface listener;

    public interface OnTabInterface {
        void onTabClick(File file);
        void onTabClose(File file, int position);
    }

    public TabAdapter(ProjectModel projectModel, OnTabInterface listener) {
        this.projectModel = projectModel;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TabViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tab, parent, false);
        return new TabViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TabViewHolder holder, int position) {
        File file = projectModel.getOpenedFiles().get(position);
        holder.tvTabName.setText(file.getName());

        boolean isActive = file.equals(projectModel.getCurrentOpenFile());

        if (isActive) {
            holder.itemView.setBackgroundResource(R.drawable.tab_active_bg);
            holder.tvTabName.setTextColor(Color.parseColor("#C0CAF5"));
            holder.tvTabName.setTypeface(null, android.graphics.Typeface.BOLD);
            holder.btnCloseTab.setColorFilter(Color.parseColor("#A9B1D6"));
        } else {
            holder.itemView.setBackgroundColor(Color.parseColor("#1F2335"));
            holder.tvTabName.setTextColor(Color.parseColor("#565F89"));
            holder.tvTabName.setTypeface(null, android.graphics.Typeface.NORMAL);
            holder.btnCloseTab.setColorFilter(Color.parseColor("#565F89"));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTabClick(file);
        });

        holder.btnCloseTab.setOnClickListener(v -> {
            if (listener != null) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onTabClose(file, pos);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return projectModel.getOpenedFiles() != null
                ? projectModel.getOpenedFiles().size()
                : 0;
    }

    public static class TabViewHolder extends RecyclerView.ViewHolder {
        TextView tvTabName;
        ImageButton btnCloseTab;

        public TabViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTabName = itemView.findViewById(R.id.tvTabName);
            btnCloseTab = itemView.findViewById(R.id.btnCloseTab);
        }
    }
}
