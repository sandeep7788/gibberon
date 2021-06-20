package org.wBHARATmeet.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.devlomi.circularstatusview.CircularStatusView;

import org.wBHARATmeet.R;
import org.wBHARATmeet.model.constants.StatusType;
import org.wBHARATmeet.model.realms.Status;
import org.wBHARATmeet.model.realms.TextStatus;
import org.wBHARATmeet.model.realms.User;
import org.wBHARATmeet.model.realms.UserStatuses;
import org.wBHARATmeet.utils.RealmHelper;
import org.wBHARATmeet.utils.SharedPreferencesManager;
import org.wBHARATmeet.utils.TimeHelper;
import org.wBHARATmeet.views.TextViewWithShapeBackground;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;
import io.realm.OrderedRealmCollection;
import io.realm.RealmRecyclerViewAdapter;
import io.realm.RealmResults;

import static org.wBHARATmeet.utils.FontUtil.isFontExists;

public class HomePageStatusAdapter extends RealmRecyclerViewAdapter<UserStatuses, HomePageStatusAdapter.StatusHolder> {

    private List<UserStatuses> statusesList;
    private List<UserStatuses> originalList;
    private Context context;
    private OnClickListener onStatusClick;

    public HomePageStatusAdapter(@Nullable OrderedRealmCollection data, boolean autoUpdate, Context context, OnClickListener onStatusClick) {
        super(data, autoUpdate);
        statusesList = data;
        originalList = statusesList;
        this.context = context;
        this.onStatusClick = onStatusClick;
    }


    @NonNull
    @Override
    public StatusHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_status_circuler, parent, false);
        return new StatusHolder(row);
    }


    @Override
    public void onBindViewHolder(@NonNull StatusHolder holder, int position) {
        holder.bind(statusesList.get(holder.getAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return statusesList.size();
    }

    public void filter(String query) {

        Log.e("@@TAG", "filter: "+query);
        if (query.trim().isEmpty()) {
            statusesList = originalList;
        } else {
            RealmResults<UserStatuses> userStatuses = RealmHelper.getInstance().searchForStatus(query);
            statusesList = userStatuses;
        }

        notifyDataSetChanged();

    }

    class StatusHolder extends RecyclerView.ViewHolder {
        private CircleImageView profileImage;
        private TextView tvUsername;
        private CircularStatusView circularStatusView;
        private TextViewWithShapeBackground tvTextStatus;


        public StatusHolder(View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.profile_image);
            tvUsername = itemView.findViewById(R.id.tv_username);
            circularStatusView = itemView.findViewById(R.id.circular_status_view);
            tvTextStatus = itemView.findViewById(R.id.tv_text_status);
        }

        public void bind(final UserStatuses statuses) {
            User user = statuses.getUser();
            if (user == null)
                user = SharedPreferencesManager.getCurrentUser();


            tvUsername.setText(user.getProperUserName());
            RealmResults<Status> filteredStatuses = statuses.getFilteredStatuses();


            if (!filteredStatuses.isEmpty()) {
                Status lastStatus = filteredStatuses.last();
                circularStatusView.setPortionsCount(filteredStatuses.size());

                if (statuses.isAreAllSeen())
                    circularStatusView.setPortionsColor(context.getResources().getColor(R.color.status_seen_color));
                else
                    setCircularStatusColors(filteredStatuses);


                if (lastStatus.getType() == StatusType.TEXT) {
                    tvTextStatus.setVisibility(View.VISIBLE);
                    profileImage.setVisibility(View.GONE);
                    TextStatus textStatus = lastStatus.getTextStatus();
                    if (textStatus != null) {
                        tvTextStatus.setText(textStatus.getText());

                        try {
                            String color = textStatus.getBackgroundColor();
                            tvTextStatus.setShapeColor(Color.parseColor(color));
                        } catch (Exception e) {

                            tvTextStatus.setShapeColor(Color.BLACK);

                        }

                        setTypeFace(tvTextStatus, textStatus.getFontName());
                    }
                } else {
                    tvTextStatus.setVisibility(View.GONE);
                    profileImage.setVisibility(View.VISIBLE);
                    Glide.with(context).load(lastStatus.getThumbImg()).into(profileImage);
                    setInitialTextStatusValues();
                }
            } else {
                Glide.with(context).load(user.getThumbImg()).into(profileImage);
                setInitialTextStatusValues();
            }

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (onStatusClick != null)
                        onStatusClick.onStatusClick(view, statuses);
                }
            });
        }


        private void setCircularStatusColors(RealmResults<Status> filteredStatuses) {
            for (int i = 0; i < filteredStatuses.size(); i++) {
                Status status = filteredStatuses.get(i);
                int color = status.isSeen()
                        ? context.getResources().getColor(R.color.status_seen_color)
                        : context.getResources().getColor(R.color.status_not_seen_color);
                circularStatusView.setPortionColorForIndex(i, color);
            }
        }

        private void setInitialTextStatusValues() {
            tvTextStatus.setText("");
            tvTextStatus.setShapeColor(Color.BLACK);
        }
    }

    public interface OnClickListener {
        void onStatusClick(View view, UserStatuses userStatuses);
    }

    private void setTypeFace(TextView textView, String fontName) {
        if (isFontExists(fontName)) {
            textView.setTypeface(Typeface.createFromAsset(context.getAssets(), "fonts/" + fontName));
        }
    }


}
