package app.organicmaps.widget.placepage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MonthHoursTest
{
  private Locale saved;

  @Before
  public void setUp()
  {
    saved = Locale.getDefault();
    Locale.setDefault(Locale.US);
  }

  @After
  public void tearDown()
  {
    Locale.setDefault(saved);
  }

  @Test
  public void months_with_hours_one_line_a_rule()
  {
    final List<MonthHours.Line> lines =
        MonthHours.parse("Jan-Apr,Oct-Dec 09:00-19:00; May-Sep 09:00-22:00", false, Locale.US);
    assertEquals(2, lines.size());
    assertEquals("Jan–Apr, Oct–Dec", lines.get(0).when);
    assertEquals(Arrays.asList("09:00 AM—07:00 PM"), lines.get(0).shifts);
    assertEquals("May–Sep", lines.get(1).when);
    assertEquals(Arrays.asList("09:00 AM—10:00 PM"), lines.get(1).shifts);
  }

  @Test
  public void twenty_four_hour_clock_and_split_shifts()
  {
    final List<MonthHours.Line> lines = MonthHours.parse("Apr-Oct 10:00-12:00,13:00-18:00", true, Locale.US);
    assertEquals(1, lines.size());
    assertEquals(Arrays.asList("10:00—12:00", "13:00—18:00"), lines.get(0).shifts);
  }

  @Test
  public void weekdays_and_off()
  {
    final List<MonthHours.Line> lines = MonthHours.parse("Nov-Mar Sa-Su 10:00-16:00; Nov-Mar Mo-Fr off", true, Locale.US);
    assertEquals(2, lines.size());
    assertEquals("Nov–Mar Sat–Sun", lines.get(0).when);
    assertEquals("Nov–Mar Mon–Fri", lines.get(1).when);
    assertTrue(lines.get(1).isOff());

    final Calendar janSaturday = Calendar.getInstance();
    janSaturday.set(2026, Calendar.JANUARY, 3); // a Saturday
    assertTrue(lines.get(0).holdsOn(janSaturday));
    assertFalse(lines.get(1).holdsOn(janSaturday));
    final Calendar julySaturday = Calendar.getInstance();
    julySaturday.set(2026, Calendar.JULY, 4);
    assertFalse(lines.get(0).holdsOn(julySaturday));
  }

  @Test
  public void anything_else_is_left_to_the_caller()
  {
    assertNull(MonthHours.parse(null, true, Locale.US));
    assertNull(MonthHours.parse("", true, Locale.US));
    assertNull(MonthHours.parse("Mo-Fr 09:00-17:00", true, Locale.US)); // no months: the timetables handle it
    assertNull(MonthHours.parse("Jan 01-Mar 15 09:00-17:00", true, Locale.US));
    assertNull(MonthHours.parse("Jan-Apr 09:00-19:00; PH off", true, Locale.US));
    assertNull(MonthHours.parse("Jan-Apr sunrise-sunset", true, Locale.US));
    assertNull(MonthHours.parse("Jan-Apr 9-19", true, Locale.US));
  }
}
