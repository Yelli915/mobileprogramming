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

    public void updateRecords(List<RunningRecord> newRecords) {
        this.records.clear();
        if (newRecords != null) {
            this.records.addAll(newRecords);
        }
        notifyDataSetChanged();
    }

    static class RecordViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        TextView dateText;
        TextView distanceText;
        TextView runningTypeText;
        TextView timeText;
        TextView paceText;
        TextView courseNameText;
        TextView difficultyText;

        RecordViewHolder(View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.tv_record_name);
            dateText = itemView.findViewById(R.id.tv_record_date);
            distanceText = itemView.findViewById(R.id.tv_record_distance);
            runningTypeText = itemView.findViewById(R.id.tv_record_running_type);
            timeText = itemView.findViewById(R.id.tv_record_time);
            paceText = itemView.findViewById(R.id.tv_record_pace);
            courseNameText = itemView.findViewById(R.id.tv_record_course_name);
            difficultyText = itemView.findViewById(R.id.tv_record_difficulty);
        }

        void bind(RunningRecord record, java.util.Map<String, String> courseNameCache) {
            // 기록 이름
            if (nameText != null) {
                String name = record.getName();
                if (name != null && !name.trim().isEmpty()) {
                    nameText.setText(name);
                    nameText.setVisibility(View.VISIBLE);
                } else {
                    nameText.setVisibility(View.GONE);
                }
            }
            
            // 날짜
            if (dateText != null) {
                String date = record.getDate();
                if (date == null || date.trim().isEmpty()) {
                    // createdAt이 있으면 날짜 생성
                    if (record.getCreatedAt() > 0) {
                        java.util.Date dateObj = new java.util.Date(record.getCreatedAt());
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy년 MM월 dd일", java.util.Locale.KOREA);
                        date = sdf.format(dateObj);
                    } else {
                        date = "날짜 없음";
                    }
                }
                dateText.setText(date != null ? date : "날짜 없음");
                dateText.setTextColor(0xFF000000); // 검은색
                dateText.setVisibility(View.VISIBLE);
                dateText.invalidate();
            }
            
            // 거리 (라벨 제거, 숫자만)
            if (distanceText != null) {
                String distanceStr = null;
                // totalDistanceKm를 우선 확인
                if (record.getTotalDistanceKm() > 0) {
                    distanceStr = GoogleSignInUtils.formatDistanceKm(record.getTotalDistanceKm());
                } else {
                    // totalDistanceKm가 없으면 getDistanceFormatted() 사용
                    distanceStr = record.getDistanceFormatted();
                }
                if (distanceStr == null || distanceStr.trim().isEmpty()) {
                    distanceStr = "0.0km";
                }
                distanceText.setText(distanceStr);
                distanceText.setVisibility(View.VISIBLE);
                distanceText.invalidate();
            }
            
            // 난이도 배지
            if (difficultyText != null) {
                String difficulty = record.getDifficulty();
                if (difficulty != null && !difficulty.isEmpty()) {
                    String difficultyDisplay = record.getDifficultyDisplayName();
                    if (difficultyDisplay != null && !difficultyDisplay.isEmpty()) {
                        difficultyText.setText(difficultyDisplay);
                        difficultyText.setVisibility(View.VISIBLE);
                        difficultyText.invalidate();
                    } else {
                        difficultyText.setVisibility(View.GONE);
                    }
                } else {
                    difficultyText.setVisibility(View.GONE);
                }
            }
            
            // 러닝 타입 배지
            if (runningTypeText != null) {
                String type = record.getRunningType();
                if (type != null && !type.isEmpty()) {
                    runningTypeText.setText(type);
                    runningTypeText.setVisibility(View.VISIBLE);
                } else {
                    runningTypeText.setText("일반 운동");
                    runningTypeText.setVisibility(View.VISIBLE);
                }
            }
            
            // 시간 (라벨 제거, 숫자만)
            if (timeText != null) {
                String timeStr = null;
                // elapsedTimeMs를 우선 확인하여 직접 계산
                if (record.getElapsedTimeMs() > 0) {
                    timeStr = GoogleSignInUtils.formatElapsedTimeShort(record.getElapsedTimeMs());
                } else {
                    // elapsedTimeMs가 없으면 getTimeFormatted() 사용
                    timeStr = record.getTimeFormatted();
                    if (timeStr == null || timeStr.trim().isEmpty() || timeStr.equals("--:--")) {
                        timeStr = "--:--";
                    }
                }
                // "시간: XX:XX" 형식에서 "시간: " 제거
                if (timeStr != null && timeStr.startsWith("시간: ")) {
                    timeStr = timeStr.substring(4);
                }
                timeText.setText(timeStr != null ? timeStr : "--:--");
                timeText.setTextColor(0xFF000000); // 검은색
                timeText.setVisibility(View.VISIBLE);
                timeText.invalidate();
            }
            
            // 페이스 (라벨 제거, 숫자만)
            if (paceText != null) {
                String paceStr = null;
                // elapsedTimeMs와 totalDistanceKm을 우선 확인하여 직접 계산
                if (record.getElapsedTimeMs() > 0 && record.getTotalDistanceKm() > 0) {
                    double totalTimeSeconds = record.getElapsedTimeMs() / 1000.0;
                    double paceSeconds = totalTimeSeconds / record.getTotalDistanceKm();
                    paceStr = GoogleSignInUtils.formatPaceFromSeconds(paceSeconds);
                } else if (record.getAveragePace() != null && !record.getAveragePace().trim().isEmpty()) {
                    // averagePace 필드가 있으면 사용
                    paceStr = record.getAveragePace();
                } else {
                    // 계산할 수 없으면 getPaceFormatted() 사용
                    paceStr = record.getPaceFormatted();
                    if (paceStr == null || paceStr.trim().isEmpty() || paceStr.equals("--:--/km")) {
                        paceStr = "--:--/km";
                    }
                }
                // "평균 페이스: X:XX/km" 형식에서 "평균 페이스: " 제거
                if (paceStr != null && paceStr.startsWith("평균 페이스: ")) {
                    paceStr = paceStr.substring(7);
                }
                paceText.setText(paceStr != null ? paceStr : "--:--/km");
                paceText.setTextColor(0xFF000000); // 검은색
                paceText.setVisibility(View.VISIBLE);
                paceText.invalidate();
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
        if (position < 0 || position >= records.size()) {
            return;
        }
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

