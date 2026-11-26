package app.organicmaps.widget.placepage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.editor.data.HoursMinutes;
import app.organicmaps.sdk.editor.data.Timespan;
import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Opening hours that change with the months ("Jan-Apr,Oct-Dec 09:00-19:00; May-Sep 09:00-22:00").
 *
 * The editor's timetables (OpeningHours.nativeTimetablesFromString) have no months, so such a
 * string came back unparsed and the place page showed it raw beside hours it words properly.
 * This reads the plain shapes of it, one line per rule: months, optional weekdays, then times
 * or off. Anything else gives null and the caller keeps the string as it is.
 */
public final class MonthHours
{
  private static final String[] MONTHS = {"jan", "feb", "mar", "apr", "may", "jun",
                                          "jul", "aug", "sep", "oct", "nov", "dec"};
  // OSM's order, Mo first; the values are Calendar's (SUNDAY = 1).
  private static final String[] OSM_DAYS = {"mo", "tu", "we", "th", "fr", "sa", "su"};
  private static final int[] CALENDAR_DAYS = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                                              Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY,
                                              Calendar.SUNDAY};

  /** One rule: when it holds ("Jan–Apr, Oct–Dec", "May–Sep Sat–Sun") and its hours. */
  public static final class Line
  {
    @NonNull
    public final String when;
    /** The time spans as shown, empty when the rule says off / closed. */
    @NonNull
    public final List<String> shifts;
    /** Months 0..11 and Calendar weekdays the rule covers, to find the one in force today. */
    private final boolean[] months;
    @Nullable
    private final boolean[] days;

    Line(@NonNull String when, @NonNull List<String> shifts, boolean[] months, @Nullable boolean[] days)
    {
      this.when = when;
      this.shifts = shifts;
      this.months = months;
      this.days = days;
    }

    public boolean isOff()
    {
      return shifts.isEmpty();
    }

    /** True when the rule covers [cal]'s month and, if it names days, its weekday. */
    public boolean holdsOn(@NonNull Calendar cal)
    {
      return months[cal.get(Calendar.MONTH)] && (days == null || days[cal.get(Calendar.DAY_OF_WEEK)]);
    }
  }

  private MonthHours() {}

  /**
   * The rules of [oh], or null when it is not wholly months-and-times (then nothing here
   * improves on what the caller already does with it). Needs at least one month selector.
   */
  @Nullable
  public static List<Line> parse(@Nullable String oh, boolean is24HourFormat, @NonNull Locale locale)
  {
    if (oh == null || oh.trim().isEmpty())
      return null;
    final DateFormatSymbols symbols = DateFormatSymbols.getInstance(locale);
    final String[] monthNames = symbols.getShortMonths();
    final String[] dayNames = symbols.getShortWeekdays();
    final List<Line> lines = new ArrayList<>();
    boolean anyMonths = false;
    for (String rawRule : oh.split(";"))
    {
      final String rule = rawRule.trim();
      if (rule.isEmpty())
        continue;
      final String[] parts = rule.split("\\s+");
      int p = 0;

      boolean[] months = null;
      String monthsText = null;
      if (p < parts.length)
      {
        months = new boolean[12];
        monthsText = selector(parts[p], MONTHS, null, monthNames, months);
        if (monthsText != null)
        {
          anyMonths = true;
          p++;
        }
      }
      if (monthsText == null)
      {
        months = new boolean[12];
        Arrays.fill(months, true);
      }

      boolean[] days = null;
      String daysText = null;
      if (p < parts.length)
      {
        final boolean[] d = new boolean[8];
        daysText = selector(parts[p], OSM_DAYS, CALENDAR_DAYS, dayNames, d);
        if (daysText != null)
        {
          days = d;
          p++;
        }
      }

      if (p != parts.length - 1)
        return null;
      final List<String> shifts = times(parts[p], is24HourFormat);
      if (shifts == null)
        return null;

      final String when;
      if (monthsText != null && daysText != null)
        when = monthsText + " " + daysText;
      else if (monthsText != null)
        when = monthsText;
      else if (daysText != null)
        when = daysText;
      else
        when = "";
      lines.add(new Line(when, shifts, months, days));
    }
    return anyMonths && !lines.isEmpty() ? lines : null;
  }

  /**
   * A comma list of names or name ranges ("Jan-Apr,Oct-Dec", "Mo-Fr,Su"), marked into [marks]
   * and worded with [shown]; null if [token] is anything else. Ranges may wrap (Nov-Feb).
   */
  @Nullable
  private static String selector(@NonNull String token, @NonNull String[] keys, @Nullable int[] values,
                                 @NonNull String[] shown, @NonNull boolean[] marks)
  {
    final StringBuilder text = new StringBuilder();
    for (String item : token.split(",", -1))
    {
      final String[] ends = item.split("-", -1);
      if (ends.length < 1 || ends.length > 2)
        return null;
      final int from = indexOf(keys, ends[0]);
      final int to = ends.length == 2 ? indexOf(keys, ends[1]) : from;
      if (from < 0 || to < 0)
        return null;
      for (int i = from;; i = (i + 1) % keys.length)
      {
        marks[values == null ? i : values[i]] = true;
        if (i == to)
          break;
      }
      if (text.length() > 0)
        text.append(", ");
      text.append(shown[values == null ? from : values[from]]);
      if (to != from)
        text.append('–').append(shown[values == null ? to : values[to]]);
    }
    return text.toString();
  }

  private static int indexOf(@NonNull String[] keys, @NonNull String s)
  {
    final String k = s.toLowerCase(Locale.ROOT);
    for (int i = 0; i < keys.length; i++)
      if (keys[i].equals(k))
        return i;
    return -1;
  }

  /** "09:00-19:00,20:00-22:00" as the place page words spans, an empty list for off/closed. */
  @Nullable
  private static List<String> times(@NonNull String token, boolean is24HourFormat)
  {
    final List<String> shifts = new ArrayList<>();
    final String t = token.toLowerCase(Locale.ROOT);
    if (t.equals("off") || t.equals("closed"))
      return shifts;
    for (String span : token.split(",", -1))
    {
      final String[] ends = span.split("-", -1);
      if (ends.length != 2)
        return null;
      final HoursMinutes start = time(ends[0], is24HourFormat);
      final HoursMinutes end = time(ends[1], is24HourFormat);
      if (start == null || end == null)
        return null;
      shifts.add(new Timespan(start, end).toWideString());
    }
    return shifts;
  }

  @Nullable
  private static HoursMinutes time(@NonNull String s, boolean is24HourFormat)
  {
    final String[] hm = s.split(":", -1);
    if (hm.length != 2 || hm[0].isEmpty() || hm[0].length() > 2 || hm[1].length() != 2)
      return null;
    try
    {
      final int h = Integer.parseInt(hm[0]);
      final int m = Integer.parseInt(hm[1]);
      if (h < 0 || h > 24 || m < 0 || m > 59 || (h == 24 && m != 0))
        return null;
      return new HoursMinutes(h, m, is24HourFormat);
    }
    catch (NumberFormatException e)
    {
      return null;
    }
  }
}
