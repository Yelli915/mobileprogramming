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
    private java.util.Map<String, String> courseNameCache;

    public interface OnItemClickListener {
        void onItemClick(RunningRecord record);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(RunningRecord record);
    }

    public RunningRecordAdapter(List<RunningRecord> records, OnItemClickListener onItemClick) {
        this.records = records;
        this.onItemClick = onItemClick;
        this.courseNameCache = new java.util.HashMap<>();
    }

    public void setCourseNameCache(java.util.Map<String, String> cache) {
        this.courseNameCache = cache != null ? cache : new java.util.HashMap<>();
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
        TextView courseNameText;

        RecordViewHolder(View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.tv_record_date);
            distanceText = itemView.findViewById(R.id.tv_record_distance);
            runningTypeText = itemView.findViewById(R.id.tv_record_running_type);
            timeText = itemView.findViewById(R.id.tv_record_time);
            paceText = itemView.findViewById(R.id.tv_record_pace);
            courseNameText = itemView.findViewById(R.id.tv_record_course_name);
        }

        void bind(RunningRecord record, java.util.Map<String, String> courseNameCache) {
            // 날짜
            if (dateText != null) {
                dateText.setText(record.getDate());
            }
            
            // 거리 (라벨 제거, 숫자만)
            if (distanceText != null) {
                String distanceStr = record.getDistanceFormatted();
                // "X.XX km" 형식에서 숫자만 추출하거나 그대로 사용
                distanceText.setText(distanceStr);
            }
            
            // 러닝 타입 배지
            if (runningTypeText != null) {
                String type = record.getRunningType();
                if (type != null && !type.isEmpty()) {
                    runningTypeText.setText(type);
                    runningTypeText.setVisibility(View.VISIBLE);
                } else {
                    runningTypeText.setVisibility(View.GONE);
                }
            }
            
            // 시간 (라벨 제거, 숫자만)
            if (timeText != null) {
                String timeStr = record.getTimeFormatted();
                // "시간: XX:XX" 형식에서 "시간: " 제거
                if (timeStr != null && timeStr.startsWith("시간: ")) {
                    timeStr = timeStr.substring(4);
                }
                timeText.setText(timeStr);
            }
            
            // 페이스 (라벨 제거, 숫자만)
            if (paceText != null) {
                String paceStr = record.getPaceFormatted();
                // "평균 페이스: X:XX/km" 형식에서 "평균 페이스: " 제거
                if (paceStr != null && paceStr.startsWith("평균 페이스: ")) {
                    paceStr = paceStr.substring(7);
                }
                paceText.setText(paceStr);
            }
            
            // 코스 이름 (스케치 러닝인 경우)
            if (courseNameText != null) {
                String courseId = record.getCourseId();
                if (courseId != null && !courseId.isEmpty() && courseNameCache != null) {
                    String courseName = courseNameCache.get(courseId);
                    if (courseName != null && !courseName.isEmpty()) {
                        courseNameText.setText("📍 " + courseName);
                        courseNameText.setVisibility(View.VISIBLE);
                    } else {
                        courseNameText.setVisibility(View.GONE);
                    }
                } else {
                    courseNameText.setVisibility(View.GONE);
                }
            }
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
        holder.bind(record, courseNameCache);
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

