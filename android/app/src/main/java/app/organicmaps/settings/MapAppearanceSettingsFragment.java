package app.organicmaps.settings;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import app.organicmaps.R;
import app.organicmaps.editor.MapLanguagesFragment;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.editor.data.Language;
import app.organicmaps.sdk.settings.MapLanguageCode;
import app.organicmaps.sdk.util.Config;
import app.organicmaps.sdk.util.PowerManagment;
import app.organicmaps.util.Utils;

import java.util.Locale;

@Keep
public class MapAppearanceSettingsFragment extends BaseXmlSettingsFragment implements MapLanguagesFragment.Listener
{
  @Override
  protected int getXmlResources()
  {
    return R.xml.prefs_map_appearance;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);

    init3dModePrefsCallbacks();
    initLargeFontSizePrefsCallbacks();
    initTransliterationPrefsCallbacks();
    initAlternativeMapLanguageHandlingCallbacks();
    initBookmarksTextPlacementPrefsCallbacks();
    // Atlas: the map is flat on E Ink, so there is no switch for 3D buildings.
    getPreference(getString(R.string.pref_3d_buildings)).setVisible(false);
  }

  @Override
  public void onResume()
  {
    super.onResume();
    updateMapLanguageCodeSummary();
  }

  @Override
  public boolean onPreferenceTreeClick(Preference preference)
  {
    final String key = preference.getKey();
    if (key == null)
      return super.onPreferenceTreeClick(preference);

    if (key.equals(getString(R.string.pref_map_locale)))
    {
      MapLanguagesFragment langFragment = (MapLanguagesFragment) getSettingsActivity().stackFragment(
          MapLanguagesFragment.class, getString(R.string.change_map_locale), null);
      langFragment.setListener(this);
    }
    return super.onPreferenceTreeClick(preference);
  }

  @Override
  public void onMapLanguageSelected(Language language)
  {
    MapLanguageCode.setMapLanguageCode(language.code);
    getSettingsActivity().onBackPressed();
  }

  private void updateMapLanguageCodeSummary()
  {
    final Preference pref = getPreference(getString(R.string.pref_map_locale));
    String mapLanguageCode = MapLanguageCode.getMapLanguageCode();
    if (mapLanguageCode.equals(Language.AUTO_LANG_CODE))
      pref.setSummary(R.string.auto);
    else if (mapLanguageCode.equals(Language.DEFAULT_LANG_CODE))
      pref.setSummary(R.string.pref_maplanguage_local);
    else
      pref.setSummary(new Locale(mapLanguageCode).getDisplayLanguage());
  }

  private void initLargeFontSizePrefsCallbacks()
  {
    // Atlas: a minus and a plus, a quarter at a time from 100% to 400%, where a slider stood.
    final com.wanderwildwood.chizu.ui.StepperPreference pref =
        getPreference(getString(R.string.pref_font_scale_factor));
    final Runnable show = () -> {
      final int quarters = (int) Math.round(Config.getFontScaleFactor() * 4.0);
      pref.setSummary(quarters * 25 + "%");
      pref.setEnds(quarters > 4, quarters < 16);
    };
    pref.setListener(delta -> {
      final int old = (int) Math.round(Config.getFontScaleFactor() * 4.0);
      final int quarters = Math.max(4, Math.min(16, old + delta));
      if (quarters != old)
        Config.setFontScaleFactor(quarters / 4.0);
      show.run();
    });
    show.run();
  }

  private void initAlternativeMapLanguageHandlingCallbacks()
  {
    final ListPreference pref = getPreference(getString(R.string.pref_alt_map_lang_handling_key));
    pref.setValue(String.valueOf(Config.getAlternativeMapLanguageHandling()));
    pref.setSummary(pref.getEntry());
    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      final int alternativeMapLanguageHandling = Integer.parseInt((String) newValue);
      Config.setAlternativeMapLanguageHandling(alternativeMapLanguageHandling);
      preference.setSummary(pref.getEntries()[alternativeMapLanguageHandling]);
      return true;
    });
  }

  private void initBookmarksTextPlacementPrefsCallbacks()
  {
    final Preference pref = getPreference(getString(R.string.pref_bookmarks_text_placement));
    ((TwoStatePreference) pref).setChecked(Framework.nativeGetShowBookmarkLabels());
    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      final boolean oldVal = Framework.nativeGetShowBookmarkLabels();
      final boolean newVal = (Boolean) newValue;
      if (oldVal != newVal)
        Framework.nativeSetShowBookmarkLabels(newVal);
      return true;
    });
  }


  private void initTransliterationPrefsCallbacks()
  {
    final Preference pref = getPreference(getString(R.string.pref_transliteration));
    ((TwoStatePreference) pref).setChecked(Config.isTransliteration());
    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      final boolean oldVal = Config.isTransliteration();
      final boolean newVal = (Boolean) newValue;
      if (oldVal != newVal)
        Config.setTransliteration(newVal);
      return true;
    });
  }

  private void init3dModePrefsCallbacks()
  {
    final TwoStatePreference pref = getPreference(getString(R.string.pref_3d_buildings));
    final Framework.Params3dMode _3d = new Framework.Params3dMode();
    Framework.nativeGet3dMode(_3d);

    // Check power management: high-power mode disables 3D buildings
    @PowerManagment.SchemeType int powerScheme = PowerManagment.getScheme();
    if (powerScheme == PowerManagment.HIGH)
    {
      pref.setShouldDisableView(true);
      pref.setEnabled(false);
      pref.setSummary(getString(R.string.pref_map_3d_buildings_disabled_summary));
      pref.setChecked(false);
    }
    else
    {
      pref.setShouldDisableView(false);
      pref.setEnabled(true);
      pref.setSummary("");
      pref.setChecked(_3d.buildings);
    }

    pref.setOnPreferenceChangeListener((preference, newValue) -> {
      Framework.Params3dMode current = new Framework.Params3dMode();
      Framework.nativeGet3dMode(current);
      Framework.nativeSet3dMode(current.enabled, (Boolean) newValue);
      return true;
    });
  }
}
