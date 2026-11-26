package app.organicmaps.search;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.R;
import app.organicmaps.editor.data.TimeFormatUtils;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.sdk.bookmarks.data.Metadata;
import app.organicmaps.sdk.editor.OpeningHours;
import app.organicmaps.sdk.editor.data.Timetable;
import app.organicmaps.sdk.search.SearchResult;
import app.organicmaps.sdk.util.DateUtils;
import app.organicmaps.util.Utils;
import app.organicmaps.util.WindowInsetUtils;
import app.organicmaps.widget.placepage.MonthHours;
import app.organicmaps.widget.placepage.sections.PlaceOpeningHoursAdapter;
import com.google.android.material.textview.MaterialTextView;
import com.wanderwildwood.chizu.ui.DottedRules;
import com.wanderwildwood.chizu.ui.PagedList;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Atlas: the place screen of the Directory. A search result tapped in search opens here instead of
 * on the map: name, distance and open status, then phone, address, the week's hours and website,
 * each only when the place has it, and at the foot Show on map and Directions.
 *
 * The details come from Framework.nativeGetPlaceInfo, which reads the feature without selecting it
 * on the map. Hours go through the place page's own parser and formatters (OpeningHours,
 * TimeFormatUtils, PlaceOpeningHoursAdapter's week helpers). A phone number opens the dialer with
 * the number filled in (ACTION_DIAL); nothing here ever calls by itself.
 *
 * The screen is a view laid over the search screen, so the results list underneath keeps its
 * scroll position for the way back.
 */
class DirectoryPlace
{
  @NonNull
  private final SearchFragment mFragment;
  @NonNull
  private final ViewGroup mHost;
  @Nullable
  private View mView;

  DirectoryPlace(@NonNull SearchFragment fragment, @NonNull ViewGroup host)
  {
    mFragment = fragment;
    mHost = host;
  }

  boolean isShown()
  {
    return mView != null;
  }

  void hide()
  {
    if (mView == null)
      return;
    mHost.removeView(mView);
    mView = null;
  }

  void show(@NonNull SearchResult result, int resultIndex, @NonNull String query)
  {
    hide();
    final Context context = mHost.getContext();
    final View view = LayoutInflater.from(context).inflate(R.layout.directory_place, mHost, false);
    mView = view;

    final MapObject place = (result.description != null && result.description.featureId != null)
                                ? Framework.nativeGetPlaceInfo(result.description.featureId)
                                : null;

    view.findViewById(R.id.directory_place_back).setOnClickListener(v -> hide());
    ((MaterialTextView) view.findViewById(R.id.directory_place_query)).setText(query.trim());
    ((MaterialTextView) view.findViewById(R.id.directory_place_name)).setText(nameOf(context, result, place));

    final MaterialTextView status = view.findViewById(R.id.directory_place_status);
    final String statusLine = statusLine(context, result);
    status.setText(statusLine);
    status.setVisibility(statusLine.isEmpty() ? View.GONE : View.VISIBLE);

    final RecyclerView rows = view.findViewById(R.id.directory_place_rows);
    rows.setLayoutManager(new LinearLayoutManager(context));
    rows.setAdapter(new RowsAdapter(rowsOf(context, result, place)));
    rows.addItemDecoration(new DottedRules(context));
    PagedList.turnsAPageOnSwipe(rows);

    final MaterialTextView showOnMap = view.findViewById(R.id.directory_place_show_on_map);
    final MaterialTextView directions = view.findViewById(R.id.directory_place_directions);
    oneIconBox(showOnMap);
    oneIconBox(directions);
    showOnMap.setOnClickListener(v -> mFragment.showSingleResultOnMap(result, resultIndex));
    directions.setOnClickListener(v -> mFragment.directionsTo(result, nameOf(context, result, place)));

    ViewCompat.setOnApplyWindowInsetsListener(view, WindowInsetUtils.PaddingInsetsListener.allSides());
    mHost.addView(view);
    ViewCompat.requestApplyInsets(view);
  }

  /**
   * The foot icons come in different widths (the route one is a bitmap); each is centered in the
   * same 24 dp box so the two labels line up.
   */
  private static void oneIconBox(@NonNull MaterialTextView row)
  {
    final Drawable icon = row.getCompoundDrawablesRelative()[0];
    if (icon == null)
      return;
    final int box = Math.round(24 * row.getResources().getDisplayMetrics().density);
    final int iw = Math.max(1, icon.getIntrinsicWidth());
    final int ih = Math.max(1, icon.getIntrinsicHeight());
    final float scale = Math.min((float) box / iw, (float) box / ih);
    final LayerDrawable boxed = new LayerDrawable(new Drawable[] {icon});
    boxed.setLayerGravity(0, Gravity.CENTER);
    boxed.setLayerSize(0, Math.round(iw * scale), Math.round(ih * scale));
    boxed.setBounds(0, 0, box, box);
    row.setCompoundDrawablesRelative(boxed, null, null, null);
  }

  /** The name as the results list shows it (with the branch), so the two screens agree. */
  @NonNull
  private static String nameOf(@NonNull Context context, @NonNull SearchResult result,
                               @Nullable MapObject place)
  {
    final String title = result.getFormattedTitle(context).toString();
    if (!TextUtils.isEmpty(title))
      return title;
    if (!TextUtils.isEmpty(result.name))
      return result.name;
    if (place != null && !TextUtils.isEmpty(place.getTitle()))
      return place.getTitle();
    return result.description != null && result.description.localizedFeatureType != null
        ? result.description.localizedFeatureType
        : "";
  }

  /** "0.4 mi · Open", the distance and the open state as the results list words them. */
  @NonNull
  private static String statusLine(@NonNull Context context, @NonNull SearchResult result)
  {
    final List<String> parts = new ArrayList<>();
    if (result.description != null && result.description.distance != null)
    {
      final String distance = result.description.distance.toString(context);
      if (!TextUtils.isEmpty(distance))
        parts.add(distance);
    }
    final String open = SearchAdapter.openState(context.getResources(), result);
    if (open != null)
      parts.add(open);
    return TextUtils.join(" · ", parts);
  }

  @NonNull
  private static List<Row> rowsOf(@NonNull Context context, @NonNull SearchResult result, @Nullable MapObject place)
  {
    final Resources res = context.getResources();
    final List<Row> rows = new ArrayList<>();

    if (place != null)
    {
      final String phones = place.getMetadata(Metadata.MetadataType.FMD_PHONE_NUMBER);
      if (!TextUtils.isEmpty(phones))
      {
        for (String phone : phones.split(";"))
        {
          final String number = phone.trim();
          if (!number.isEmpty())
            rows.add(new Row(res.getString(R.string.directory_phone), number, null,
                             v -> Utils.callPhone(v.getContext(), number)));
        }
      }
    }

    String address = place != null ? place.getAddress() : null;
    if (TextUtils.isEmpty(address))
      address = result.getFormattedAddress(context).toString();
    if (!TextUtils.isEmpty(address))
      rows.add(new Row(res.getString(R.string.address), address, null, null));

    if (place != null)
    {
      final List<HoursLine> hours = hoursOf(context, place.getMetadata(Metadata.MetadataType.FMD_OPEN_HOURS));
      if (!hours.isEmpty())
        rows.add(new Row(res.getString(R.string.directory_hours), null, hours, null));

      final String shown = place.getWebsiteUrl(true /* strip */, Metadata.MetadataType.FMD_WEBSITE);
      final String url = place.getWebsiteUrl(false /* strip */, Metadata.MetadataType.FMD_WEBSITE);
      if (!TextUtils.isEmpty(shown) && !TextUtils.isEmpty(url))
        rows.add(new Row(res.getString(R.string.website), shown, null, v -> Utils.openUrl(v.getContext(), url)));
    }
    return rows;
  }

  /** One line of the week: days, hours, and whether today falls in it. */
  static final class HoursLine
  {
    @NonNull
    final String days;
    @NonNull
    final String hours;
    final boolean today;

    HoursLine(@NonNull String days, @NonNull String hours, boolean today)
    {
      this.days = days;
      this.hours = hours;
      this.today = today;
    }
  }

  /**
   * The week as the place page words it (PlacePageOpeningHoursFragment, PlaceOpeningHoursAdapter):
   * the same parser, the same day ranges and shift strings, all laid out at once instead of folded.
   */
  @NonNull
  private static List<HoursLine> hoursOf(@NonNull Context context, @Nullable String ohStr)
  {
    final Resources res = context.getResources();
    final List<HoursLine> lines = new ArrayList<>();
    if (TextUtils.isEmpty(ohStr))
      return lines;

    final Timetable[] timetables = OpeningHours.nativeTimetablesFromString(ohStr);
    if (timetables == null || timetables.length == 0)
    {
      // Hours by the month ("Jan-Apr,Oct-Dec 09:00-19:00; May-Sep 09:00-22:00"): one line a rule.
      final List<MonthHours.Line> byMonth =
          MonthHours.parse(ohStr, DateUtils.is24HourFormat(context), Locale.getDefault());
      if (byMonth != null)
      {
        final Calendar now = Calendar.getInstance();
        boolean found = false;
        for (MonthHours.Line line : byMonth)
        {
          final boolean today = !found && line.holdsOn(now);
          found |= today;
          lines.add(new HoursLine(line.when, line.isOff() ? res.getString(R.string.day_off)
                                                          : TextUtils.join(", ", line.shifts), today));
        }
        return lines;
      }
      // Not parsed: shown as it is, as the place page does.
      switch (ohStr)
      {
        case "off", "closed" -> lines.add(new HoursLine("", res.getString(R.string.day_off), false));
        case "unknown" -> lines.add(new HoursLine("", res.getString(R.string.oh_unknown), false));
        default -> lines.add(new HoursLine("", ohStr, false));
      }
      return lines;
    }

    if (timetables[0].isFullWeek())
    {
      final Timetable tt = timetables[0];
      if (tt.isFullday)
        lines.add(new HoursLine("", res.getString(R.string.twentyfour_seven), false));
      else
        lines.add(new HoursLine(res.getString(R.string.daily), shifts(res, tt), false));
      return lines;
    }

    final int firstDay = Calendar.getInstance(Locale.getDefault()).getFirstDayOfWeek();
    final List<Integer> week = PlaceOpeningHoursAdapter.buildWeekByFirstDay(firstDay);
    final int today = week.indexOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK));
    // Same grouping as PlaceOpeningHoursAdapter.setTimetables: runs of days with one timetable,
    // and runs of closed days between them.
    for (int i = 0; i < week.size(); i++)
    {
      final int start = i;
      final Timetable tt = PlaceOpeningHoursAdapter.findScheduleForWeekDay(timetables, week.get(i));
      if (tt != null)
      {
        while (i < week.size() && tt.containsWeekday(week.get(i)))
          i++;
        i--;
      }
      else
      {
        while (i + 1 < week.size() && PlaceOpeningHoursAdapter.findScheduleForWeekDay(timetables, week.get(i + 1)) == null)
          i++;
      }
      final String days = TimeFormatUtils.formatWeekdaysRange(week.get(start), week.get(i));
      final String hours = tt == null ? res.getString(R.string.day_off) : shifts(res, tt);
      lines.add(new HoursLine(days, hours, today >= start && today <= i));
    }
    return lines;
  }

  @NonNull
  private static String shifts(@NonNull Resources res, @NonNull Timetable tt)
  {
    if (tt.isFullday)
      return res.getString(R.string.editor_time_allday);
    return TextUtils.join(", ", TimeFormatUtils.getShiftStrings(tt));
  }

  private static final class Row
  {
    @NonNull
    final String label;
    @Nullable
    final String value;
    @Nullable
    final List<HoursLine> hours;
    @Nullable
    final View.OnClickListener onClick;

    Row(@NonNull String label, @Nullable String value, @Nullable List<HoursLine> hours,
        @Nullable View.OnClickListener onClick)
    {
      this.label = label;
      this.value = value;
      this.hours = hours;
      this.onClick = onClick;
    }
  }

  private static final class RowsAdapter extends RecyclerView.Adapter<RowsAdapter.Holder>
  {
    @NonNull
    private final List<Row> mRows;

    RowsAdapter(@NonNull List<Row> rows)
    {
      mRows = rows;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
      return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.directory_place_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position)
    {
      holder.bind(mRows.get(position));
    }

    @Override
    public int getItemCount()
    {
      return mRows.size();
    }

    static final class Holder extends RecyclerView.ViewHolder
    {
      private final MaterialTextView mLabel;
      private final MaterialTextView mValue;
      private final TableLayout mHours;

      Holder(@NonNull View view)
      {
        super(view);
        mLabel = view.findViewById(R.id.directory_row_label);
        mValue = view.findViewById(R.id.directory_row_value);
        mHours = view.findViewById(R.id.directory_row_hours);
      }

      void bind(@NonNull Row row)
      {
        mLabel.setText(row.label);
        itemView.setOnClickListener(row.onClick);
        itemView.setClickable(row.onClick != null);
        mHours.removeAllViews();
        if (row.hours == null)
        {
          mValue.setVisibility(View.VISIBLE);
          mValue.setText(row.value);
          mHours.setVisibility(View.GONE);
          return;
        }
        mValue.setVisibility(View.GONE);
        mHours.setVisibility(View.VISIBLE);
        final Context context = itemView.getContext();
        final int gap = context.getResources().getDimensionPixelSize(R.dimen.margin_base);
        for (HoursLine line : row.hours)
        {
          final TableRow tr = new TableRow(context);
          final MaterialTextView days = cell(context, line.days, line.today);
          days.setPaddingRelative(0, 0, line.days.isEmpty() ? 0 : gap, 0);
          tr.addView(days);
          tr.addView(cell(context, line.hours, line.today));
          mHours.addView(tr);
        }
      }

      @NonNull
      private static MaterialTextView cell(@NonNull Context context, @NonNull String text, boolean bold)
      {
        final MaterialTextView tv = new MaterialTextView(context);
        tv.setText(text);
        tv.setTextAppearance(R.style.MwmTextAppearance_Body3);
        tv.setTextColor(context.getColor(R.color.house_ink));
        tv.setTypeface(Typeface.create(tv.getTypeface(), bold ? Typeface.BOLD : Typeface.NORMAL));
        return tv;
      }
    }
  }
}
