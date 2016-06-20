package com.kapp.youtube.model;

import android.graphics.Bitmap;
import android.net.Uri;
import android.widget.ImageView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.andexert.library.RippleView;
import com.bumptech.glide.Glide;
import com.kapp.youtube.MainApplication;
import com.kapp.youtube.Utils;

/**
 * Created by khang on 18/04/2016.
 * Email: khang.neon.1997@gmail.com
 */
public class YoutubeData implements IDisplayData {
    private static final String TAG = "YoutubeData";
    public String id, title, whoUpload, thumbnailUrl;

    public YoutubeData(String id, String title, String whoUpload, String thumbnailUrl) {
        this.id = id;
        this.title = title;
        this.whoUpload = whoUpload;
        this.thumbnailUrl = thumbnailUrl;
    }

    @Override
    public void showIconAndChangeRipple(ImageView imageView, RippleView rippleView) {
        int color = ColorGenerator.MATERIAL.getColor(getDescription());
        if (thumbnailUrl != null)
            Glide.with(MainApplication.applicationContext)
                    .load(Uri.parse(thumbnailUrl))
                    .placeholder(TextDrawable.builder().buildRect(
                            getDescription().substring(0, 2),
                            color))
                    .crossFade()
                    .into(imageView);
        else
            imageView.setImageDrawable(TextDrawable.builder().buildRect(
                    getDescription().substring(0, 2),
                    color));
        rippleView.setRippleColor(color);
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getDescription() {
        return whoUpload == null || whoUpload.length() < 2 ?
                "Unknown" : whoUpload;
    }

    public void getIconAsBitmap(Bitmap output) {
        String description = getDescription();
        int color = ColorGenerator.MATERIAL.getColor(description);
        Utils.drawableToBitmap(TextDrawable.builder().buildRect(
                description.substring(0, 2), color
        ), output);
    }
}
