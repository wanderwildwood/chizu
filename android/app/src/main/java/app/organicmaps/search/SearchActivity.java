package app.organicmaps.search;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import app.organicmaps.base.BaseMwmFragmentActivity;

public class SearchActivity extends BaseMwmFragmentActivity
{
  public static final String EXTRA_QUERY = "search_query";
  public static final String EXTRA_LOCALE = "locale";
  public static final String EXTRA_SEARCH_ON_MAP = "search_on_map";
  /// Atlas: opened as the start screen (Settings > Start on > Directory): cog for back, Map tile at the foot.
  public static final String EXTRA_DIRECTORY_HOME = "atlas.directory_home";

  /// Atlas: the directory as Atlas's start screen, over the map (see StartOn).
  public static void startDirectory(@NonNull Activity activity)
  {
    final Intent i = new Intent(activity, SearchActivity.class);
    i.putExtra(EXTRA_DIRECTORY_HOME, true);
    i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
    activity.startActivity(i);
  }

  public static void start(@NonNull Activity activity, @Nullable String query)
  {
    start(activity, query, null /* locale */, false /* isSearchOnMap */);
  }

  public static void start(@NonNull Activity activity, @Nullable String query, @Nullable String locale,
                           boolean isSearchOnMap)
  {
    final Intent i = new Intent(activity, SearchActivity.class);
    Bundle args = new Bundle();
    args.putString(EXTRA_QUERY, query);
    args.putString(EXTRA_LOCALE, locale);
    args.putBoolean(EXTRA_SEARCH_ON_MAP, isSearchOnMap);
    i.putExtras(args);
    activity.startActivity(i);
  }

  @Override
  protected Class<? extends Fragment> getFragmentClass()
  {
    return SearchFragment.class;
  }
}
