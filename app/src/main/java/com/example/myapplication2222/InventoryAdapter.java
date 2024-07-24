package com.example.myapplication2222;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.List;

public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.InventoryViewHolder> {

    private final List<InventoryItem> inventoryList;

    public InventoryAdapter(List<InventoryItem> inventoryList) {
        this.inventoryList = inventoryList;
    }

    @NonNull
    @Override
    public InventoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_inventory, parent, false);
        return new InventoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull InventoryViewHolder holder, int position) {
        InventoryItem item = inventoryList.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return inventoryList.size();
    }

    public void updateInventoryList(List<InventoryItem> newList) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new InventoryDiffCallback(inventoryList, newList));
        inventoryList.clear();
        inventoryList.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    static class InventoryViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameTextView;
        private final TextView priceTextView;
        private final TextView stockTextView;

        public InventoryViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.nameTextView);
            priceTextView = itemView.findViewById(R.id.priceTextView);
            stockTextView = itemView.findViewById(R.id.stockTextView);

            itemView.setOnClickListener(v -> {
                String stockText = stockTextView.getText().toString();
                if (stockText.contains("품절")) {
                    showSnackbar(v, "품절", R.color.color_white, R.color.color_red);
                } else {
                    showSnackbar(v, "재고 " + stockText + " 이하", R.color.color_white, R.color.color_on_secondary);
                }
            });
        }

        private void showSnackbar(View view, String message, int textColor, int backgroundColor) {
            Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_SHORT);
            snackbar.setTextColor(view.getResources().getColor(textColor));
            snackbar.setBackgroundTint(view.getResources().getColor(backgroundColor));
            snackbar.show();
        }

        public void bind(InventoryItem item) {
            nameTextView.setText(item.getName());
            priceTextView.setText(String.format("%d원", item.getPrice())); // 가격에 단위 추가
            int stock = item.getStock();
            if (stock > 0) {
                stockTextView.setText(String.format("%d개", stock)); // 재고에 단위 추가
                stockTextView.setTextColor(itemView.getResources().getColor(android.R.color.black)); // 기본 텍스트 색상
            } else {
                stockTextView.setText("품절");
                stockTextView.setTextColor(itemView.getResources().getColor(android.R.color.holo_red_dark)); // 빨간색으로 표시
            }
        }
    }

    static class InventoryDiffCallback extends DiffUtil.Callback {

        private final List<InventoryItem> oldList;
        private final List<InventoryItem> newList;

        public InventoryDiffCallback(List<InventoryItem> oldList, List<InventoryItem> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            InventoryItem oldItem = oldList.get(oldItemPosition);
            InventoryItem newItem = newList.get(newItemPosition);

            // null 체크 추가
            if (oldItem == null || newItem == null) {
                return oldItem == newItem;
            }

            String oldId = oldItem.getId();
            String newId = newItem.getId();

            // null 체크 후 equals 호출
            return oldId != null && oldId.equals(newId);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            InventoryItem oldItem = oldList.get(oldItemPosition);
            InventoryItem newItem = newList.get(newItemPosition);

            // null 체크 후 equals 호출
            return oldItem != null && oldItem.equals(newItem);
        }
    }
}
