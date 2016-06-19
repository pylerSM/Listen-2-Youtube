package com.kapp.youtube.presenter;

import android.support.annotation.NonNull;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.kapp.youtube.Constants;

import java.io.IOException;

/**
 * Created by blackcat on 19/06/2016.
 */
public class GetVideoTitle extends BasePresenter<String, Void, String> {

    private final YouTube youtube;

    public GetVideoTitle(int jobType, @NonNull IPresenterCallback callback) {
        super(jobType, callback);
        youtube = new YouTube.Builder(new NetHttpTransport(), new JacksonFactory(), new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest request) throws IOException {
                request.setConnectTimeout(10 * 1000);
            }
        }).build();
    }

    @Override
    protected String doInBackground(String... strings) {
        String videoId = strings[0];
        try {
            YouTube.Videos.List video = youtube.videos().list("snippet");
            video.setKey(Constants.API_KEY);
            video.setId(videoId);
            video.setFields("items(snippet/title)");
            return video.execute().getItems().get(0).getSnippet().getTitle();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
