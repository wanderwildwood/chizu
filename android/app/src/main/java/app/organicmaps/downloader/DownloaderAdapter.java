package app.organicmaps.downloader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.location.Location;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.MwmActivity;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.sdk.downloader.CountryItem;
import app.organicmaps.sdk.downloader.MapManager;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.util.StringUtils;
import app.organicmaps.util.UiUtils;
import app.organicmaps.util.bottomsheet.MenuBottomSheetFragment;
import app.organicmaps.util.bottomsheet.MenuBottomSheetItem;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;
import com.wanderwildwood.chizu.Continents;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class DownloaderAdapter extends RecyclerView.Adapter<DownloaderAdapter.ViewHolderWrapper>
{
  private static final int TYPE_COUNTRY = 0;
  private static final int TYPE_HEADER = 1;
  private static final int TYPE_CONTINENT = 2; // Atlas: a continent in the list of all maps.

  private static final String DOWNLOADER_MENU_ID = "DOWNLOADER_BOTTOM_SHEET";

  private final RecyclerView mRecycler;
  private final Activity mActivity;
  private final DownloaderFragment mFragment;
  private boolean mMyMapsMode = true;
  // Atlas: the continent whose countries the list of all maps shows (its name's string id), 0 while it lists the continents.
  private int mContinentName;
  private boolean mSearchResultsMode;
  private String mSearchQuery;

  private final List<GenericItem> mItemsAndHeader = new ArrayList<>();

  private final List<CountryItem> mItems = new ArrayList<>();
  // Core Country.id String -> List<CountryItem> mapping index for updateItem function.
  // Use List, because we have multiple search results now for a single country.
  private final Map<String, List<CountryItem>> mCountryIndex = new HashMap<>();

  private final Stack<PathEntry> mPath =
      new Stack<>(); // Holds navigation history. The last element is the current level.

  private int mListenerSlot;
  private CountryItem mSelectedItem;

  private final OnBackPressedCallback mBackPressedCallback = new OnBackPressedCallback(false) {
    @Override
    public void handleOnBackPressed()
    {
      goUpwards();
      setEnabled(canGoUpwards());
    }
  };

  private static class GenericItem
  {
    @Nullable
    public final String mHeaderText;
    @Nullable
    public final CountryItem mItem;
    public GenericItem(@Nullable CountryItem item)
    {
      mItem = item;
      mHeaderText = null;
    }
    public GenericItem(@Nullable String headerText)
    {
      mItem = null;
      mHeaderText = headerText;
    }
    // Atlas: a continent row; its name's string id says which.
    public int mContinentName;
    public GenericItem(int continentName, @NonNull String name)
    {
      mItem = null;
      mHeaderText = name;
      mContinentName = continentName;
    }
  }

  private void onDownloadActionSelected(final CountryItem item, DownloaderAdapter adapter)
  {
    MapManagerHelper.warn3gAndDownload(adapter.mActivity, item.id, null);
  }

  private void onUpdateActionSelected(final CountryItem item, DownloaderAdapter adapter)
  {
    item.update();
    if (item.status != CountryItem.STATUS_UPDATABLE)
      return;
    MapManagerHelper.warnOn3gUpdate(adapter.mActivity, item.id, () -> MapManagerHelper.startUpdate(item.id));
  }

  private void onExploreActionSelected(CountryItem item, DownloaderAdapter adapter)
  {
    Intent intent = new Intent(adapter.mActivity, MwmActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
    intent.putExtra(MwmActivity.EXTRA_COUNTRY_ID, item.id);
    adapter.mActivity.startActivity(intent);

    if (!(adapter.mActivity instanceof MwmActivity))
      adapter.mActivity.finish();
  }

  void onDeleteActionSelected(final CountryItem item, final DownloaderAdapter adapter)
  {
    if (RoutingController.get().isNavigating())
    {
      new MaterialAlertDialogBuilder(adapter.mActivity)
          .setTitle(R.string.downloader_delete_map)
          .setMessage(R.string.downloader_delete_map_while_routing_dialog)
          .setPositiveButton(R.string.ok, null)
          .show();
      return;
    }

    if (!MapManager.nativeHasUnsavedEditorChanges(item.id))
    {
      deleteNode(item, adapter);
      return;
    }

    new MaterialAlertDialogBuilder(adapter.mActivity)
        .setTitle(R.string.downloader_delete_map)
        .setMessage(R.string.downloader_delete_map_dialog)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.ok, (dialog, which) -> deleteNode(item, adapter))
        .show();
  }

  private void onCancelActionSelected(CountryItem item)
  {
    MapManager.nativeCancel(item.id);
  }

  private void deleteNode(CountryItem item)
  {
    MapManager.nativeCancel(item.id);
    MapManager.nativeDelete(item.id);
    OnmapDownloader.setAutodownloadLocked(true);
  }

  private void deleteNode(CountryItem item, DownloaderAdapter adapter)
  {
    if (adapter.mActivity instanceof MwmActivity)
    {
      ((MwmActivity) adapter.mActivity).closePlacePage();
    }
    deleteNode(item);
    refreshData();
  }

  private record PathEntry(CountryItem item, boolean myMapsMode, int topPosition, int topOffset)
  {
    @Override
    public String toString()
    {
      return item.id + " (" + item.name + "), "
    + "myMapsMode: " + myMapsMode + ", topPosition: " + topPosition + ", topOffset: " + topOffset;
    }
  }

  private final MapManager.StorageCallback mStorageCallback = new MapManager.StorageCallback() {
    private void updateItem(String countryId)
    {
      List<CountryItem> lst = mCountryIndex.get(countryId);
      if (lst == null)
        return;

      for (CountryItem ci : lst)
      {
        ci.update();

        LinearLayoutManager lm = (LinearLayoutManager) mRecycler.getLayoutManager();
        int first = lm.findFirstVisibleItemPosition();
        int last = lm.findLastVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION)
          return;

        for (int i = first; i <= last; i++)
        {
          ViewHolderWrapper vh = (ViewHolderWrapper) mRecycler.findViewHolderForAdapterPosition(i);
          if (vh != null && vh.mKind == TYPE_COUNTRY && ((CountryItem) vh.mHolder.mItem).id.equals(countryId))
            vh.mHolder.rebind();
        }
      }
    }

    @Override
    public void onStatusChanged(List<MapManager.StorageCallbackData> data)
    {
      for (MapManager.StorageCallbackData item : data)
      {
        if (item.isLeafNode && item.newStatus == CountryItem.STATUS_FAILED)
        {
          MapManagerHelper.showError(mActivity, item, null);
          break;
        }
      }

      for (MapManager.StorageCallbackData item : data)
      {
        updateItem(item.countryId);
      }
    }

    @Override
    public void onProgress(String countryId, long localSize, long remoteSize)
    {
      updateItem(countryId);
    }
  };

  private View createViewHolderFrame(ViewGroup parent, int kind)
  {
    if (kind == TYPE_COUNTRY)
      return inflate(parent, R.layout.downloader_item);
    else if (kind == TYPE_CONTINENT)
      return inflate(parent, R.layout.downloader_item_continent);
    else
      return inflate(parent, R.layout.downloader_item_header);
  }

  class ViewHolderWrapper extends RecyclerView.ViewHolder
  {
    private final int mKind;
    @NonNull
    private final BaseInnerViewHolder mHolder;

    ViewHolderWrapper(@NonNull ViewGroup parent, int kind)
    {
      super(createViewHolderFrame(parent, kind));

      mKind = kind;
      if (mKind == TYPE_COUNTRY)
        mHolder = new ItemViewHolder(itemView);
      else
        mHolder = new HeaderViewHolder(itemView);
    }

    @SuppressWarnings("unchecked")
    void bind(int position)
    {
      final GenericItem item = mItemsAndHeader.get(position);
      if (mKind == TYPE_COUNTRY && item.mItem != null)
        mHolder.bind(item.mItem);
      else if (item.mHeaderText != null)
        mHolder.bind(item.mHeaderText);
      if (mKind == TYPE_CONTINENT)
        itemView.setOnClickListener(v -> openContinent(item.mContinentName));
    }
  }

  private static abstract class BaseInnerViewHolder<T>
  {
    T mItem;

    void bind(T item)
    {
      mItem = item;
    }

    void rebind()
    {
      bind(mItem);
    }
  }

  private void showBottomSheet(CountryItem selectedItem)
  {
    mSelectedItem = selectedItem;
    MenuBottomSheetFragment.newInstance(DOWNLOADER_MENU_ID, mSelectedItem.name)
        .show(mFragment.getChildFragmentManager(), DOWNLOADER_MENU_ID);
  }

  public ArrayList<MenuBottomSheetItem> getMenuItems()
  {
    ArrayList<MenuBottomSheetItem> items = new ArrayList<>();
    switch (mSelectedItem.status)
    {
    case CountryItem.STATUS_DOWNLOADABLE: items.add(getDownloadMenuItem()); break;

    case CountryItem.STATUS_UPDATABLE:
      items.add(getUpdateMenuItem());
      // Fallthrough

    case CountryItem.STATUS_DONE:
      if (!mSelectedItem.isExpandable())
        items.add(getExploreMenuItem());
      appendDeleteMenuItem(items);
      break;

    case CountryItem.STATUS_FAILED:
      items.add(getCancelMenuItem());

      if (mSelectedItem.present)
      {
        appendDeleteMenuItem(items);
        items.add(getExploreMenuItem());
      }
      break;

    case CountryItem.STATUS_PROGRESS:
    case CountryItem.STATUS_APPLYING:
    case CountryItem.STATUS_ENQUEUED:
      items.add(getCancelMenuItem());

      if (mSelectedItem.present)
        items.add(getExploreMenuItem());
      break;

    case CountryItem.STATUS_PARTLY:
      items.add(getDownloadMenuItem());
      appendDeleteMenuItem(items);
      break;
    }
    return items;
  }

  private MenuBottomSheetItem getDownloadMenuItem()
  {
    return new MenuBottomSheetItem(R.string.downloader_download_map, R.drawable.ic_download,
                                   () -> onDownloadActionSelected(mSelectedItem, DownloaderAdapter.this));
  }

  private MenuBottomSheetItem getUpdateMenuItem()
  {
    return new MenuBottomSheetItem(R.string.downloader_update_map, R.drawable.ic_update,
                                   () -> onUpdateActionSelected(mSelectedItem, DownloaderAdapter.this));
  }

  private MenuBottomSheetItem getExploreMenuItem()
  {
    return new MenuBottomSheetItem(R.string.zoom_to_country, R.drawable.ic_explore,
                                   () -> onExploreActionSelected(mSelectedItem, DownloaderAdapter.this));
  }

  private void appendDeleteMenuItem(ArrayList<MenuBottomSheetItem> items)
  {
    // Do not show "Delete" option for World files.
    // Checking the name is not beautiful, but the simplest way for now ..
    if (!mSelectedItem.id.startsWith("World"))
    {
      items.add(MenuBottomSheetItem.armed(R.string.delete, R.drawable.ic_delete,
                                        () -> onDeleteActionSelected(mSelectedItem, DownloaderAdapter.this)));
    }
  }

  private MenuBottomSheetItem getCancelMenuItem()
  {
    return new MenuBottomSheetItem(R.string.cancel, R.drawable.ic_close, () -> onCancelActionSelected(mSelectedItem));
  }

  private class ItemViewHolder extends BaseInnerViewHolder<CountryItem>
  {
    private final DownloaderStatusIcon mStatusIcon;
    private final MaterialTextView mName;
    private final MaterialTextView mSubtitle;
    private final MaterialTextView mFoundName;
    private final MaterialTextView mSize;

    private void processClick(boolean clickOnStatus)
    {
      switch (mItem.status)
      {
      case CountryItem.STATUS_DONE, CountryItem.STATUS_PROGRESS, CountryItem.STATUS_APPLYING,
          CountryItem.STATUS_ENQUEUED ->
        processLongClick();
      case CountryItem.STATUS_DOWNLOADABLE, CountryItem.STATUS_PARTLY ->
      {
        if (clickOnStatus)
        {
          if (mItem.isExpandable())
            goDeeper(mItem, true);
          else
            onDownloadActionSelected(mItem, DownloaderAdapter.this);
        }
        else
          processLongClick();
      }
      case CountryItem.STATUS_FAILED -> MapManagerHelper.warn3gAndRetry(mActivity, mItem.id, null);
      case CountryItem.STATUS_UPDATABLE ->
        MapManagerHelper.warnOn3gUpdate(mActivity, mItem.id, () -> MapManagerHelper.startUpdate(mItem.id));
      default -> throw new IllegalArgumentException("Inappropriate item status: " + mItem.status);
      }
    }

    private void processLongClick()
    {
      showBottomSheet(mItem);
    }

    ItemViewHolder(View frame)
    {
      mStatusIcon = new DownloaderStatusIcon(frame.findViewById(R.id.downloader_status_frame)) {
        @Override
        protected int selectIcon(CountryItem country)
        {
          if (country.status == CountryItem.STATUS_DOWNLOADABLE || country.status == CountryItem.STATUS_PARTLY)
          {
            return (country.isExpandable() ? (mMyMapsMode ? R.drawable.downloader_folder_done : R.drawable.downloader_folder)
                                           : R.drawable.downloader_download);
          }

          return super.selectIcon(country);
        }

        @Override
        protected void updateIcon(CountryItem country)
        {
          super.updateIcon(country);
          mIcon.setFocusable(country.isExpandable() && country.status != CountryItem.STATUS_DONE);
        }
      }.setOnIconClickListener(v -> processClick(true)).setOnCancelClickListener(v -> {
        MapManager.nativeCancel(mItem.id);
        refreshData();
      });

      mName = frame.findViewById(R.id.name);
      mSubtitle = frame.findViewById(R.id.subtitle);
      mFoundName = frame.findViewById(R.id.found_name);
      mSize = frame.findViewById(R.id.size);

      frame.setOnClickListener(v -> {
        if (mItem.isExpandable())
          goDeeper(mItem, true);
        else
          processClick(false);
      });

      frame.setOnLongClickListener(v -> {
        processLongClick();
        return true;
      });
    }

    @Override
    void bind(CountryItem item)
    {
      super.bind(item);

      String found = null;
      if (mSearchResultsMode)
      {
        mName.setText(mItem.name);

        String searchResultName = mItem.searchResultName;
        if (!TextUtils.isEmpty(searchResultName))
        {
          found = StringUtils.toLowerCase(searchResultName);
          SpannableStringBuilder builder = new SpannableStringBuilder(searchResultName);
          int start = found.indexOf(mSearchQuery);
          int end = start + mSearchQuery.length();
          if (start > -1)
            builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

          mFoundName.setText(builder);
        }

        if (!mItem.isExpandable())
          UiUtils.setTextAndHideIfEmpty(mSubtitle, mItem.topmostParentName);
      }
      else
      {
        mName.setText(mItem.name);
        if (!mItem.isExpandable())
          UiUtils.setTextAndHideIfEmpty(mSubtitle, mItem.description);
      }

      if (mItem.isExpandable())
      {
        UiUtils.setTextAndHideIfEmpty(
            mSubtitle,
            String.format("%s: %s", mActivity.getString(R.string.downloader_status_maps),
                          mActivity.getString(R.string.downloader_of, mItem.childCount, mItem.totalChildCount)));
      }

      UiUtils.showIf(mSearchResultsMode && !TextUtils.isEmpty(found), mFoundName);

      long size = getMapDisplaySize();
      mSize.setText(StringUtils.getFileSizeString(mFragment.requireContext(), size));
      mStatusIcon.update(mItem);
    }

    private long getMapDisplaySize()
    {
      long size;
      if (mItem.status == CountryItem.STATUS_ENQUEUED || mItem.status == CountryItem.STATUS_PROGRESS
          || mItem.status == CountryItem.STATUS_APPLYING)
      {
        size = mItem.enqueuedSize;
      }
      else if (mItem.status == CountryItem.STATUS_FAILED || mItem.status == CountryItem.STATUS_DOWNLOADABLE)
      {
        size = mItem.totalSize;
      }
      else
      {
        size = ((!mSearchResultsMode && mMyMapsMode) ? mItem.size : mItem.totalSize);
      }
      return size;
    }
  }

  static class HeaderViewHolder extends BaseInnerViewHolder<String>
  {
    @NonNull
    private final MaterialTextView mTitle;

    HeaderViewHolder(@NonNull View frame)
    {
      mTitle = frame.findViewById(R.id.title);
    }

    void bind(String text)
    {
      mTitle.setText(text);
    }
  }

  // Atlas: the list of all maps is browsed by continent: first the continents, then one continent's countries.
  private boolean showsContinents()
  {
    return !mMyMapsMode && !mSearchResultsMode && CountryItem.isRoot(getCurrentRootId());
  }

  private static int continentNameOf(@NonNull CountryItem ci)
  {
    // The two world overview files belong to no continent, and every phone has them already.
    if ("World".equals(ci.id) || "WorldCoasts".equals(ci.id))
      return 0;
    final Continents.Continent continent = Continents.of(ci.id);
    return continent == null ? Continents.OTHER : continent.name;
  }

  private void openContinent(int continentName)
  {
    mContinentName = continentName;
    processData();
    mRecycler.scrollToPosition(0);
    mFragment.update();
  }

  private void addContinentRows()
  {
    final java.util.Set<Integer> present = new java.util.HashSet<>();
    for (CountryItem ci : mItems)
      present.add(continentNameOf(ci));
    for (Continents.Continent continent : Continents.ALL)
      if (present.contains(continent.name))
        mItemsAndHeader.add(new GenericItem(continent.name, mActivity.getString(continent.name)));
    if (present.contains(Continents.OTHER))
      mItemsAndHeader.add(new GenericItem(Continents.OTHER, mActivity.getString(Continents.OTHER)));
  }

  private void collectHeaders()
  {
    mItemsAndHeader.clear();

    final boolean continents = showsContinents();
    if (continents && mContinentName != 0)
      mItemsAndHeader.add(new GenericItem(mActivity.getString(mContinentName)));

    int headerId = 0;
    int prev = -1;
    for (CountryItem ci : mItems)
    {
      // Atlas: under "All maps" the maps near the phone stay on top; the rest waits inside its continent.
      if (continents && mContinentName == 0 && ci.category != CountryItem.CATEGORY_NEAR_ME)
        continue;
      if (continents && mContinentName != 0 && continentNameOf(ci) != mContinentName)
        continue;
      // Disable headers when using the search
      if (!mSearchResultsMode)
      {
        switch (ci.category)
        {
        case CountryItem.CATEGORY_NEAR_ME ->
        {
          if (ci.category != prev)
          {
            headerId = CountryItem.CATEGORY_NEAR_ME;
            mItemsAndHeader.add(new GenericItem(mActivity.getString(R.string.downloader_near_me_subtitle)));
            prev = ci.category;
          }
        }
        case CountryItem.CATEGORY_DOWNLOADED ->
        {
          if (ci.category != prev)
          {
            // Atlas: no "Downloaded" heading; the chosen tab above the list says it.
            headerId = CountryItem.CATEGORY_DOWNLOADED;
            prev = ci.category;
          }
        }
        default ->
        {
          int prevHeader = headerId;
          headerId = CountryItem.CATEGORY_AVAILABLE + ci.name.charAt(0);
          if (headerId != prevHeader)
            mItemsAndHeader.add(new GenericItem(StringUtils.toUpperCase(ci.name.substring(0, 1))));
          prev = ci.category;
        }
        }
        ci.headerId = headerId;
      }
      mItemsAndHeader.add(new GenericItem(ci));
    }

    if (continents && mContinentName == 0)
      addContinentRows();
  }

  void refreshData()
  {
    mItems.clear();

    String parent = getCurrentRootId();
    boolean hasLocation = false;
    double lat = 0.0;
    double lon = 0.0;

    if (!mMyMapsMode && CountryItem.isRoot(parent))
    {
      Location loc = MwmApplication.from(mActivity).getLocationHelper().getSavedLocation();
      hasLocation = (loc != null);
      if (hasLocation)
      {
        lat = loc.getLatitude();
        lon = loc.getLongitude();
      }
    }

    MapManager.nativeListItems(parent, lat, lon, hasLocation, mMyMapsMode, mItems);
    processData();
  }

  void setSearchResultsMode(@NonNull Collection<CountryItem> results, String query)
  {
    mSearchResultsMode = true;
    mSearchQuery = StringUtils.toLowerCase(query);

    mItems.clear();
    mItems.addAll(results);
    processData();
  }

  void resetSearchResultsMode()
  {
    mSearchResultsMode = false;
    mSearchQuery = null;
    refreshData();
  }

  private void processData()
  {
    // avoid repetition
    final Map<String, CountryItem> uniqueItems = new LinkedHashMap<>();
    for (CountryItem ci : mItems)
      uniqueItems.putIfAbsent(ci.id, ci);
    mItems.clear();
    mItems.addAll(uniqueItems.values());

    if (!mSearchResultsMode)
      Collections.sort(mItems);

    collectHeaders();

    mCountryIndex.clear();
    for (CountryItem ci : mItems)
    {
      List<CountryItem> lst = mCountryIndex.get(ci.id);
      if (lst != null)
        lst.add(ci);
      else
      {
        lst = new ArrayList<>();
        lst.add(ci);
        mCountryIndex.put(ci.id, lst);
      }
    }

    if (mItems.isEmpty())
      mFragment.setupPlaceholder();

    mFragment.showPlaceholder(mItems.isEmpty());

    notifyDataSetChanged();
  }

  DownloaderAdapter(DownloaderFragment fragment)
  {
    mActivity = fragment.requireActivity();
    mFragment = fragment;
    mRecycler = mFragment.getRecyclerView();
  }

  @NonNull
  private View inflate(ViewGroup parent, @LayoutRes int layoutId)
  {
    return LayoutInflater.from(mActivity).inflate(layoutId, parent, false);
  }

  @Override
  public int getItemViewType(int position)
  {
    if (mItemsAndHeader.get(position).mItem != null)
      return TYPE_COUNTRY;
    else if (mItemsAndHeader.get(position).mContinentName != 0)
      return TYPE_CONTINENT;
    else
      return TYPE_HEADER;
  }

  @Override
  public ViewHolderWrapper onCreateViewHolder(ViewGroup parent, int viewType)
  {
    return new ViewHolderWrapper(parent, viewType);
  }

  @Override
  public void onBindViewHolder(ViewHolderWrapper holder, int position)
  {
    holder.bind(position);
  }

  @Override
  public int getItemCount()
  {
    return mItemsAndHeader.size();
  }

  private void goDeeper(CountryItem child, boolean refresh)
  {
    LinearLayoutManager lm = (LinearLayoutManager) mRecycler.getLayoutManager();

    // Save scroll positions (top item + item`s offset) for current hierarchy level
    int position = lm.findFirstVisibleItemPosition();
    int offset;

    if (position > -1)
      offset = lm.findViewByPosition(position).getTop();
    else
    {
      position = 0;
      offset = 0;
    }

    boolean wasEmpty = mPath.isEmpty();
    mPath.push(new PathEntry(child, mMyMapsMode, position, offset));
    mMyMapsMode &= (!mSearchResultsMode || child.childCount > 0);

    if (wasEmpty)
      mFragment.clearSearchQuery();

    lm.scrollToPosition(0);

    mBackPressedCallback.setEnabled(true);
    if (!refresh)
      return;

    if (mSearchResultsMode)
      resetSearchResultsMode();
    else
      refreshData();
    mFragment.update();
  }

  boolean canGoUpwards()
  {
    return !mPath.isEmpty();
  }

  boolean goUpwards()
  {
    if (!canGoUpwards())
      return false;

    // Atlas: Back from a continent's countries shows the continents again.
    if (mContinentName != 0 && showsContinents())
    {
      mContinentName = 0;
      processData();
      mRecycler.scrollToPosition(0);
      mFragment.update();
      return true;
    }

    PathEntry entry = mPath.pop();
    mMyMapsMode = entry.myMapsMode;
    mBackPressedCallback.setEnabled(canGoUpwards()); // Atlas: a tab can go all the way up at once.
    refreshData();

    LinearLayoutManager lm = (LinearLayoutManager) mRecycler.getLayoutManager();
    lm.scrollToPositionWithOffset(entry.topPosition, entry.topOffset);

    mFragment.update();
    return true;
  }

  @NonNull
  OnBackPressedCallback getBackPressedCallback()
  {
    return mBackPressedCallback;
  }

  void setAvailableMapsMode()
  {
    goDeeper(getCurrentRootItem(), false);
    mMyMapsMode = false;
    mContinentName = 0;
    refreshData();
  }

  private CountryItem getCurrentRootItem()
  {
    return (canGoUpwards() ? mPath.peek().item : CountryItem.fill(CountryItem.getRootId()));
  }

  @NonNull
  String getCurrentRootId()
  {
    return (canGoUpwards() ? getCurrentRootItem().id : CountryItem.getRootId());
  }

  @Nullable
  String getCurrentRootName()
  {
    return (canGoUpwards() ? getCurrentRootItem().name : null);
  }

  boolean isMyMapsMode()
  {
    return mMyMapsMode;
  }

  void attach()
  {
    mListenerSlot = MapManager.nativeSubscribe(mStorageCallback);
  }

  void detach()
  {
    MapManager.nativeUnsubscribe(mListenerSlot);
  }

  boolean isSearchResultsMode()
  {
    return mSearchResultsMode;
  }
}
