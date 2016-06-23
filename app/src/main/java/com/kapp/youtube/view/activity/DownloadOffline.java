package com.kapp.youtube.view.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v7.view.ContextThemeWrapper;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.kapp.youtube.Constants;
import com.kapp.youtube.MainApplication;
import com.kapp.youtube.R;
import com.kapp.youtube.Settings;
import com.kapp.youtube.Utils;
import com.kapp.youtube.presenter.GetLink;
import com.kapp.youtube.presenter.GetVideoTitle;
import com.kapp.youtube.presenter.IPresenterCallback;
import com.kapp.youtube.service.DownloadService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by blackcat on 19/06/2016.
 */
public class DownloadOffline extends Activity {
    public static final String YOUTUBE_ID = "youtube_id";
    public static final String TITLE = "title";
    private boolean grantedPermission = true;
    private ProgressDialog fetchingUrl;

    public static final String DOWNLOAD_ACTION = "com.kapp.youtube.download";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            grantedPermission = Utils.checkPermissions(this);
        if (!grantedPermission) {
            Toast.makeText(DownloadOffline.this, "Please open app and grant permission: Write storage", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String action = getIntent().getAction();
        if (action != null) {
            String youtubeId = null;
            String title = null;
            if (action.equals(Intent.ACTION_SEND)) {
                Uri uri = Uri.parse(getIntent().getStringExtra(Intent.EXTRA_TEXT));
                youtubeId = uri.getLastPathSegment();
                Utils.logEvent("web_browser_intent");
            } else if (action.equalsIgnoreCase(DOWNLOAD_ACTION)) {
                youtubeId = getIntent().getStringExtra(YOUTUBE_ID);
                title = getIntent().getStringExtra(TITLE);
            } else if (getIntent().getData() != null) { /* Intent.ACTION_VIEW */
                youtubeId = getIntent().getData().getQueryParameter("v");
                Utils.logEvent("youtube_app_intent");
            }
            if (youtubeId != null && youtubeId.length() == Constants.YOUTUBE_ID_LENGTH) {
                Utils.logEvent("download_offline");
                boolean shouldFinish = true;

                if (Settings.getDownloadChoice().equalsIgnoreCase(getString(R.string.default_download_choice))) {
                    fetchingUrl = ProgressDialog.show(new ContextThemeWrapper(this, R.style.AppBaseTheme),
                            "", "Fetching urls...");
                    fetchingUrl.setCancelable(false);
                    shouldFinish = false;
                }
                final String finalYoutubeId = youtubeId;
                final WebView webview = new WebView(DownloadOffline.this);
                final boolean finalShouldFinish = shouldFinish;
                if (title == null)
                    new GetVideoTitle(0, new IPresenterCallback() {
                        @Override
                        public void onFinish(int jobType, Object result) {
                            String title = result == null ? "Unknown " + SystemClock.currentThreadTimeMillis() : result.toString();
                            doGetLink(webview, finalYoutubeId, title, finalShouldFinish);
                        }
                    }).execute(youtubeId);
                else
                    doGetLink(webview, finalYoutubeId, title, finalShouldFinish);
                if (shouldFinish)
                    finish();
                return;
            }
        }
        Toast.makeText(this, "Can not parse youtube url.", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void doGetLink(final WebView webView, final String videoId, final String title, final boolean finished) {
        new GetLink(0, new IPresenterCallback() {
            @Override
            public void onFinish(int jobType, Object result) {
                if (fetchingUrl != null && fetchingUrl.isShowing())
                    fetchingUrl.dismiss();
                if (result != null) {
                    final JSONObject jsonObject = (JSONObject) result;
                    try {
                        final String
                                album = jsonObject.getString("album"),
                                getLinkUrl = jsonObject.getString("getLinkUrl");

                        final JSONObject audio = jsonObject.getJSONObject("audio");
                        final JSONArray videos = jsonObject.getJSONArray("videos");

                        if (Settings.getDownloadChoice().contains("Mp3")) {
                            processDownloadMp3(webView, getLinkUrl, title, album);
                        } else if (Settings.getDownloadChoice().contains("ask")) {
                            final List<String> items = new ArrayList<>(),
                                    urls = new ArrayList<>(), extensions = new ArrayList<>();
                            urls.add(audio.getString("url"));
                            extensions.add(audio.getString("extension"));
                            items.add("Audio@mp3 - 320k");
                            items.add("Audio@" + audio.getString("extension") + " - " + audio.getString("bitrate"));

                            for (int i = 0; i < videos.length(); i++) {
                                items.add("Video@" + videos.getJSONObject(i).getString("extension")
                                        + " - " + videos.getJSONObject(i).getString("resolution"));
                                urls.add(videos.getJSONObject(i).getString("url"));
                                extensions.add(videos.getJSONObject(i).getString("extension"));
                            }

                            MaterialDialog dialog = new MaterialDialog.Builder(new ContextThemeWrapper(DownloadOffline.this, R.style.AppBaseTheme))
                                    .items(items)
                                    .title("Choose download link")
                                    .positiveText("OK")
                                    .autoDismiss(false)
                                    .cancelable(false)
                                    .itemsCallbackSingleChoice(-1, new MaterialDialog.ListCallbackSingleChoice() {
                                        @Override
                                        public boolean onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                                            return true;
                                        }
                                    })
                                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                                        @Override
                                        public void onClick(@NonNull final MaterialDialog dialog, @NonNull DialogAction which) {
                                            int index = dialog.getSelectedIndex();
                                            if (dialog.isShowing())
                                                dialog.dismiss();
                                            Utils.logEvent("download");
                                            if (index == 0) {
                                                processDownloadMp3(webView, getLinkUrl, title, album);
                                            } else {
                                                String url = urls.get(index - 1);
                                                processDownload2(url, title, album, extensions.get(index - 1),
                                                        index == 1 ? DownloadService.TYPE_MUSIC : DownloadService.TYPE_VIDEO);

                                            }
                                            finish();
                                        }
                                    })
                                    .negativeText("Cancel")
                                    .onNegative(new MaterialDialog.SingleButtonCallback() {
                                        @Override
                                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                            if (dialog.isShowing())
                                                dialog.dismiss();
                                            finish();
                                        }
                                    }).build();
                            dialog.show();
                        } else {
                            if (Settings.getDownloadChoice().contains("Opus"))
                                processDownload2(audio.getString("url"), title, album, audio.getString("extension"), DownloadService.TYPE_MUSIC);
                            else if (videos.length() > 0) {
                                String downloadChoice = Settings.getDownloadChoice();
                                int index;
                                if (downloadChoice.contains("small"))
                                    index = 0;
                                else if (downloadChoice.contains("medium"))
                                    index = videos.length() / 2 + videos.length() % 2 - 1;
                                else
                                    index = videos.length() - 1;
                                JSONObject video = videos.getJSONObject(index);
                                processDownload2(video.getString("url"), title, album, video.getString("extension"), DownloadService.TYPE_VIDEO);
                            } else
                                Toast.makeText(MainApplication.applicationContext, "No video found", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(MainApplication.applicationContext, "Fetch urls error.", Toast.LENGTH_SHORT).show();
                        if (!finished)
                            finish();
                    }
                } else {
                    Toast.makeText(MainApplication.applicationContext, "Fetch urls error by some reasons:\n" +
                            "- Your network connectivity\n" +
                            "- Video not available for US", Toast.LENGTH_LONG).show();
                    if (!finished)
                        finish();
                }

            }
        }).execute(videoId, title, "Unknown");
    }

    private void processDownloadMp3(final WebView webView, String getLinkUrl, final String title, final String album) {
        webView.setWebChromeClient(new WebChromeClient());
        webView.getSettings().setJavaScriptEnabled(true);
        Toast.makeText(MainApplication.applicationContext, "Converting to mp3...", Toast.LENGTH_LONG).show();
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url.contains("middle.php")) {
                    Uri uri = Uri.parse(url);
                    String server = uri.getQueryParameters("server").get(0);
                    String hash = uri.getQueryParameters("hash").get(0);
                    String file = null;
                    try {
                        file = URLDecoder.decode(uri.getQueryParameters("file").get(0), "utf-8");
                        //file = URLEncoder.encode(file, "utf-8");
                        //file = file.replaceAll("\\+"," ");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    Log.e("convertMp3Dialog", "onPageFinished: " + Utils.getValidFileName(uri.getQueryParameters("file").get(0)));
                    Intent intent = new Intent(DownloadOffline.this, DownloadService.class);
                    intent.setAction(DownloadService.ACTION_NEW_DOWNLOAD);
                    intent.putExtra(DownloadService.URL, String.format("http://%s.listentoyoutube.com/download/%s/%s", server, hash, file));
                    intent.putExtra(DownloadService.TITLE, title);
                    intent.putExtra(DownloadService.ALBUM, album);
                    intent.putExtra(DownloadService.FILE_NAME, Utils.getValidFileName(file));
                    intent.putExtra(DownloadService.TYPE, DownloadService.TYPE_MUSIC);
                    startService(intent);
                }
            }
        });
        webView.loadUrl(getLinkUrl);
    }

    private void processDownload2(String url, String title, String album, String extension, int type) {
        Toast.makeText(MainApplication.applicationContext, "Start download " + title, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(MainApplication.applicationContext, DownloadService.class);
        intent.setAction(DownloadService.ACTION_NEW_DOWNLOAD);
        intent.putExtra(DownloadService.URL, url);
        intent.putExtra(DownloadService.TITLE, title);
        intent.putExtra(DownloadService.ALBUM, album);
        intent.putExtra(DownloadService.FILE_NAME, Utils.getValidFileName(title)
                + "." + extension);
        intent.putExtra(DownloadService.TYPE, type);
        startService(intent);
    }
}
