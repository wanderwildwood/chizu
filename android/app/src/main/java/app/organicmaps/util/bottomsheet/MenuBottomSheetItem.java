package app.organicmaps.util.bottomsheet;

public class MenuBottomSheetItem
{
  public final int titleRes;
  public final int iconRes;
  public final int badgeCount;
  public final OnClickListener onClickListener;
  /** Atlas: the row asks for a second tap ("Delete — tap again") before it acts. */
  public boolean armed;

  /** A row for something that cannot be taken back: it acts on the second tap. */
  public static MenuBottomSheetItem armed(int titleRes, int iconRes, OnClickListener onClickListener)
  {
    final MenuBottomSheetItem item = new MenuBottomSheetItem(titleRes, iconRes, onClickListener);
    item.armed = true;
    return item;
  }

  public MenuBottomSheetItem(int titleRes, int iconRes, OnClickListener onClickListener)
  {
    this.titleRes = titleRes;
    this.iconRes = iconRes;
    this.badgeCount = 0;
    this.onClickListener = onClickListener;
  }

  public MenuBottomSheetItem(int titleRes, int iconRes, int badgeCount, OnClickListener onClickListener)
  {
    this.titleRes = titleRes;
    this.iconRes = iconRes;
    this.badgeCount = badgeCount;
    this.onClickListener = onClickListener;
  }

  public interface OnClickListener
  {
    void onClick();
  }
}
