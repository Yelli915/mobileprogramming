package Run.U;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.SlideViewHolder> {

    private static class Slide {
        int imageResId;
        String title;
        String description;

        Slide(int imageResId, String title, String description) {
            this.imageResId = imageResId;
            this.title = title;
            this.description = description;
        }
    }

    private final Slide[] slides = new Slide[]{
            new Slide(R.drawable.ic_onboarding_sketch, "스케치 러닝", "다양한 코스를 선택하고\n지도에서 경로를 미리 확인하세요"),
            new Slide(R.drawable.ic_onboarding_record, "기록을 한눈에", "운동 기록을 자동으로 저장하고\n통계로 확인하세요"),
            new Slide(R.drawable.ic_onboarding_share, "코스 탐색", "난이도별로 코스를 탐색하고\n나에게 맞는 코스를 찾아보세요")
    };

    @NonNull
    @Override
    public SlideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding_slide, parent, false);
        return new SlideViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlideViewHolder holder, int position) {
        Slide slide = slides[position];
        holder.imageView.setImageResource(slide.imageResId);
        holder.titleView.setText(slide.title);
        holder.descriptionView.setText(slide.description);
    }

    @Override
    public int getItemCount() {
        return slides.length;
    }

    static class SlideViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView titleView;
        TextView descriptionView;

        SlideViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.slide_image);
            titleView = itemView.findViewById(R.id.slide_title);
            descriptionView = itemView.findViewById(R.id.slide_description);
        }
    }
}

