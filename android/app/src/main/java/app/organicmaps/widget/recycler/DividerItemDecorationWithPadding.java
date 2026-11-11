package app.organicmaps.widget.recycler;

import com.wanderwildwood.chizu.ui.DottedRules;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;
import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.R;
import app.organicmaps.bookmarks.Holders;
import app.organicmaps.editor.FeatureCategoryAdapter;
import java.util.Objects;

public class DividerItemDecorationWithPadding extends RecyclerView.ItemDecoration
{
  @Dimension
  private final int mStartMargin;
  @NonNull
  private final Drawable mDivider;
  @NonNull
  private final DottedRules.Rule mDottedRule;

  public DividerItemDecorationWithPadding(@NonNull Context context, int marginStart)
  {
    mDivider = Objects.requireNonNull(AppCompatResources.getDrawable(context, R.drawable.list_divider));
    mStartMargin = context.getResources().getDimensionPixelSize(marginStart);
    mDottedRule = new DottedRules.Rule(context);
  }

  @Override
  public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state)
  {
    if (state.isMeasuring())
      return;

    final int right = parent.getWidth();
    final int dividerHeight = mDivider.getIntrinsicHeight();

    final int childCount = parent.getChildCount();
    for (int i = 0; i < childCount; i++)
    {
      View child = parent.getChildAt(i);
      View nextChild = parent.getChildAt(i + 1);
      RecyclerView.ViewHolder viewHolder = parent.getChildViewHolder(child);
      RecyclerView.ViewHolder viewHolderNext = null;
      if (nextChild != null)
        viewHolderNext = parent.getChildViewHolder(nextChild);

      // For FeatureCategoryAdapter, only draw dividers around section headers
      boolean isSectionHeader = viewHolder instanceof FeatureCategoryAdapter.SectionHeaderViewHolder;
      boolean isNextSectionHeader = viewHolderNext instanceof FeatureCategoryAdapter.SectionHeaderViewHolder;
      if (parent.getAdapter() instanceof FeatureCategoryAdapter && !isSectionHeader && !isNextSectionHeader)
        continue;

      int top = child.getBottom();
      int bottom = top + dividerHeight;

      if (viewHolder instanceof Holders.SectionViewHolder || viewHolder instanceof Holders.HeaderViewHolder
          || isSectionHeader || viewHolderNext instanceof Holders.SectionViewHolder
          || viewHolderNext instanceof Holders.HeaderViewHolder || isNextSectionHeader
          // Atlas: one solid rule sets the actions apart from the lists; between the actions the dotted rule.
          || (viewHolderNext instanceof Holders.GeneralViewHolder && !(viewHolder instanceof Holders.GeneralViewHolder)))
        mDivider.setBounds(0, top, right, bottom);
      else
      {
        // Atlas: between ordinary rows the thin dotted rule, inset to the text on both sides;
        // nothing under the last row. Headings and sections keep the solid rule above.
        if (nextChild != null)
          mDottedRule.draw(c, right, top);
        continue;
      }

      mDivider.draw(c);
    }
  }
}
