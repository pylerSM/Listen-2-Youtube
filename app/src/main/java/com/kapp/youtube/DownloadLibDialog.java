package com.kapp.youtube;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import net.hockeyapp.android.metrics.MetricsManager;

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
            callback = (OnDownloadLibDialogDismiss) activity;
            final String arch = JNILib.getCPUArchitecture();
            final StorageReference bucket = FirebaseStorage.getInstance().getReference();
            dialog = new MaterialDialog.Builder(getActivity())
                    .cancelable(false)
                    .title("Load media library")
                    .content("Downloading " + arch + "-libs.zip...")
                    .progress(true, 100)
                    .progressIndeterminateStyle(false)
                    .build();
            File outputDir = getActivity().getCacheDir();
            try {
                final File outputFile = File.createTempFile("libs", "zip", outputDir);
                bucket.child(arch).child("libs.zip").getFile(outputFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                        setContent("Check md5 sum...");
                        bucket.child(arch).child("md5.txt").getBytes(Long.MAX_VALUE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
                            @Override
                            public void onSuccess(byte[] bytes) {
                                String md5 = null, getMd5 = null;
                                try {
                                    md5 = new String(bytes, "UTF-8").trim();
                                    Log.e("TAG", "onSuccess: md5 " + md5);

                                    getMd5 = getMD5Checksum(outputFile);
                                    Log.e("TAG", "onSuccess: md5 get " + getMd5);
                                    if (md5.toLowerCase().contains(getMd5)) {
                                        setContent("Unzip files...");
                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    if (unpackZip(outputFile, JNILib.getLibsFolder())) {
                                                        setContent("Checking files...");
                                                        if (JNILib.checkJNILibs()) {
                                                            toast("Load media library success.");
                                                            MetricsManager.trackEvent("LoadLibSuccess-" + arch);
                                                            exitDialog();
                                                        } else
                                                            throw new Exception("Library file incorrect. Please contact developer.");
                                                    } else
                                                        throw new Exception("Unzip error");
                                                } catch (Exception e) {
                                                    exitDialog();
                                                    logException(e, arch, "Upzip");
                                                }
                                            }
                                        }).start();

                                    } else
                                        throw new Exception("MD5 mismatch");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    exitDialog();
                                    toast(e.getMessage());
                                    MetricsManager.trackEvent("LoadLibsError-" + arch);
                                }
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                exitDialog();
                                logException(e, arch, "Download md5.txt");
                            }
                        });
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        logException(e, arch, "Download libs.zip");
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                exitDialog();
                toast("Load media library error: " + e.getMessage());
                logException(e, arch, "Create temp zip file");
            }
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return dialog;
    }

    private void logException(@NonNull Exception e, String arch, String value) {
        MetricsManager.trackEvent("LoadLibsError-" + arch);
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
        } catch (IOException e) {
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
}
