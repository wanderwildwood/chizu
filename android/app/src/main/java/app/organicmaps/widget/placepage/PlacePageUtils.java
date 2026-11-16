package app.organicmaps.widget.placepage;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import androidx.appcompat.widget.ListPopupWindow;
import com.wanderwildwood.chizu.ui.DarkMode;
import androidx.annotation.NonNull;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.sdk.Framework;
import app.organicmaps.util.Utils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import java.util.ArrayList;
import java.util.List;

public class PlacePageUtils
{
  static void updateMapViewport(@NonNull View parent, int placePageDistanceToTop, int viewportMinHeight)
  {
    parent.post(() -> {
      // Because of the post(), this lambda is called after the car.SurfaceRenderer.onStableAreaChanged() and breaks the
      // visibleRect configuration
      if (MwmApplication.from(parent.getContext()).getDisplayManager().isCarDisplayUsed())
        return;
      final int screenWidth = parent.getWidth();
      if (placePageDistanceToTop >= viewportMinHeight)
        Framework.nativeSetVisibleRect(0, 0, screenWidth, placePageDistanceToTop);
    });
  }

  static boolean isSettlingState(@BottomSheetBehavior.State int state)
  {
    return state == BottomSheetBehavior.STATE_SETTLING;
  }

  static boolean isDraggingState(@BottomSheetBehavior.State int state)
  {
    return state == BottomSheetBehavior.STATE_DRAGGING;
  }

  static boolean isCollapsedState(@BottomSheetBehavior.State int state)
  {
    return state == BottomSheetBehavior.STATE_COLLAPSED;
  }

  static boolean isExpandedState(@BottomSheetBehavior.State int state)
  {
    return state == BottomSheetBehavior.STATE_EXPANDED;
  }

  static boolean isHiddenState(@BottomSheetBehavior.State int state)
  {
    return state == BottomSheetBehavior.STATE_HIDDEN;
  }

  @NonNull
  static String toString(@BottomSheetBehavior.State int state)
  {
    return switch (state)
    {
      case BottomSheetBehavior.STATE_EXPANDED -> "EXPANDED";
      case BottomSheetBehavior.STATE_COLLAPSED -> "COLLAPSED";
      case BottomSheetBehavior.STATE_HALF_EXPANDED -> "HALF_EXPANDED";
      case BottomSheetBehavior.STATE_DRAGGING -> "DRAGGING";
      case BottomSheetBehavior.STATE_SETTLING -> "SETTLING";
      case BottomSheetBehavior.STATE_HIDDEN -> "HIDDEN";
      default -> throw new AssertionError("Unsupported state detected: " + state);
    };
  }

  public static void copyToClipboard(Context context, View frame, String text)
  {
    copyToClipboard(context, frame.getRootView().findViewById(R.id.pp_buttons_layout), frame, text);
  }

  public static void copyToClipboard(Context context, View snackbarContainer, View frame, String text)
  {
    Utils.copyTextToClipboard(context, text);

    KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
    // Starting from API 33, the automatic system control that shows copied text is displayed.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || keyguardManager.isDeviceLocked())
    {
      Utils.showSnackbarAbove(snackbarContainer, frame, context.getString(R.string.copied_to_clipboard, text));
    }
  }

  public static void showCopyPopup(Context context, View popupAnchor, List<String> items)
  {
    // A list popup rather than a PopupMenu: its window can be handed to dark mode.
    final ListPopupWindow popup = new ListPopupWindow(context);
    final String copyText = context.getResources().getString(android.R.string.copy);
    // A menu item can be clicked after PlacePageButtons is removed from the Views hierarchy so
    // let's find a container for the snackbar outside of PlacePageButtons and in advance.
    final View snackbarTarget = (View) popupAnchor.getRootView().findViewById(R.id.pp_buttons_layout).getParent();

    final List<String> labels = new ArrayList<>();
    for (String item : items)
      labels.add(String.format("%s %s", copyText, item));

    popup.setAnchorView(popupAnchor);
    popup.setModal(true);
    popup.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, labels));
    popup.setOnItemClickListener((parent, view, position, id) -> {
      copyToClipboard(context, snackbarTarget, popupAnchor, items.get(position));
      popup.dismiss();
    });
    popup.show();
    final ListView list = popup.getListView();
    if (list != null)
      DarkMode.INSTANCE.window(list);
  }
}
