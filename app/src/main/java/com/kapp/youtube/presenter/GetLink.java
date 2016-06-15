package com.kapp.youtube.presenter;

import android.support.annotation.NonNull;

import com.kapp.youtube.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Created by khang on 29/04/2016.
 * Email: khang.neon.1997@gmail.com
 */
public class GetLink extends BasePresenter<String, Void, JSONObject> {
    private static final String TAG = "GetLink";


    public GetLink(int jobType, @NonNull IPresenterCallback callback) {
        super(jobType, callback);
    }

    @Override
    protected JSONObject doInBackground(String... params) {
        String youtubeId = params[0];
        String title = params[1];
        String album = params[2];


        try {
            JSONObject jsonObject = Utils.getAllStreams(youtubeId);
            jsonObject.put("title", title);
            jsonObject.put("album", album);
            return jsonObject;
        } catch (JSONException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
