package app.organicmaps.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import app.organicmaps.R;
import app.organicmaps.editor.ProfileActivity;
import app.organicmaps.sdk.editor.OsmOAuth;

public class SettingsPrefsFragment extends BaseXmlSettingsFragment
{
  @Override
  protected int getXmlResources()
  {
    return R.xml.prefs_main;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);
    // Atlas: About is the info icon at the top right of Settings, not a row in the list.
    final Bundle args = getArguments();
    if (args != null && args.containsKey(ARG_PREFERENCE_ROOT))
      return;
    requireActivity().addMenuProvider(new MenuProvider() {
      @Override
      public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater)
      {
        menu.add(Menu.NONE, R.id.about, Menu.NONE, R.string.about_menu_title)
            .setIcon(R.drawable.info_icon)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
      }

      @Override
      public boolean onMenuItemSelected(@NonNull MenuItem item)
      {
        if (item.getItemId() != R.id.about)
          return false;
        startActivity(new Intent(requireActivity(), com.wanderwildwood.chizu.ui.AboutActivity.class));
        return true;
      }
    }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
  }

  @Override
  public void onResume()
  {
    super.onResume();
    updateProfileSettingsPrefsSummary();
  }

  @Override
  public boolean onPreferenceTreeClick(Preference preference)
  {
    final String key = preference.getKey();
    if (key != null && key.equals(getString(R.string.pref_osm_profile)))
    {
      startActivity(new Intent(requireActivity(), ProfileActivity.class));
    }
    return super.onPreferenceTreeClick(preference);
  }

  private void updateProfileSettingsPrefsSummary()
  {
    final Preference pref = getPreference(getString(R.string.pref_osm_profile));
    if (!com.wanderwildwood.chizu.Atlas.EDITING)
    {
      // Atlas: no OpenStreetMap account, so no profile row.
      pref.setVisible(false);
      return;
    }
    if (OsmOAuth.isAuthorized())
      pref.setSummary(OsmOAuth.getUsername());
    else
      pref.setSummary(R.string.not_signed_in);
  }
}
