package com.cylonid.nativealpha;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.WebApp;
import com.cylonid.nativealpha.util.Const;
import com.cylonid.nativealpha.util.NotificationUtils;
import com.cylonid.nativealpha.util.ShortcutIconUtils;
import com.cylonid.nativealpha.util.WebViewLauncher;
import com.google.android.material.snackbar.Snackbar;
import com.mikhaellopez.circularprogressbar.CircularProgressBar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static androidx.appcompat.app.AppCompatActivity.RESULT_OK;
import static com.cylonid.nativealpha.util.Const.CODE_OPEN_FILE;

enum IconFetchResult {
    FAVICON(0),
    TITLE(1),
    NEW_BASEURL(2);

    public final int index;
    IconFetchResult(int index) {
        this.index = index;
    }

}

public class ShortcutDialogFragment extends DialogFragment  {

    private static final String ARG_WEBAPP_ID = "webapp_id";
    private static final String STATE_TITLE = "shortcut_title";

    private WebApp webapp;
    private String base_url;
    private Bitmap bitmap;
    private ImageView uiFavicon;
    private CircularProgressBar uiProgressBar;
    private EditText uiTitle;
    private Thread faviconFetcherThread;
    private CountDownTimer faviconFetchTimer;
    private String restoredTitle;

    public ShortcutDialogFragment() {}

    public static ShortcutDialogFragment newInstance(WebApp webapp) {
        ShortcutDialogFragment frag = new ShortcutDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_WEBAPP_ID, webapp.getID());
        frag.setArguments(args);
        frag.webapp = webapp;
        frag.base_url = webapp.getBaseUrl();
        frag.bitmap = null;

        return frag;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (webapp == null && getArguments() != null
                && getArguments().containsKey(ARG_WEBAPP_ID)) {
            int appId = getArguments().getInt(ARG_WEBAPP_ID);
            DataManager manager = DataManager.getInstance();
            manager.loadAppData();
            // Resolve by identity: a deleted app must never fall back to a list position.
            for (WebApp candidate : manager.getWebsites()) {
                if (candidate != null && candidate.getID() == appId) {
                    webapp = candidate;
                    break;
                }
            }
        }
        if (webapp != null) {
            base_url = webapp.getBaseUrl();
            bitmap = ShortcutIconUtils.loadFavicon(requireContext(), webapp.getID());
        }
        if (savedInstanceState != null) {
            restoredTitle = savedInstanceState.getString(STATE_TITLE);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (uiTitle != null) {
            outState.putString(STATE_TITLE, uiTitle.getText().toString());
        }
    }

    private void stopFaviconFetching() {
        if (faviconFetchTimer != null) {
            faviconFetchTimer.cancel();
            faviconFetchTimer = null;
        }
        if (faviconFetcherThread != null) {
            faviconFetcherThread.interrupt();
            faviconFetcherThread = null;
            Log.d("CLEANUP", "Stopped running faviconfetcher");
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        stopFaviconFetching();
        super.onDismiss(dialog);
    }

    @Override
    public void onDestroyView() {
        stopFaviconFetching();
        uiTitle = null;
        uiFavicon = null;
        uiProgressBar = null;
        super.onDestroyView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CODE_OPEN_FILE && resultCode == RESULT_OK
                && data != null && data.getData() != null && isAdded() && uiFavicon != null) {
            Uri uri = data.getData();
            try {
                bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), uri);
                if (bitmap != null) {
                    stopFaviconFetching();
                    applyNewBitmapToDialog();
                }

            }
            catch(IOException | SecurityException e) {
                NotificationUtils.showToast(requireActivity(), getString(R.string.icon_not_found), Toast.LENGTH_SHORT);
                e.printStackTrace();
            }
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        if (webapp == null) {
            return new AlertDialog.Builder(requireActivity())
                    .setMessage(R.string.webapp_not_found)
                    .setPositiveButton(android.R.string.ok, null)
                    .create();
        }

        final View view = getLayoutInflater().inflate(R.layout.shortcut_dialog, null);

        final AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setView(view)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel,  (dialog1, which) -> {
                    dismiss();
                })
                .create();

        uiTitle = view.findViewById(R.id.websiteTitle);
        uiFavicon = view.findViewById(R.id.favicon);
        uiProgressBar = view.findViewById(R.id.circularProgressBar);
        uiTitle.setText(restoredTitle != null ? restoredTitle : webapp.getTitle());

        Button btnCustomIcon = view.findViewById(R.id.btnCustomIcon);
        btnCustomIcon.setOnClickListener(v -> {

            stopFaviconFetching();
            uiProgressBar.setVisibility(View.GONE);
            uiFavicon.setVisibility(View.VISIBLE);

            if (uiTitle.getText().toString().equals(""))
                setShortcutTitle(webapp.getTitle());

            Intent intent = new Intent().setType("image/*").setAction(Intent.ACTION_GET_CONTENT);
            try {
                startActivityForResult(Intent.createChooser(intent, "Select an icon"), CODE_OPEN_FILE);
            } catch (android.content.ActivityNotFoundException e) {
                NotificationUtils.showInfoSnackbar(requireActivity(), getString(R.string.no_filemanager), Snackbar.LENGTH_LONG);
                e.printStackTrace();
            }
        });
        dialog.setOnShowListener(dialog12 -> {
            if (!isAdded() || uiTitle == null) return;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (addShortcutToHomeScreen(bitmap)) dismiss();
            });
            if (bitmap != null) applyNewBitmapToDialog();
            else startFaviconFetching();
        });

        return dialog;
    }

    private Bitmap loadBitmap(String strUrl)  {
        Bitmap bitmap;
        try {
            URL url = new URL(strUrl);
            HttpURLConnection con = (HttpURLConnection)url.openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            InputStream is = con.getInputStream();
            bitmap = BitmapFactory.decodeStream(is);
            if (bitmap == null || bitmap.getWidth() < Const.FAVICON_MIN_WIDTH)
                return null;

        } catch (Exception e) {
            bitmap = null;
            e.printStackTrace();
        }
        return bitmap;
    }
    private TreeMap<Integer, String> buildIconMap() {
        TreeMap<Integer, String> found_icons = new TreeMap<>();
        if(base_url == null || base_url.equals("")) return found_icons;
        String host_part = base_url.replace("http://", "").replace("https://", "").replace("www.", "");

        //No suitable icon

        if (host_part.startsWith("amazon."))
            found_icons.put(300, "https://upload.wikimedia.org/wikipedia/commons/d/de/Amazon_icon.png");

        if (host_part.startsWith("paypal."))
            found_icons.put(196, "https://www.paypalobjects.com/webstatic/icon/pp196.png");

        if (host_part.startsWith("google."))
            found_icons.put(240, "https://www.gstatic.com/images/branding/googleg/2x/googleg_standard_color_120dp.png");

        // Size doesn't fit
        if (host_part.startsWith("anchor.fm"))
            found_icons.put(Integer.MAX_VALUE, "https://d12xoj7p9moygp.cloudfront.net/favicon/apple-touch-icon-wave-152x152.png");

        //OEBB has a typo in its web manifest
        if (host_part.startsWith("oebb.at"))
            found_icons.put(Integer.MAX_VALUE, "https://www.oebb.at/.resources/pv-2017/themes/images/favicons/android-chrome-192x192.png");


        //Path in PWA manifest is HTTP
        if (host_part.startsWith("oe3.orf.at"))
            found_icons.put(Integer.MAX_VALUE, "https://tubestatic.orf.at/mojo/1_3/storyserver//tube/common/images/apple-icons/oe3.png");


        return found_icons;
    }

    public String[] fetchWebappData() {
        String[] result = new String[] {null, null, null};
        TreeMap<Integer, String> found_icons = buildIconMap();

        try {
            //Connect to the website
            Document doc = Jsoup.connect(base_url).ignoreHttpErrors(true).userAgent(Const.DESKTOP_USER_AGENT).followRedirects(true).timeout(5000).get();

            //Step 1: Check for META Redirect
            Elements metaTags = doc.select("meta[http-equiv=refresh]");
            if (!metaTags.isEmpty()) {
                Element metaTag = metaTags.first();
                String content = metaTag.attr("content");
                Pattern pattern = Pattern.compile(".*URL='?(.*)$", Pattern.CASE_INSENSITIVE);
                Matcher m = pattern.matcher(content);
                String redirectUrl = m.matches() ? m.group(1) : null;
                if (redirectUrl != null) {
                    base_url = redirectUrl;
                    doc = Jsoup.connect(base_url).followRedirects(true).timeout(5000).get();
                }
            }
            //Step 2: Check PWA manifest
            Elements manifest = doc.select("link[rel=manifest]");
            if (!manifest.isEmpty()) {
                Element mf = manifest.first();
                String data = Jsoup.connect(mf.absUrl("href")).ignoreContentType(true).timeout(5000).execute().body();
                JSONObject json = new JSONObject(data);

                try {
                    result[IconFetchResult.TITLE.index] = json.getString("name");
                    String start_url = json.getString("start_url");
                    if (!start_url.isEmpty()) {
                        URL base_url = new URL(mf.absUrl("href"));
                        URL fl_url = new URL(base_url, start_url);
                        result[IconFetchResult.NEW_BASEURL.index] = fl_url.toString();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                try {
                    JSONArray manifest_icons = json.getJSONArray("icons");

                    for (int i = 0; i < manifest_icons.length(); i++) {
                        String icon_href = manifest_icons.getJSONObject(i).getString("src");
                        String sizes = manifest_icons.getJSONObject(i).getString("sizes");
                        Integer width = ShortcutIconUtils.getWidthFromIcon(sizes);
                        URL base_url = new URL(mf.absUrl("href"));
                        URL full_url = new URL(base_url, icon_href);
                        found_icons.put(width, full_url.toString());
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            //Step 3: Fallback to PNG icons
            if (found_icons.isEmpty()) {

                Elements html_title = doc.select("title");
                if (!html_title.isEmpty())
                    result[IconFetchResult.TITLE.index] = html_title.first().text();

                Elements icons = doc.select("link[rel=icon]");
                icons.addAll(doc.select("link[rel=shortcut icon]"));
                //If necessary, use apple icons
                if (icons.size() < 3) {
                    Elements apple_icons = doc.select("link[rel=apple-touch-icon]");
                    Elements apple_icons_prec = doc.select("link[rel=apple-touch-icon-precomposed]");
                    icons.addAll(apple_icons);
                    icons.addAll(apple_icons_prec);
                }

                for (Element icon : icons) {
                    String icon_href = icon.absUrl("href");
                    String sizes = icon.attr("sizes");
                    if (!sizes.equals("")) {
                        Integer width = ShortcutIconUtils.getWidthFromIcon(sizes);
                        found_icons.put(width, icon_href);
                    } else
                        found_icons.put(1, icon_href);

                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!found_icons.isEmpty()) {
            Map.Entry<Integer, String> best_fit = found_icons.lastEntry();
            result[IconFetchResult.FAVICON.index] = best_fit.getValue();

        }

        return result;
    }

    private void startFaviconFetching() {

        stopFaviconFetching();
        final FragmentActivity activity = requireActivity();
        final String initialTitle = uiTitle.getText().toString();
        faviconFetcherThread = new Thread(() -> {
            Thread worker = Thread.currentThread();
            String[] webappdata = fetchWebappData();
            if (worker.isInterrupted()) return;
            Bitmap fetchedBitmap = loadBitmap(webappdata[IconFetchResult.FAVICON.index]);
            activity.runOnUiThread(() -> {
                // An interrupted request can still finish its network call after recreation.
                if (faviconFetcherThread != worker || !isAdded() || uiTitle == null) return;
                stopFaviconFetching();
                bitmap = fetchedBitmap;
                boolean titleUnchanged = restoredTitle == null
                        && initialTitle.equals(uiTitle.getText().toString());
                applyNewBitmapToDialog();
                if (titleUnchanged) {
                    setShortcutTitle(webappdata[IconFetchResult.TITLE.index]);
                }
                applyNewBaseUrl(webappdata[IconFetchResult.NEW_BASEURL.index]);
            });
        });
        faviconFetcherThread.start();

        faviconFetchTimer = new CountDownTimer(5000, 5000) {
            @Override
            public void onTick(long millisUntilFinished) { }
            public void onFinish() {
                stopFaviconFetching();
                if (isAdded() && uiTitle != null) prepareFailedUI();
            }
        }.start();

    }


    private boolean addShortcutToHomeScreen(Bitmap bitmap) {
        try {
            Intent intent = WebViewLauncher.createWebViewIntent(webapp, requireActivity());
            if (intent == null) {
                NotificationUtils.showToast(requireActivity(), getString(R.string.webview_activity_launch_failed));
                return false;
            }
            IconCompat icon = bitmap != null ? IconCompat.createWithBitmap(bitmap)
                    : IconCompat.createWithResource(requireActivity(), R.mipmap.ic_shellalpha_fg);
            ShortcutInfoCompat shortcut = buildShortcutInfo(requireContext(), webapp,
                    uiTitle.getText().toString(), intent, icon);
            if (shortcut != null && pinOrUpdateShortcut(requireContext(), shortcut)) return true;
        } catch (RuntimeException e) {
            Log.e("ShortcutDialog", "Could not save shortcut", e);
        }
        NotificationUtils.showToast(requireActivity(), getString(R.string.shortcut_save_failed));
        return false;
    }

    @Nullable
    static ShortcutInfoCompat buildShortcutInfo(Context context, WebApp app,
            @Nullable String enteredLabel, @Nullable Intent launchIntent, IconCompat icon) {
        if (launchIntent == null) return null;
        String label = enteredLabel == null ? "" : enteredLabel.trim();
        if (label.isEmpty()) label = app.getTitle() == null ? "" : app.getTitle().trim();
        if (label.isEmpty()) label = context.getString(R.string.app_name).trim();
        if (label.isEmpty()) return null;

        return new ShortcutInfoCompat.Builder(context, "lite-app-" + app.getID())
                .setIcon(icon)
                .setShortLabel(label)
                .setLongLabel(label)
                .setIntent(launchIntent)
                .build();
    }

    static boolean pinOrUpdateShortcut(Context context, ShortcutInfoCompat shortcut) {
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null) return false;
        for (ShortcutInfo existing : manager.getPinnedShortcuts()) {
            if (!existing.getId().equals(shortcut.getId())) continue;
            Intent existingIntent = existing.getIntent();
            // A legacy title can occupy the stable ID of a different app.
            // Leave any shortcut without an unambiguous owner untouched.
            if (existingIntent == null || !existingIntent.hasExtra(Const.INTENT_WEBAPPID)
                    || !shortcut.getId().equals("lite-app-" +
                            existingIntent.getIntExtra(Const.INTENT_WEBAPPID, -1))) {
                return false;
            }
            if (!existing.isEnabled()) {
                manager.enableShortcuts(Collections.singletonList(shortcut.getId()));
            }
            return ShortcutManagerCompat.updateShortcuts(context, Collections.singletonList(shortcut));
        }
        // Do not identify, delete, or migrate legacy shortcuts by their display title.
        return ShortcutManagerCompat.isRequestPinShortcutSupported(context)
                && ShortcutManagerCompat.requestPinShortcut(context, shortcut, null);
    }

    private void prepareFailedUI() {
        showFailedMessage();
        if(uiTitle.getText().toString().trim().isEmpty() && webapp.getTitle() != null) {
          uiTitle.setText(webapp.getTitle());
        }

        uiTitle.requestFocus();

        uiProgressBar.setVisibility(View.GONE);
        uiFavicon.setVisibility(View.VISIBLE);
    }
    private void showFailedMessage() {
        String title = webapp != null && webapp.getTitle() != null ? webapp.getTitle() : "";
        NotificationUtils.showToast(requireActivity(), getString(R.string.icon_fetch_failed_line1, title) + getString(R.string.icon_fetch_failed_line2) + getString(R.string.icon_fetch_failed_line3));
    }

    private void setShortcutTitle(String shortcut_title) {
        if (shortcut_title != null) {
            if (!shortcut_title.equals(""))
                uiTitle.setText(shortcut_title);

        }
        else {
            uiTitle.setText(webapp.getTitle());
        }
        uiTitle.requestFocus();
    }

    private void applyNewBaseUrl(String url) {
        if (url != null) {
            webapp.setBaseUrl(url);
            DataManager.getInstance().saveWebAppData();
        }

    }

    private void applyNewBitmapToDialog() {
        if (bitmap == null) {
            prepareFailedUI();
            return;
        }
        ShortcutIconUtils.saveFavicon(requireContext(), webapp.getID(), bitmap);
        uiFavicon.setImageBitmap(bitmap);
        uiProgressBar.setVisibility(View.GONE);
        uiFavicon.setVisibility(View.VISIBLE);


    }

}
