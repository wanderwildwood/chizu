package app.organicmaps.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.search.SearchRecents;
import app.organicmaps.util.Graphics;
import app.organicmaps.widget.SearchToolbarController;
import com.google.android.material.textview.MaterialTextView;

class SearchHistoryAdapter extends RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder>
{
  private static final int TYPE_ITEM = 0;
  private static final int TYPE_CLEAR = 1;
  private static final int TYPE_MY_POSITION = 2;
  // Atlas: the places last driven to come first, above the searches.
  private static final int TYPE_PLACE = 3;
  @NonNull
  private java.util.List<com.wanderwildwood.chizu.GoPlaces.Place> mPlaces;

  @NonNull
  private final SearchToolbarController mSearchToolbarController;
  private final boolean mShowMyPosition;

  public static class ViewHolder extends RecyclerView.ViewHolder
  {
    private final MaterialTextView mText;

    public ViewHolder(View itemView)
    {
      super(itemView);
      mText = (MaterialTextView) itemView;
      Graphics.tint(mText);
    }
  }

  public SearchHistoryAdapter(@NonNull SearchToolbarController searchToolbarController, boolean showMyPosition,
                              @NonNull android.content.Context context)
  {
    SearchRecents.refresh();
    mPlaces = com.wanderwildwood.chizu.GoPlaces.recent(context);
    mSearchToolbarController = searchToolbarController;
    mShowMyPosition = showMyPosition;
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int type)
  {
    final ViewHolder res;

    switch (type)
    {
    case TYPE_ITEM:
      res = new ViewHolder(
          LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_search_recent, viewGroup, false));
      res.mText.setOnClickListener(v -> mSearchToolbarController.setQuery(res.mText.getText()));
      break;

    case TYPE_CLEAR:
      res = new ViewHolder(
          LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_search_clear_history, viewGroup, false));
      res.mText.setOnClickListener(v -> {
        SearchRecents.clear();
        com.wanderwildwood.chizu.GoPlaces.clearRecent(v.getContext());
        mPlaces = java.util.Collections.emptyList();
        notifyDataSetChanged();
      });
      break;

    case TYPE_PLACE:
      res = new ViewHolder(
          LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_search_recent_place, viewGroup, false));
      res.mText.setOnClickListener(v -> {
        final int at = res.getBindingAdapterPosition() - (mShowMyPosition ? 1 : 0);
        if (at < 0 || at >= mPlaces.size())
          return;
        final com.wanderwildwood.chizu.GoPlaces.Place place = mPlaces.get(at);
        if (RoutingController.get().isWaitingPoiPick())
        {
          // A point of the route being planned was asked for.
          RoutingController.get().onPoiSelected(place.toMapObject());
          mSearchToolbarController.onUpClick();
        }
        else
          com.wanderwildwood.chizu.GoPlaces.go(v.getContext(), place);
      });
      break;

    case TYPE_MY_POSITION:
      res = new ViewHolder(
          LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_search_my_position, viewGroup, false));
      res.mText.setOnClickListener(v -> {
        RoutingController.get().onPoiSelected(
            MwmApplication.from(viewGroup.getContext()).getLocationHelper().getMyPosition());
        mSearchToolbarController.onUpClick();
      });
      break;

    default: throw new IllegalArgumentException("Unsupported ViewHolder type given");
    }

    Graphics.tint(res.mText);
    return res;
  }

  @Override
  public void onBindViewHolder(ViewHolder viewHolder, int position)
  {
    final int type = getItemViewType(position);
    if (mShowMyPosition)
      position--;
    if (type == TYPE_PLACE)
      viewHolder.mText.setText(
          com.wanderwildwood.chizu.ui.RecentPlaceRow.text(viewHolder.mText.getContext(), mPlaces.get(position)));
    else if (type == TYPE_ITEM)
      viewHolder.mText.setText(SearchRecents.get(position - mPlaces.size()));
  }

  @Override
  public int getItemCount()
  {
    int res = mPlaces.size() + SearchRecents.getSize();
    if (res > 0)
      res++;

    if (mShowMyPosition)
      res++;

    return res;
  }

  @Override
  public int getItemViewType(int position)
  {
    if (mShowMyPosition)
    {
      if (position == 0)
        return TYPE_MY_POSITION;

      position--;
    }

    if (position < mPlaces.size())
      return TYPE_PLACE;
    return (position - mPlaces.size() < SearchRecents.getSize() ? TYPE_ITEM : TYPE_CLEAR);
  }
}
