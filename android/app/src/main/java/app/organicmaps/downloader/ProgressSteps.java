package app.organicmaps.downloader;

import android.text.TextUtils;
import android.widget.TextView;

/**
 * Download progress on E Ink: shown in steps of ten percent, and text set only when it changes,
 * so the panel redraws ten times in a download instead of a hundred.
 */
final class ProgressSteps
{
  static final int STEP = 10;

  private ProgressSteps() {}

  /** [percent] (0 to 100) rounded down to its step. */
  static int of(double percent)
  {
    return Math.max(0, Math.min(100, (int) (percent / STEP) * STEP));
  }

  /** Sets [text] on [view] only when it differs, since an equal setText still redraws. */
  static void setTextIfNew(TextView view, CharSequence text)
  {
    if (!TextUtils.equals(view.getText(), text))
      view.setText(text);
  }
}
