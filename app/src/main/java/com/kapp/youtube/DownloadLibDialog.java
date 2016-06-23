package com.kapp.youtube;

import android.app.Activity;
import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.stericson.RootTools.RootTools;
import com.thin.downloadmanager.DownloadRequest;
import com.thin.downloadmanager.DownloadStatusListenerV1;
import com.thin.downloadmanager.ThinDownloadManager;

import org.videolan.libvlc.util.JNILib;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by blackcat on 17/06/2016.
 */
public class DownloadLibDialog extends DialogFragment {
    public interface OnDownloadLibDialogDismiss {
        void onDismiss(DownloadLibDialog dialog);
    }

    private MaterialDialog dialog;
    private OnDownloadLibDialogDismiss callback;

    public DownloadLibDialog() {
        setRetainInstance(true);
        Utils.logEvent("download_library");
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (dialog == null) {
            //Log.e("dialog", "onAttach: download=============================");
            callback = (OnDownloadLibDialogDismiss) activity;
            final String arch = JNILib.getCPUArchitecture();
            if (arch.contains("64") && (!RootTools.isRootAvailable() || !RootTools.isAccessGiven())) {
                dialog = new MaterialDialog.Builder(activity)
                        .title("Require Root access")
                        .content("Detect device " + arch + " but the app was not granted root access permission.\n" +
                                "Please re-open and try again when you ready!")
                        .cancelable(false)
                        .negativeText("OK")
                        .onAny(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                exitDialog();
                            }
                        })
                        .autoDismiss(false)
                        .build();
            } else {
                dialog = new MaterialDialog.Builder(getActivity())
                        .cancelable(false)
                        .title("Load media library")
                        .content("Downloading " + arch + "-libs.zip...")
                        .progress(false, 100)
                        .progressIndeterminateStyle(false)
                        .build();
                final File outputDir = getActivity().getCacheDir();
                try {
                    final File outputFile = File.createTempFile("libs", "zip", outputDir);
                    doDownloadZip(arch, Uri.parse(Constants.HOSTING_SERVER + arch + "/libs.zip"), outputFile);
                } catch (IOException e) {
                    e.printStackTrace();
                    exitDialog();
                    logException(e.toString(), arch, "Create temp zip file");
                }
            }
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return dialog;
    }

    private void logException(String e, String arch, String w) {
        Utils.logEvent("down_lib_err-" + arch);
        toast("Error while: " + w + "\nPlease try later.\n" + e);
    }


    private void toast(final String message) {
        if (getActivity() != null)
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void setContent(final String content) {
        if (getActivity() != null)
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (dialog != null)
                        dialog.setContent(content);
                }
            });
    }

    public void exitDialog() {
        if (getActivity() != null)
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dialog.dismiss();
                    callback.onDismiss(DownloadLibDialog.this);
                }
            });
    }

    @Override
    public void dismiss() {
        if (getDialog() != null && getDialog().isShowing() && isResumed()) {
            try {
                super.dismiss();
            } catch (IllegalArgumentException e) {
                Log.e("DownloadLibDialog", "Error dismissing");
            }
        }
    }

    private byte[] createChecksum(File filename) throws Exception {
        InputStream fis = new FileInputStream(filename);

        byte[] buffer = new byte[1024];
        MessageDigest complete = MessageDigest.getInstance("MD5");
        int numRead;

        do {
            numRead = fis.read(buffer);
            if (numRead > 0) {
                complete.update(buffer, 0, numRead);
            }
        } while (numRead != -1);

        fis.close();
        return complete.digest();
    }

    private String getMD5Checksum(File filename) throws Exception {
        byte[] b = createChecksum(filename);
        String result = "";

        for (byte aB : b) {
            result += Integer.toString((aB & 0xff) + 0x100, 16).substring(1);
        }
        return result;
    }

    private boolean unpackZip(File zipFile, File outputDir) {
        InputStream is;
        ZipInputStream zis;
        try {
            outputDir.mkdirs();
            String filename;
            is = new FileInputStream(zipFile);
            zis = new ZipInputStream(new BufferedInputStream(is));
            ZipEntry ze;
            byte[] buffer = new byte[1024];
            int count;

            while ((ze = zis.getNextEntry()) != null) {
                // zapis do souboru
                filename = ze.getName();

                // Need to create directories if not exists, or
                // it will generate an Exception...
                if (ze.isDirectory()) {
                    File fmd = new File(outputDir, filename);
                    fmd.mkdirs();
                    continue;
                }
                File of = new File(outputDir, filename);
                FileOutputStream fout = new FileOutputStream(of);

                // cteni zipu a zapis
                while ((count = zis.read(buffer)) != -1) {
                    fout.write(buffer, 0, count);
                }

                fout.close();
                zis.closeEntry();
            }

            zis.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    @Override
    public void onDestroyView() {
        if (getDialog() != null && getRetainInstance())
            getDialog().setDismissMessage(null);
        super.onDestroyView();
    }

    private void doDownloadZip(final String arch, Uri uri, final File dest) {
        ThinDownloadManager downloadManager = new ThinDownloadManager(new Handler());
        DownloadRequest downloadRequest = new DownloadRequest(uri);
        downloadRequest.setDestinationURI(Uri.fromFile(dest))
                .setDownloadContext(getActivity().getApplicationContext())
                .setPriority(DownloadRequest.Priority.HIGH)
                .setStatusListener(new DownloadStatusListenerV1() {
                    int lastProcess = -1;

                    @Override
                    public void onDownloadComplete(DownloadRequest downloadRequest) {
                        dialog.getProgressBar().setIndeterminate(true);
                        doCheckSumAndUnzip(arch, dest);
                    }

                    @Override
                    public void onDownloadFailed(DownloadRequest downloadRequest, int errorCode, String errorMessage) {
                        logException(errorMessage, arch, "Download library");
                        exitDialog();
                    }

                    @Override
                    public void onProgress(DownloadRequest downloadRequest, long totalBytes, long downloadedBytes, int progress) {
                        if (lastProcess != progress && dialog != null && dialog.isShowing()) {
                            try {
                                dialog.setProgress(progress);
                            } catch (Exception ignore) {
                            }
                            lastProcess = progress;
                        }
                    }
                });
        downloadManager.add(downloadRequest);
    }

    private void doCheckSumAndUnzip(final String arch, final File dest) {
        setContent("Check md5 sum...");
        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder()
                .url(Constants.HOSTING_SERVER + arch + "/md5.txt")
                .build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                exitDialog();
                logException(e.toString(), arch, "Download md5.txt");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String md5FromServer = response.body().string();
                try {
                    String md5FromFile = getMD5Checksum(dest);
                    Log.e("TAG", "onSuccess: md5 get " + md5FromFile);
                    if (md5FromServer.toLowerCase().contains(md5FromFile)) {
                        setContent("Unzip files...");
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (unpackZip(dest, JNILib.getLibsFolder())) {
                                        try {
                                            dest.delete();
                                        } catch (Exception ignored) {
                                        }
                                        setContent("Checking files...");
                                        if (arch.contains("64")) {
                                            String dataDir = MainApplication.applicationContext.getApplicationInfo().dataDir;
                                            String dir = "/data/app/com.kapp.youtube-1/lib/"
                                                    + (arch.contains("arm") ? "arm64/" : "x86_64/");
                                            int success = 0;
                                            if (RootTools.copyFile(JNILib.getLibPath(JNILib.VLC),
                                                    dir + "libvlc.so",
                                                    true, false))
                                                success++;
                                            if (RootTools.copyFile(JNILib.getLibPath(JNILib.VLC_JNI),
                                                    dir + "libvlcjni.so",
                                                    true, false))
                                                success++;

                                            if (RootTools.copyFile(JNILib.getLibPath(JNILib.VLC),
                                                    dataDir + "/lib/libvlc.so",
                                                    true, false))
                                                success++;
                                            if (RootTools.copyFile(JNILib.getLibPath(JNILib.VLC_JNI),
                                                    dataDir + "/lib/libvlcjni.so",
                                                    true, false))
                                                success++;

                                            if (success >= 2) {
                                                File flag = JNILib.getFlagFile();
                                                if (!flag.exists())
                                                    flag.createNewFile();
                                                new File(JNILib.getLibPath(JNILib.VLC)).delete();
                                                new File(JNILib.getLibPath(JNILib.VLC_JNI)).delete();
                                                toast("Load media library success.");
                                                Utils.logEvent("down_libs_scc-" + arch);
                                                exitDialog();
                                            } else
                                                throw new Exception("Copy file library folder error");
                                        } else {
                                            if (JNILib.checkJNILibs()) {
                                                toast("Load media library success.");
                                                Utils.logEvent("down_libs_scc-" + arch);
                                                exitDialog();
                                            } else
                                                throw new Exception("Library file incorrect. Please contact developer.");
                                        }
                                    } else {
                                        dest.delete();
                                        throw new Exception("Unzip error");
                                    }
                                } catch (Exception e) {
                                    exitDialog();
                                    logException(e.toString(), arch, "Upzip");
                                }
                            }
                        }).start();

                    } else
                        throw new Exception("MD5 mismatch");
                } catch (Exception e) {
                    exitDialog();
                    logException(e.toString(), arch, "Processing library files");
                }

            }
        });
    }
}
