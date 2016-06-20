package com.kapp.youtube.view.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
import com.kapp.youtube.R;
import com.kapp.youtube.Utils;
import com.kapp.youtube.presenter.GetLink;
import com.kapp.youtube.presenter.GetVideoTitle;
import com.kapp.youtube.presenter.IPresenterCallback;
import com.kapp.youtube.service.DownloadService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by blackcat on 19/06/2016.
 */
public class DownloadOffline extends Activity {
    private boolean grantedPermission = true;
    private ProgressDialog fetchingUrl;

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
            String url = null;
            if (action.equals(Intent.ACTION_SEND)) {
                Uri uri = Uri.parse(getIntent().getStringExtra(Intent.EXTRA_TEXT));
                youtubeId = uri.getLastPathSegment();
                url = getIntent().getStringExtra(Intent.EXTRA_TEXT);
                Utils.logEvent("web_browser_intent");
            } else if (getIntent().getData() != null) { /* Intent.ACTION_VIEW */
                youtubeId = getIntent().getData().getQueryParameter("v");
                url = getIntent().getData().toString();
                Utils.logEvent("youtube_app_intent");
            }
            if (youtubeId != null && youtubeId.length() == Constants.YOUTUBE_ID_LENGTH) {
                Utils.logEvent("download_offline");
                fetchingUrl = ProgressDialog.show(new ContextThemeWrapper(this, R.style.AppBaseTheme),
                        "", "Fetching urls...");
                fetchingUrl.setCancelable(false);
                final String finalYoutubeId = youtubeId;
                final String finalUrl = url;
                new GetVideoTitle(0, new IPresenterCallback() {
                    @Override
                    public void onFinish(int jobType, Object result) {
                        String title = result == null ? "Unknown" : result.toString();
                        doGetLink(finalYoutubeId, finalUrl, title);
                    }
                }).execute(youtubeId);
                return;
            }
        }
        Toast.makeText(this, "Can not parse youtube url.", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void doGetLink(String videoId, final String youtubeUrl, String title) {
        new GetLink(0, new IPresenterCallback() {
            @Override
            public void onFinish(int jobType, Object result) {
                fetchingUrl.dismiss();
                if (result != null) {
                    final JSONObject jsonObject = (JSONObject) result;
                    try {
                        final String title = jsonObject.getString("title"),
                                album = jsonObject.getString("album"),
                                getLinkUrl = jsonObject.getString("getLinkUrl");
                        final JSONObject audio = jsonObject.getJSONObject("audio");
                        final List<String> items = new ArrayList<>(),
                                urls = new ArrayList<>(), extensions = new ArrayList<>();
                        urls.add(audio.getString("url"));
                        extensions.add(audio.getString("extension"));
                        items.add("Audio@mp3 - 320k");
                        items.add("Audio@" + audio.getString("extension") + " - " + audio.getString("bitrate"));
                        final JSONArray videos = jsonObject.getJSONArray("videos");
                        for (int i = 0; i < videos.length(); i++) {
                            items.add("Video@" + videos.getJSONObject(i).getString("extension")
                                    + " - " + videos.getJSONObject(i).getString("resolution"));
                            urls.add(videos.getJSONObject(i).getString("url"));
                            extensions.add(videos.getJSONObject(i).getString("extension"));
                        }
                        final WebView webView = new WebView(DownloadOffline.this);
                        webView.setWebChromeClient(new WebChromeClient());
                        webView.getSettings().setJavaScriptEnabled(true);
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
                                        dialog.dismiss();
                                        Utils.logEvent("download");
                                        if (index == 0) {
                                            final ProgressDialog convertMp3Dialog = ProgressDialog.show(new ContextThemeWrapper(DownloadOffline.this, R.style.AppBaseTheme), "", "Converting to mp3...");
                                            convertMp3Dialog.setCancelable(true);
                                            convertMp3Dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                                                @Override
                                                public void onDismiss(DialogInterface dialogInterface) {
                                                    finish();
                                                }
                                            });
                                            convertMp3Dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                                                @Override
                                                public void onCancel(DialogInterface dialogInterface) {
                                                    finish();
                                                }
                                            });
                                            webView.setWebViewClient(new WebViewClient() {
                                                @Override
                                                public void onPageFinished(WebView view, String url) {
                                                    super.onPageFinished(view, url);
                                                    if (convertMp3Dialog.isShowing() && url.contains("middle.php")) {
                                                        Uri uri = Uri.parse(url);
                                                        String server = uri.getQueryParameters("server").get(0);
                                                        String hash = uri.getQueryParameters("hash").get(0);
                                                        String file = null;
                                                        try {
                                                            file = URLEncoder.encode(uri.getQueryParameters("file").get(0), "utf-8");
                                                        } catch (UnsupportedEncodingException e) {
                                                            e.printStackTrace();
                                                        }
                                                        Log.e("convertMp3Dialog", "onPageFinished: " + Utils.getValidFileName(uri.getQueryParameters("file").get(0)));
                                                        Intent intent = new Intent(DownloadOffline.this, DownloadService.class);
                                                        intent.setAction(DownloadService.ACTION_NEW_DOWNLOAD);
                                                        intent.putExtra(DownloadService.URL, String.format("http://%s.listentoyoutube.com/download/%s/%s", server, hash, file));
                                                        intent.putExtra(DownloadService.TITLE, youtubeUrl);
                                                        intent.putExtra(DownloadService.ALBUM, album);
                                                        intent.putExtra(DownloadService.FILE_NAME, Utils.getValidFileName(file));
                                                        intent.putExtra(DownloadService.TYPE, DownloadService.TYPE_MUSIC);
                                                        startService(intent);

                                                        convertMp3Dialog.dismiss();
                                                    }
                                                }
                                            });
                                            webView.loadUrl(getLinkUrl);
                                        } else {
                                            String url = urls.get(index - 1);
                                            Intent intent = new Intent(DownloadOffline.this, DownloadService.class);
                                            intent.setAction(DownloadService.ACTION_NEW_DOWNLOAD);
                                            intent.putExtra(DownloadService.URL, url);
                                            intent.putExtra(DownloadService.TITLE, title);
                                            intent.putExtra(DownloadService.ALBUM, album);
                                            intent.putExtra(DownloadService.FILE_NAME, Utils.getValidFileName(title)
                                                    + "." + extensions.get(index - 1));
                                            intent.putExtra(DownloadService.TYPE, index == 1 ?
                                                    DownloadService.TYPE_MUSIC : DownloadService.TYPE_VIDEO);
                                            startService(intent);
                                            finish();
                                        }
                                    }
                                })
                                .negativeText("Cancel")
                                .onNegative(new MaterialDialog.SingleButtonCallback() {
                                    @Override
                                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                        dialog.dismiss();
                                        finish();
                                    }
                                }).build();
                        dialog.show();
                    } catch (JSONException e) {
                        Toast.makeText(DownloadOffline.this, "Fetch urls error.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } else {
                    Toast.makeText(DownloadOffline.this, "Fetch urls error by some reasons:\n" +
                            "- Your network connectivity\n" +
                            "- Video not available for US", Toast.LENGTH_LONG).show();
                    finish();
                }

            }
        }).execute(videoId, title, "Unknown");
    }
}
