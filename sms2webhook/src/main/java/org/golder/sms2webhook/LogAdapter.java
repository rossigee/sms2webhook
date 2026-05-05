package org.golder.sms2webhook;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {
    private List<MainViewModel.LogEntry> logs = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final Context context;

    public LogAdapter(Context context) {
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
        MainViewModel.LogEntry entry = logs.get(position);
        holder.bind(entry);
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    public void updateLogs(List<MainViewModel.LogEntry> newLogs) {
        int prevSize = this.logs.size();
        this.logs = new ArrayList<>(newLogs);
        int newSize = this.logs.size();
        if (newSize > prevSize) {
            notifyItemRangeInserted(0, newSize - prevSize);
        } else {
            notifyDataSetChanged();
        }
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