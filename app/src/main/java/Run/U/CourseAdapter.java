package Run.U;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class CourseAdapter extends RecyclerView.Adapter<CourseAdapter.CourseViewHolder> {

    private List<Course> courses;
    private OnCourseClickListener onCourseClick;

    public interface OnCourseClickListener {
        void onCourseClick(Course course);
    }

    public CourseAdapter(List<Course> courses, OnCourseClickListener onCourseClick) {
        this.courses = courses;
        this.onCourseClick = onCourseClick;
    }

    @NonNull
    @Override
    public CourseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_course, parent, false);
        return new CourseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CourseViewHolder holder, int position) {
        Course course = courses.get(position);
        holder.bind(course);
        holder.itemView.setOnClickListener(v -> {
            if (onCourseClick != null) {
                onCourseClick.onCourseClick(course);
            }
        });
    }

    @Override
    public int getItemCount() {
        return courses.size();
    }

    static class CourseViewHolder extends RecyclerView.ViewHolder {
        private TextView tvCourseName;
        private TextView tvCourseInfo;
        private TextView tvTotalDistance;
        private TextView tvEstimatedTime;

        CourseViewHolder(View itemView) {
            super(itemView);
            tvCourseName = itemView.findViewById(R.id.tvCourseName);
            tvCourseInfo = itemView.findViewById(R.id.tvCourseInfo);
            tvTotalDistance = itemView.findViewById(R.id.tvTotalDistance);
            tvEstimatedTime = itemView.findViewById(R.id.tvEstimatedTime);
        }

        void bind(Course course) {
            tvCourseName.setText(course.getName());
            tvCourseInfo.setText(course.getDistanceFormatted() + " | " + course.getDifficultyKorean());
            tvTotalDistance.setText("총 거리: " + course.getDistanceFormatted() + ",");
            tvEstimatedTime.setText("예상 소요: " + course.getEstimatedTimeFormatted());
        }
    }
}

