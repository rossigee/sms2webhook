package org.golder.sms2webhook;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogAdapter extends ListAdapter<MainViewModel.LogEntry, LogAdapter.LogViewHolder> {
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final Context context;

    public LogAdapter(Context context) {
        super(new DiffUtil.ItemCallback<MainViewModel.LogEntry>() {
            @Override
            public boolean areItemsTheSame(@NonNull MainViewModel.LogEntry oldItem, @NonNull MainViewModel.LogEntry newItem) {
                return oldItem.timestamp == newItem.timestamp && oldItem.message.equals(newItem.message);
            }

            @Override
            public boolean areContentsTheSame(@NonNull MainViewModel.LogEntry oldItem, @NonNull MainViewModel.LogEntry newItem) {
                return oldItem.type.equals(newItem.type);
            }
        });
        this.context = context;
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log_entry, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        MainViewModel.LogEntry entry = getItem(position);
        holder.bind(entry);
    }

    public void submitLogs(List<MainViewModel.LogEntry> newLogs) {
        submitList(newLogs);
    }

    class LogViewHolder extends RecyclerView.ViewHolder {
        private final ImageView statusIcon;
        private final TextView logMessage;
        private final TextView logTimestamp;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            statusIcon = itemView.findViewById(R.id.statusIcon);
            logMessage = itemView.findViewById(R.id.logMessage);
            logTimestamp = itemView.findViewById(R.id.logTimestamp);
        }

        public void bind(MainViewModel.LogEntry entry) {
            logMessage.setText(entry.message);
            logTimestamp.setText(timeFormat.format(new Date(entry.timestamp)));

            // Set icon and color based on log type
            switch (entry.type) {
                case SUCCESS:
                    statusIcon.setImageResource(android.R.drawable.ic_dialog_info);
                    statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.success));
                    break;
                case WARNING:
                    statusIcon.setImageResource(android.R.drawable.ic_dialog_alert);
                    statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.warning));
                    break;
                case ERROR:
                    statusIcon.setImageResource(android.R.drawable.ic_dialog_alert);
                    statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.error));
                    break;
                case INFO:
                default:
                    statusIcon.setImageResource(android.R.drawable.ic_dialog_info);
                    statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.info));
                    break;
            }
        }
    }
}