package Run.U;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class RunningRecordAdapter extends RecyclerView.Adapter<RunningRecordAdapter.RecordViewHolder> {

    private List<RunningRecord> records;
    private OnItemClickListener onItemClick;
    private OnItemLongClickListener onItemLongClick;

    public interface OnItemClickListener {
        void onItemClick(RunningRecord record);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(RunningRecord record);
    }

    public RunningRecordAdapter(List<RunningRecord> records, OnItemClickListener onItemClick) {
        this.records = records;
        this.onItemClick = onItemClick;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.onItemLongClick = listener;
    }

    static class RecordViewHolder extends RecyclerView.ViewHolder {
        TextView dateText;
        TextView distanceText;
        TextView runningTypeText;
        TextView timeText;
        TextView paceText;

        RecordViewHolder(View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.tv_record_date);
            distanceText = itemView.findViewById(R.id.tv_record_distance);
            runningTypeText = itemView.findViewById(R.id.tv_record_running_type);
            timeText = itemView.findViewById(R.id.tv_record_time);
            paceText = itemView.findViewById(R.id.tv_record_pace);
        }

        void bind(RunningRecord record) {
            dateText.setText(record.getDate());
            distanceText.setText(record.getDistanceFormatted() + " - " + record.getRunningType());
            runningTypeText.setText(record.getRunningType());
            timeText.setText("시간: " + record.getTimeFormatted());
            paceText.setText("평균 페이스: " + record.getPaceFormatted());
        }
    }

    @NonNull
    @Override
    public RecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_running_record, parent, false);
        return new RecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordViewHolder holder, int position) {
        RunningRecord record = records.get(position);
        holder.bind(record);
        holder.itemView.setOnClickListener(v -> {
            if (onItemClick != null) {
                onItemClick.onItemClick(record);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (onItemLongClick != null) {
                onItemLongClick.onItemLongClick(record);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return records.size();
    }
}

