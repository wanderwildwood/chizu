package app.organicmaps.widget.placepage;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import app.organicmaps.R;
import app.organicmaps.base.BaseMwmFragment;
import app.organicmaps.util.ThemeUtils;
import app.organicmaps.util.WindowInsetUtils;

import java.util.Map;
import java.util.Objects;

public class WikiArticleFragment extends BaseMwmFragment
{
  public static final String EXTRA_WIKI_ARTICLE = "description";

  @SuppressWarnings("NullableProblems")
  @NonNull
  private String mDescription;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    mDescription = Objects.requireNonNull(requireArguments().getString(EXTRA_WIKI_ARTICLE));
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
  {
    View root = inflater.inflate(R.layout.fragment_place_description, container, false);
    WebView webView = root.findViewById(R.id.webview);
    WebSettings settings = webView.getSettings();
    settings.setBuiltInZoomControls(false);
    settings.setDisplayZoomControls(false);

    final String textColor = colorToCssHex(android.R.attr.textColorPrimary);
    final String textColorSecondary = colorToCssHex(android.R.attr.textColorSecondary);

    webView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.bg_app));
    webView.setVerticalScrollBarEnabled(true);
    webView.setWebViewClient(new HouseTypeClient());

    final String html = buildHtml(mDescription, textColor, textColorSecondary);
    webView.loadDataWithBaseURL(
            null,
            html,
            "text/html",
            "UTF-8",
            null
    );
    ViewCompat.setOnApplyWindowInsetsListener(root, WindowInsetUtils.PaddingInsetsListener.excludeTop());
    return root;
  }

  @NonNull
  private String buildHtml(@NonNull String content, @NonNull String textColor, @NonNull String textColorSecondary)
  {

    return "<!DOCTYPE html>" +
            "<html dir='auto'>" +
            "<head>" +
            "<meta charset='utf-8'>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0'>" +
            "<style>" +
            "html, body {" +
            "  margin: 0;" +
            "  padding: 0;" +
            "}" +
            "@font-face { font-family: 'Lato'; font-weight: 400; src: url('" + FONT_HOST + "lato_regular.ttf'); }" +
            "@font-face { font-family: 'Lato'; font-weight: 700; src: url('" + FONT_HOST + "lato_bold.ttf'); }" +
            "body {" +
            "  font-family: 'Lato', sans-serif;" +
            "  padding: 24px 20px 32px;" +
            "  color: " + textColor + ";" +
            "  line-height: 1.45;" +
            "  word-wrap: break-word;" +
            "  text-align: start;" +
            "  text-justify: inter-word;" +
            "}" +
            "p {" +
            "  margin: 0 0 16px 0;" +
            "  text-align: start;" +
            "}" +
            "h1, h2, h3 {" +
            "  margin: 24px 0 12px 0;" +
            "  line-height: 1.25;" +
            "  text-align: start;" +
            "  color: " + textColor + ";" +
            "}" +
            "img {" +
            "  max-width: 100%;" +
            "  height: auto;" +
            "}" +
            "a {" +
            "  text-decoration: none;" +
            "}" +
            ".source {" +
            "  margin-top: 24px;" +
            "  color: " + textColorSecondary + ";" +
            "  text-align: start;" +
            "}" +
            "</style>" +
            "</head>" +
            "<body>" +
            content +
            "<p class='source'><b>wikipedia.org</b></p>" +
            "</body>" +
            "</html>";
  }

  // Atlas: the page is set in Lato like the rest of the app. The two font files are the app's
  // own font resources, handed to the page under a made-up address that never reaches a network.
  private static final String FONT_HOST = "https://font.atlas.invalid/";

  private class HouseTypeClient extends WebViewClient
  {
    @Nullable
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request)
    {
      final String url = request.getUrl().toString();
      if (!url.startsWith(FONT_HOST))
        return null;
      final int font = url.endsWith("lato_bold.ttf") ? R.font.lato_bold : R.font.lato_regular;
      return new WebResourceResponse("font/ttf", null, 200, "OK", Map.of("Access-Control-Allow-Origin", "*"),
                                     view.getResources().openRawResource(font));
    }

    // With a client set, a link would open inside this page; it goes to the browser as before.
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request)
    {
      try
      {
        startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
      }
      catch (ActivityNotFoundException ignored)
      {
        // No app for the link: the page stays as it is.
      }
      return true;
    }
  }

  private String colorToCssHex(int colorRes)
  {
    final int color = ThemeUtils.getColor(requireContext(), colorRes);
    // Convert Android color int (0xAARRGGBB) to CSS hex with alpha (#RRGGBBAA)
    final int rgb = color & 0x00FFFFFF;
    final int alpha = (color >>> 24) & 0xFF;
    return String.format("#%08X", (rgb << 8) | alpha);
  }
}
