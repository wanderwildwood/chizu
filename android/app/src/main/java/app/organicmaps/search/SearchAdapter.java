package app.organicmaps.search;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import java.util.List;
import java.util.Collections;
import com.wanderwildwood.chizu.ContactPlaces;
import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.R;
import app.organicmaps.sdk.search.SearchResult;
import app.organicmaps.util.Graphics;
import app.organicmaps.util.ThemeUtils;
import app.organicmaps.util.UiUtils;
import com.google.android.material.textview.MaterialTextView;

class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.SearchDataViewHolder>
{
  private static final int TYPE_CONTACT = 100;
  @NonNull
  private List<ContactPlaces.Place> mContacts = Collections.emptyList();

  private static final int SHORT_HORIZON_CLOSE_MIN = 60;
  private static final int SHORT_HORIZON_OPEN_MIN = 15;

  private final SearchFragment mSearchFragment;
  @Nullable
  private SearchResult[] mResults;

  static abstract class SearchDataViewHolder extends RecyclerView.ViewHolder
  {
    SearchDataViewHolder(@NonNull View itemView)
    {
      super(itemView);
    }

    abstract void bind(@NonNull SearchResult result, int position);
  }

  private static abstract class BaseResultViewHolder extends SearchDataViewHolder
  {
    SearchResult mResult;
    // Position within search results
    int mOrder;

    BaseResultViewHolder(@NonNull View view)
    {
      super(view);
      if (view instanceof MaterialTextView)
      {
        int tintAttr = getTintAttr();
        if (tintAttr != 0)
          Graphics.tint((MaterialTextView) view, tintAttr);
      }
      view.setOnClickListener(v -> processClick(mResult, mOrder));
    }

    @Override
    void bind(@NonNull SearchResult result, int order)
    {
      mResult = result;
      mOrder = order;
      final MaterialTextView titleView = getTitleView();

      if (titleView != null)
        titleView.setText(mResult.getFormattedTitle(titleView.getContext()));
    }

    @AttrRes
    int getTintAttr()
    {
      return androidx.appcompat.R.attr.colorAccent;
    }

    abstract MaterialTextView getTitleView();

    abstract void processClick(SearchResult result, int order);
  }

  private class SuggestViewHolder extends BaseResultViewHolder
  {
    SuggestViewHolder(@NonNull View view)
    {
      super(view);
    }

    @Override
    MaterialTextView getTitleView()
    {
      return (MaterialTextView) itemView;
    }

    @Override
    void processClick(SearchResult result, int order)
    {
      mSearchFragment.setQuery(result.suggestion, result.type == SearchResult.TYPE_PURE_SUGGEST);
    }
  }

  /** Atlas: someone from the contacts whose name is being typed; a tap searches for their address. */
  private class ContactViewHolder extends SearchDataViewHolder
  {
    ContactViewHolder(@NonNull View view)
    {
      super(view);
      final MaterialTextView text = (MaterialTextView) view;
      text.setSingleLine(false);
      // Two or three lines: the row grows with them instead of keeping the one-line height.
      final int gap = view.getResources().getDimensionPixelSize(R.dimen.margin_half);
      text.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
      text.setMinimumHeight(view.getResources().getDimensionPixelSize(R.dimen.search_item_height));
      text.setPaddingRelative(text.getPaddingStart(), gap, text.getPaddingEnd(), gap);
      text.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_contacts, 0, 0, 0);
      Graphics.tint(text, androidx.appcompat.R.attr.colorAccent);
    }

    @Override
    void bind(@NonNull SearchResult result, int position)
    {}

    void bind(@NonNull ContactPlaces.Place place)
    {
      final SpannableString lines = new SpannableString(place.name + "\n" + place.address);
      lines.setSpan(new StyleSpan(Typeface.BOLD), 0, place.name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      ((MaterialTextView) itemView).setText(lines);
      itemView.setOnClickListener(v -> mSearchFragment.setQuery(place.address, false));
    }
  }

  private class ResultViewHolder extends BaseResultViewHolder
  {
    @NonNull
    final View mFrame;
    @NonNull
    final MaterialTextView mName;
    @NonNull
    final MaterialTextView mOpen;
    @NonNull
    final MaterialTextView mDescription;
    @NonNull
    final MaterialTextView mRegion;
    @NonNull
    final MaterialTextView mDistance;

    @Override
    int getTintAttr()
    {
      return 0;
    }

    ResultViewHolder(@NonNull View view)
    {
      super(view);
      mFrame = view;
      mName = view.findViewById(R.id.title);
      mOpen = view.findViewById(R.id.open);
      mDescription = view.findViewById(R.id.description);
      mRegion = view.findViewById(R.id.region);
      mDistance = view.findViewById(R.id.distance);
    }

    @Override
    MaterialTextView getTitleView()
    {
      return mName;
    }

    @Override
    void bind(@NonNull SearchResult result, int order)
    {
      super.bind(result, order);
      setBackground();

      formatOpeningHours(mResult);
      UiUtils.setTextAndHideIfEmpty(mDescription, mResult.description.description);
      mRegion.setText(mResult.getFormattedAddress(mRegion.getContext()));
      UiUtils.setTextAndHideIfEmpty(mDistance, mResult.description.distance.toString(mFrame.getContext()));
    }

    private void formatOpeningHours(SearchResult result)
    {
      final Resources resources = mSearchFragment.getResources();

      if (result.description.openNow != SearchResult.OPEN_NOW_YES
          && result.description.openNow != SearchResult.OPEN_NOW_NO)
      {
        // Hide if unknown opening hours state
        UiUtils.hide(mOpen);
        return;
      }

      final boolean isOpen = result.description.openNow == SearchResult.OPEN_NOW_YES;
      final int minsToNextState = isOpen ? result.description.minutesUntilClosed : result.description.minutesUntilOpen;

      final boolean shortHorizonClosing = isOpen && minsToNextState >= 0 && minsToNextState <= SHORT_HORIZON_CLOSE_MIN;
      final boolean shortHorizonOpening = !isOpen && minsToNextState >= 0 && minsToNextState <= SHORT_HORIZON_OPEN_MIN;

      if (shortHorizonClosing || shortHorizonOpening)
      {
        final String minsToChangeStr = resources.getQuantityString(
            R.plurals.minutes_short, Math.max(minsToNextState, 1), Math.max(minsToNextState, 1));
        final String nextChangeFormatted =
            resources.getString(isOpen ? R.string.closes_in : R.string.opens_in, minsToChangeStr);

        UiUtils.setTextAndShow(mOpen, nextChangeFormatted);
        mOpen.setTextColor(ContextCompat.getColor(mSearchFragment.getContext(), R.color.base_yellow));
      }
      else
      {
        UiUtils.setTextAndShow(
            mOpen, isOpen ? resources.getString(R.string.editor_time_open) : resources.getString(R.string.closed));
        mOpen.setTextColor(
            ContextCompat.getColor(mSearchFragment.getContext(), isOpen ? R.color.base_green : R.color.base_red));
      }
    }

    private void setBackground()
    {
      final Context context = mSearchFragment.requireActivity();
      final int itemBg = ThemeUtils.getResource(context, R.attr.clickableBackground);
      mFrame.setBackgroundResource(itemBg);
    }

    @Override
    void processClick(SearchResult result, int order)
    {
      mSearchFragment.showSingleResultOnMap(result, order);
    }
  }

  SearchAdapter(SearchFragment fragment)
  {
    mSearchFragment = fragment;
  }

  @NonNull
  @Override
  public SearchDataViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
  {
    final LayoutInflater inflater = LayoutInflater.from(parent.getContext());

    return switch (viewType)
    {
      case SearchResult.TYPE_SUGGEST, SearchResult.TYPE_PURE_SUGGEST ->
        new SuggestViewHolder(inflater.inflate(R.layout.item_search_suggest, parent, false));
      case TYPE_CONTACT ->
        new ContactViewHolder(inflater.inflate(R.layout.item_search_suggest, parent, false));
      case SearchResult.TYPE_RESULT ->
        new ResultViewHolder(inflater.inflate(R.layout.item_search_result, parent, false));
      default -> throw new IllegalArgumentException("Unhandled view type given");
    };
  }

  @Override
  public void onBindViewHolder(@NonNull SearchDataViewHolder holder, int position)
  {
    // Contacts stand above the results; a result keeps its own number, which the map goes by.
    final int contacts = mContacts.size();
    if (position < contacts)
      ((ContactViewHolder) holder).bind(mContacts.get(position));
    else
      holder.bind(mResults[position - contacts], position - contacts);
  }

  @Override
  public int getItemViewType(int position)
  {
    final int contacts = mContacts.size();
    return position < contacts ? TYPE_CONTACT : mResults[position - contacts].type;
  }

  @Override
  public long getItemId(int position)
  {
    return position;
  }

  @Override
  public int getItemCount()
  {
    int res = mContacts.size();
    if (mResults == null)
      return res;

    res += mResults.length;
    return res;
  }

  public void clear()
  {
    refreshData(null);
  }

  /** Atlas: the people to offer above the results (see ContactPlaces). */
  void setContacts(@NonNull List<ContactPlaces.Place> contacts)
  {
    if (mContacts.isEmpty() && contacts.isEmpty())
      return;
    mContacts = contacts;
    notifyDataSetChanged();
  }

  void refreshData(@Nullable SearchResult[] results)
  {
    mResults = results;
    notifyDataSetChanged();
  }
}
