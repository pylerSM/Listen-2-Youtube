package com.kapp.youtube.view.activity;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.florent37.materialviewpager.MaterialViewPager;
import com.github.florent37.materialviewpager.MaterialViewPagerAnimator;
import com.github.florent37.materialviewpager.MaterialViewPagerHelper;
import com.github.florent37.materialviewpager.header.HeaderDesign;
import com.kapp.youtube.Constants;
import com.kapp.youtube.DownloadLibDialog;
import com.kapp.youtube.R;
import com.kapp.youtube.Settings;
import com.kapp.youtube.Utils;
import com.kapp.youtube.model.LocalFileData;
import com.kapp.youtube.model.PlayListData;
import com.kapp.youtube.model.YoutubeData;
import com.kapp.youtube.presenter.FetchLocalFileList;
import com.kapp.youtube.presenter.FetchPlayList;
import com.kapp.youtube.presenter.FetchRelatedVideo;
import com.kapp.youtube.presenter.IPresenterCallback;
import com.kapp.youtube.presenter.YoutubeQuery;
import com.kapp.youtube.service.PlaybackService;
import com.kapp.youtube.view.adapter.LocalFileAdapter;
import com.kapp.youtube.view.adapter.PlayListAdapter;
import com.kapp.youtube.view.adapter.SearchOnlineAdapter;
import com.kapp.youtube.view.fragment.RecyclerViewFragment;
import com.lapism.searchview.view.SearchCodes;
import com.lapism.searchview.view.SearchView;

import net.hockeyapp.android.CrashManager;
import net.hockeyapp.android.FeedbackManager;
import net.hockeyapp.android.Tracking;
import net.hockeyapp.android.UpdateManager;
import net.hockeyapp.android.metrics.MetricsManager;

import org.videolan.libvlc.util.JNILib;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, ViewPager.OnPageChangeListener,
        IPresenterCallback, SearchView.OnQueryTextListener, DownloadLibDialog.OnDownloadLibDialogDismiss {
    public static final String SEARCH_ONLINE_TITLE = "Search Online", LOCAL_FILE_TITLE = "Downloaded files",
            PLAY_LISTS = "Play lists";
    public static final String SEARCH_VIEW_KEY = "Search View", DRAWER_OPENED_KEY = "Drawer Opened",
            CURRENT_TAB_INDEX_KEY = "CURRENT TAB INDEX";
    public static final int NUM_TABS = 3;
    public static final int JOB_TYPE_SEARCH = 0, JOB_TYPE_SEARCH_LOAD_MORE = 1,
            JOB_TYPE_FETCH_LOCAL_FILE = 2, JOB_TYPE_FETCH_PLAY_LIST = 3, JOB_TYPE_GET_LINK = 4,
            JOB_TYPE_FETCH_RELATED_VIDEO = 5;
    public static final int HANDLE_CARD_VIEW_CLICK = 0, HANDLE_SMALL_BUTTON_CLICK = 1,
            HANDLE_SEARCH_ONLINE_LOAD_MORE = 2;
    public static final int SEARCH_ONLINE_TAB = 0, LOCAL_FILE_TAB = 1, PLAY_LIST_TAB = 2;
    private static final String TAG = "MainActivity";
    public static final int REQUEST_CODE = 0;
    private static final String TUTORIAL_VIDEO = "rqbk9Ce1rUw";
    SearchView searchView;
    RecyclerViewFragment searchOnlineFragment, localFileFragment, playListFragment;
    private DrawerLayout drawer;
    private MaterialViewPager materialViewPager;
    private int currentTabIndex = 0;
    private ProgressDialog progressDialog;
    private SearchOnlineAdapter searchOnlineAdapter;
    private LocalFileAdapter localFileAdapter;
    private PlayListAdapter playListAdapter;
    private String searchOnlineQueryText, nextPageToken;
    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg != null)
                switch (msg.what) {
                    case HANDLE_CARD_VIEW_CLICK:
                        onCardViewClick((View) msg.obj, msg.arg1, msg.arg2);
                        return true;
                    case HANDLE_SMALL_BUTTON_CLICK:
                        onSmallButtonClick((View) msg.obj, msg.arg1, msg.arg2);
                        return true;
                    case HANDLE_SEARCH_ONLINE_LOAD_MORE:
                        new YoutubeQuery(JOB_TYPE_SEARCH_LOAD_MORE, MainActivity.this)
                                .execute(searchOnlineQueryText, nextPageToken);
                        return true;
                }
            return false;
        }
    });

    private MusicStoreObserver observer;
    private ServiceConnection serviceConnection;
    private PlaybackService playbackService;
    private String flagCheckFetchRelatedVideoCallback;
    private boolean grantedPermission = true;
    private boolean loadLocalFile = true;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            grantedPermission = Utils.checkPermissions(this);

        if (!grantedPermission) {
            ActivityCompat.requestPermissions(this,
                    new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"},
                    REQUEST_CODE);
        }

        materialViewPager = (MaterialViewPager) findViewById(R.id.materialViewPager);
        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        final NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Init search view in Toolbar
        Toolbar toolbar = materialViewPager.getToolbar();
        setSupportActionBar(toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            searchView = (SearchView) getLayoutInflater().inflate(R.layout.search_toolbar, toolbar, false);
            searchView.setVersion(SearchCodes.VERSION_TOOLBAR);
            searchView.setStyle(SearchCodes.STYLE_TOOLBAR_CLASSIC);
            searchView.setTheme(SearchCodes.THEME_LIGHT);
            searchView.setDivider(false);
            searchView.setHint("Search in Youtube");
            searchView.setHintSize(getResources().getDimension(R.dimen.search_text_medium));
            searchView.setVoice(true);
            searchView.setOnSearchMenuListener(new SearchView.SearchMenuListener() {
                boolean setTintColor = false;
                @Override
                public void onMenuClick() {
                    if (drawer.isDrawerOpen(GravityCompat.START))
                        drawer.closeDrawer(GravityCompat.START);
                    else {
                        drawer.openDrawer(GravityCompat.START);
                        if (!setTintColor) {
                            setTintColor = true;
                            navigationView.setItemIconTintList(null);
                            navigationView.getMenu().findItem(R.id.nav_download).getIcon().setColorFilter(0xff4d4d4d, PorterDuff.Mode.SRC_ATOP);
                        }

                    }
                }
            });
            toolbar.addView(searchView);
            toolbar.setContentInsetsAbsolute(0, 0);
            ActionBar actionBar = getSupportActionBar();
            assert actionBar != null;
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setDisplayShowHomeEnabled(false);
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayUseLogoEnabled(false);
            actionBar.setHomeButtonEnabled(false);
        }

        // Restore instance state
        if (savedInstanceState != null) {
            FragmentManager fragmentManager = getSupportFragmentManager();
            searchOnlineFragment = (RecyclerViewFragment) fragmentManager.getFragment(savedInstanceState, SEARCH_ONLINE_TITLE);
            localFileFragment = (RecyclerViewFragment) fragmentManager.getFragment(savedInstanceState, LOCAL_FILE_TITLE);
            playListFragment = (RecyclerViewFragment) fragmentManager.getFragment(savedInstanceState, PLAY_LISTS);
            if (savedInstanceState.getBoolean(DRAWER_OPENED_KEY, false))
                drawer.openDrawer(GravityCompat.START);
            currentTabIndex = savedInstanceState.getInt(CURRENT_TAB_INDEX_KEY, 0);
        } else {
            if (!JNILib.checkJNILibs()) {
                if (grantedPermission)
                    showDownloadLibsDialog();
            } else
                bindPlaybackService();

            Utils.logEvent("open_main_activity");
            /* Open tutorial */
            checkFirstOpen();
        }

        if (searchOnlineFragment == null || searchOnlineFragment.getAdapter() == null) {
            searchOnlineAdapter = new SearchOnlineAdapter(this, mHandler);
            searchOnlineFragment = RecyclerViewFragment.newInstance(searchOnlineAdapter);
        } else
            searchOnlineAdapter = (SearchOnlineAdapter) searchOnlineFragment.getAdapter();


        if (localFileFragment == null || localFileFragment.getAdapter() == null) {
            localFileAdapter = new LocalFileAdapter(this, mHandler);
            localFileFragment = RecyclerViewFragment.newInstance(localFileAdapter);
            loadLocalFile = false;
        } else {
            localFileAdapter = (LocalFileAdapter) localFileFragment.getAdapter();
            loadLocalFile = localFileAdapter.getItemCount() != 0;
        }

        if (playListFragment == null || playListFragment.getAdapter() == null) {
            playListAdapter = new PlayListAdapter(this, mHandler);
            playListFragment = RecyclerViewFragment.newInstance(playListAdapter);
            if (grantedPermission)
                new FetchPlayList(this, JOB_TYPE_FETCH_PLAY_LIST, this).execute();
        } else {
            playListAdapter = (PlayListAdapter) playListFragment.getAdapter();
        }


        materialViewPager.getViewPager().setAdapter(new FragmentStatePagerAdapter(getSupportFragmentManager()) {

            @Override
            public Fragment getItem(int position) {
                switch (position % 4) {
                    case 0:
                        return searchOnlineFragment;
                    case 1:
                        return localFileFragment;
                    case 2:
                        return playListFragment;
                    default:
                        return null;
                }
            }

            @Override
            public int getCount() {
                return NUM_TABS;
            }

            @Override
            public CharSequence getPageTitle(int position) {
                switch (position % 3) {
                    case 0:
                        return SEARCH_ONLINE_TITLE;
                    case 1:
                        return LOCAL_FILE_TITLE;
                    case 2:
                        return PLAY_LISTS;
                    default:
                        return "";
                }
            }
        });

        materialViewPager.setMaterialViewPagerListener(new MaterialViewPager.Listener() {
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public HeaderDesign getHeaderDesign(int page) {
                switch (page) {
                    case 0:
                        return HeaderDesign.fromColorResAndDrawable(
                                R.color.blue,
                                ContextCompat.getDrawable(MainActivity.this, R.drawable.online)
                        );
                    case 1:
                        return HeaderDesign.fromColorResAndDrawable(
                                R.color.blue_grey,
                                ContextCompat.getDrawable(MainActivity.this, R.drawable.local)
                        );
                    case 2:
                        return HeaderDesign.fromColorResAndDrawable(
                                R.color.yellow,
                                ContextCompat.getDrawable(MainActivity.this, R.drawable.playlist)
                        );
                }
                return null;
            }
        });

        materialViewPager.getViewPager().setOffscreenPageLimit(NUM_TABS);
        materialViewPager.getPagerTitleStrip().setViewPager(materialViewPager.getViewPager());
        materialViewPager.getViewPager().addOnPageChangeListener(this);
        materialViewPager.getViewPager().setCurrentItem(currentTabIndex, false);

        searchView.setOnQueryTextListener(this);

        observer = new MusicStoreObserver(mHandler);
        getContentResolver().registerContentObserver(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                observer);


        /* HOCKEY SDK*/
        FeedbackManager.register(this);
        MetricsManager.register(this, getApplication(), Constants.HOCKEY_APP_ID);
        checkForUpdates();
        CrashManager.resetAlwaysSend(new WeakReference<Context>(this));
        CrashManager.register(this);
    }

    private void checkFirstOpen() {
        if (Settings.isFirstOpen()) {
            MaterialDialog dialog = new MaterialDialog.Builder(this)
                    .cancelable(false)
                    .autoDismiss(true)
                    .title("Features")
                    .content("You can use this app to listen/download videos directly from YouTube and web browser.")
                    .positiveText("Tutorial")
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            try {
                                Utils.logEvent("view_tutorial");
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + TUTORIAL_VIDEO));
                                startActivity(intent);
                            } catch (ActivityNotFoundException ex) {
                                Intent intent = new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("http://www.youtube.com/watch?v=" + TUTORIAL_VIDEO));
                                startActivity(intent);
                            }
                        }
                    })
                    .negativeText("Skip")
                    .onAny(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            Settings.setFirstOpen(false);
                        }
                    })
                    .build();
            dialog.show();
        }
    }

    private void bindPlaybackService() {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_DO_NOTHING);
        startService(intent);
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                playbackService = ((PlaybackService.MBinder) service).getInstance();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                playbackService = null;
            }
        };
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    private void onCardViewClick(View view, int position, int fragmentId) {
        Log.d(TAG, "onCardViewClick - line 288: " + fragmentId + " pos " + position);
        switch (fragmentId) {
            case SEARCH_ONLINE_TAB:
                if (!grantedPermission) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"},
                            REQUEST_CODE);
                    break;
                }
                if (!JNILib.checkJNILibs()) {
                    showDownloadLibsDialog();
                    break;
                }
                YoutubeData data = searchOnlineAdapter.getData(position);
                flagCheckFetchRelatedVideoCallback = data.id;
                List<YoutubeData> youtubeDatas = new ArrayList<>();
                youtubeDatas.add(data);
                if (playbackService != null)
                    playbackService.playYoutubeList(youtubeDatas, true);
                if (Settings.isAutoPlay())
                    new FetchRelatedVideo(JOB_TYPE_FETCH_RELATED_VIDEO, this).execute(data);
                break;
            case LOCAL_FILE_TAB:
                if (!grantedPermission) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"},
                            REQUEST_CODE);
                    break;
                }
                if (!JNILib.checkJNILibs()) {
                    showDownloadLibsDialog();
                    break;
                }
                if (playbackService != null)
                    playbackService.playLocalFile(localFileAdapter.getDataList(), position);
                break;
            case PLAY_LIST_TAB:
                if (playListAdapter.getSelectingPlaylist() == -1) {
                    if (position == 0) {
                        if (!grantedPermission)
                            ActivityCompat.requestPermissions(this,
                                    new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"},
                                    REQUEST_CODE);
                        else
                            showDialogCreatePlaylist(null);
                    } else {
                        playListAdapter.selectPlaylist(position - 1);
                        searchView.setQuery("");
                        searchView.setHint(
                                "Playlist: " +
                                        playListAdapter.getData(position - 1).getTitle());
                    }
                } else {
                    if (!grantedPermission) {
                        ActivityCompat.requestPermissions(this,
                                new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"},
                                REQUEST_CODE);
                        break;
                    }
                    if (!JNILib.checkJNILibs()) {
                        showDownloadLibsDialog();
                        break;
                    }
                    if (playbackService != null)
                        playbackService.playLocalFile(
                                playListAdapter.getData(playListAdapter.getSelectingPlaylist()).items,
                                position);
                }
                break;
        }
    }

    private void onSmallButtonClick(View view, int position, int fragmentId) {
        switch (fragmentId) {
            case SEARCH_ONLINE_TAB:
                YoutubeData data = searchOnlineAdapter.getData(position);
                Intent intent = new Intent(this, DownloadOffline.class);
                intent.setAction(DownloadOffline.DOWNLOAD_ACTION);
                intent.putExtra(DownloadOffline.TITLE, data.getTitle());
                intent.putExtra(DownloadOffline.YOUTUBE_ID, data.id);
                startActivity(intent);
                //progressDialog = ProgressDialog.show(this, null, "Fetching urls...");

                //new GetLink(JOB_TYPE_GET_LINK, this).execute(data.id, data.getTitle(), data.getDescription());
                break;
            case LOCAL_FILE_TAB:
                showPopupPlaylist(view, position);
                break;
            case PLAY_LIST_TAB:
                if (playListAdapter.getSelectingPlaylist() == -1) {
                    Utils.removePlaylist(getContentResolver(),
                            playListAdapter.getData(position - 1).playListId);
                    playListAdapter.removePlaylist(position - 1);
                } else {
                    Utils.removeFromPlaylist(getContentResolver(),
                            playListAdapter.getData(playListAdapter.getSelectingPlaylist())
                                    .items.get(position).id,
                            playListAdapter.getData(playListAdapter.getSelectingPlaylist())
                                    .playListId
                    );
                    playListAdapter.removeItemInCurrentPlaylist(position);
                }
                break;
        }
    }

    private void showDownloadLibsDialog() {
        new MaterialDialog.Builder(this)
                .title("Media library file does not found")
                .content("Require media codec files to start Playback Service.\n" +
                        "Do you want to download it now (10MB)? Just a moment.")
                .positiveText("Download")
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        try {
                            new DownloadLibDialog().show(getSupportFragmentManager(), "DownloadLibDialog");
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "Oops! An error occurred. Please try again.", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .negativeText("Later")
                .build()
                .show();
    }

    private void showPopupPlaylist(View view, int position) {
        final LocalFileData data = localFileAdapter.getData(position);
        PopupMenu popupMenu = new PopupMenu(this, view);
        popupMenu.getMenu().add(0, 0, 0, "Create new playlist");
        for (int i = 1; i <= playListAdapter.getDataListSize(); i++) {
            popupMenu.getMenu().add(1, i, i, playListAdapter.getData(i - 1).getTitle());
        }
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                final ContentResolver contentResolver = getContentResolver();
                switch (item.getItemId()) {
                    case 0:
                        showDialogCreatePlaylist(data);
                        break;
                    default:
                        PlayListData mPlaylist = playListAdapter.getData(item.getItemId() - 1);
                        playListAdapter.getData(item.getItemId() - 1).items.add(data);
                        playListAdapter.notifyDataSetChanged__();
                        Utils.insertSongToPlaylist(contentResolver, data, mPlaylist.playListId);
                        Toast.makeText(MainActivity.this,
                                "Insert " + data.getTitle() + " to " + mPlaylist.getTitle() + " success",
                                Toast.LENGTH_SHORT).show();
                        break;
                }
                return true;
            }
        });
        popupMenu.show();
    }

    private void showDialogCreatePlaylist(@Nullable final LocalFileData data) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create new Playlist");
        View v = LayoutInflater.from(this).inflate(R.layout.input_play_list_name, null);
        final EditText name = (EditText) v.findViewById(R.id.ed_play_list_name);
        name.clearFocus();
        final TextInputLayout inputLayout = (TextInputLayout) v.findViewById(R.id.input_layout);
        builder.setView(v);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int inputLength = name.getText().length();
                if (inputLength < 2)
                    inputLayout.setError("Too short");
                else if (inputLength > 20)
                    inputLayout.setError("Too long");
                else {
                    String playlistName = name.getText().toString();
                    Uri newPlaylistUri = Utils.createPlaylist(getContentResolver(),
                            playlistName);
                    if (newPlaylistUri != null) {
                        long playlistId = Utils.parseLastInt(newPlaylistUri.toString());
                        Toast.makeText(MainActivity.this, "Create " + playlistName + " playlist success", Toast.LENGTH_SHORT).show();
                        List<LocalFileData> itemsInPlaylist = new ArrayList<>();
                        if (data != null) {
                            itemsInPlaylist.add(data);
                            Utils.insertSongToPlaylist(getContentResolver(), data, playlistId);
                            Toast.makeText(MainActivity.this,
                                    "Insert " + data.getTitle() + " to " + playlistName + " success",
                                    Toast.LENGTH_SHORT).show();
                        }
                        PlayListData aPlaylist = new PlayListData(
                                playlistId,
                                playlistName,
                                itemsInPlaylist
                        );
                        List<PlayListData> list = new ArrayList<>();
                        list.add(aPlaylist);
                        playListAdapter.changeDataList(list, true);
                    } else
                        Toast.makeText(MainActivity.this, "Create " + playlistName + " playlist have an error.", Toast.LENGTH_SHORT).show();
                    dialog.cancel();
                    Utils.logEvent("create_play_list");
                }
            }
        });
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.cancel();
            }
        });
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (!TextUtils.isEmpty(searchView.getQuery()) || searchView.in) {
            searchView.out();
            searchView.setQuery("");
            if (localFileAdapter.getFilterText() != null)
                localFileAdapter.filter(null);
            if (playListAdapter.getFilterText() != null)
                playListAdapter.filter(null);
        } else if (currentTabIndex == PLAY_LIST_TAB && playListAdapter.getSelectingPlaylist() != -1) {
            playListAdapter.selectPlaylist(-1);
            searchView.setHint("Search play lists");
        } else
            super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(observer);
        Log.d(TAG, "onDestroy - line 461: ");
        if (serviceConnection != null) {
            unbindService(serviceConnection);
            unregisterManagers();
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.nav_settings:
                intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
            case R.id.nav_download:
                intent = new Intent(this, DownloadManagerActivity.class);
                startActivity(intent);
                break;
            case R.id.nav_share:
                try {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_SUBJECT, "Youtube Music");
                    String sAux = "XDA Labs link:\n";
                    sAux = sAux + "https://labs.xda-developers.com/store/app/com.kapp.youtube";
                    i.putExtra(Intent.EXTRA_TEXT, sAux);
                    startActivity(Intent.createChooser(i, "Choose one"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Utils.logEvent("share");
                break;
            case R.id.nav_xdalab:
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse("https://labs.xda-developers.com/store/app/com.kapp.youtube"));
                startActivity(Intent.createChooser(i, "Vote"));
                break;
            case R.id.nav_feedback:
                FeedbackManager.showFeedbackActivity(MainActivity.this);
                break;
            case R.id.nav_github:
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.github.com/Khang-NT/Listen-2-Youtube"));
                startActivity(browserIntent);
                break;
        }
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(SEARCH_VIEW_KEY, searchView.onSaveInstanceState());
        outState.putBoolean(DRAWER_OPENED_KEY, drawer.isDrawerOpen(GravityCompat.START));
        outState.putInt(CURRENT_TAB_INDEX_KEY, currentTabIndex);

        FragmentManager fragmentManager = getSupportFragmentManager();
        if (searchOnlineFragment.isAdded())
            fragmentManager.putFragment(outState, SEARCH_ONLINE_TITLE, searchOnlineFragment);
        if (localFileFragment.isAdded())
            fragmentManager.putFragment(outState, LOCAL_FILE_TITLE, localFileFragment);
        if (playListFragment.isAdded())
            fragmentManager.putFragment(outState, PLAY_LISTS, playListFragment);

    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        searchView.onRestoreInstanceState(savedInstanceState.getParcelable(SEARCH_VIEW_KEY));
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        if (searchView.in)
            searchView.out();
        searchView.setQuery("");
        switch (position) {
            case SEARCH_ONLINE_TAB:
                searchView.setHint("Search in Youtube");
                break;
            case LOCAL_FILE_TAB:
                searchView.setHint("Search in local devices");
                break;
            case PLAY_LIST_TAB:
                if (playListAdapter.getSelectingPlaylist() == -1)
                    searchView.setHint("Search play lists");
                else
                    searchView.setHint(
                            "Playlist: " +
                                    playListAdapter.getData(playListAdapter.getSelectingPlaylist()).getTitle());
                break;
        }
        if (position != LOCAL_FILE_TAB && localFileAdapter.getFilterText() != null)
            localFileAdapter.filter(null);
        if (position != PLAY_LIST_TAB && playListAdapter.getFilterText() != null)
            playListAdapter.filter(null);
        if (position == SEARCH_ONLINE_TAB && searchOnlineAdapter.getDataListSize() == 0) {
            MaterialViewPagerHelper.getAnimator(this).setScrollOffset(
                    searchOnlineFragment.getRecyclerView(),
                    0
            );
        }
        if (position == LOCAL_FILE_TAB && !loadLocalFile && grantedPermission) {
            new FetchLocalFileList(MainActivity.this, JOB_TYPE_FETCH_LOCAL_FILE, MainActivity.this)
                    .execute();
            loadLocalFile = true;
        }

        currentTabIndex = position;
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }


    @Override
    public void onFinish(int jobType, Object result) {
        switch (jobType) {
            case JOB_TYPE_SEARCH:
                if (progressDialog != null && progressDialog.isShowing())
                    progressDialog.dismiss();
                progressDialog = null;
                if (result != null) {
                    YoutubeQuery.ResultValue resultValue = (YoutubeQuery.ResultValue) result;
                    searchOnlineAdapter.changeDataList(resultValue.list, false);
                    try {
                        MaterialViewPagerAnimator animator = MaterialViewPagerHelper.getAnimator(this);
                        animator.setScrollOffset(
                                searchOnlineFragment.getRecyclerView(),
                                0
                        );
                        animator.onViewPagerPageChanged();
                    } catch (Exception e) {
                    }
                    nextPageToken = resultValue.after;
                } else
                    Toast.makeText(MainActivity.this, "Search error, please try again.", Toast.LENGTH_SHORT).show();
                break;
            case JOB_TYPE_SEARCH_LOAD_MORE:
                if (result != null) {
                    YoutubeQuery.ResultValue resultValue = (YoutubeQuery.ResultValue) result;
                    if (resultValue.before != null && resultValue.before.equals(nextPageToken)) {
                        searchOnlineAdapter.changeDataList(resultValue.list, true);
                        nextPageToken = resultValue.after;
                    }
                }
                break;
            case JOB_TYPE_FETCH_LOCAL_FILE:
                if (result != null) {
                    List<LocalFileData> list = (List<LocalFileData>) result;
                    localFileAdapter.changeDataList(list, false);
                } else
                    Toast.makeText(MainActivity.this,
                            "Can't load music store, please restart application.",
                            Toast.LENGTH_SHORT).show();
                break;
            case JOB_TYPE_FETCH_PLAY_LIST:
                if (result != null) {
                    Log.d(TAG, "onFinish - line 359: JOB_TYPE_FETCH_PLAY_LIST");
                    List<PlayListData> list = (List<PlayListData>) result;
                    playListAdapter.changeDataList(list, false);
                } else
                    Toast.makeText(MainActivity.this,
                            "Can't load music store, please restart application.",
                            Toast.LENGTH_SHORT).show();
                break;
            case JOB_TYPE_GET_LINK:
//                if (progressDialog != null && progressDialog.isShowing())
//                    progressDialog.dismiss();
//                progressDialog = null;
//                if (result != null) {
//                    final JSONObject jsonObject = (JSONObject) result;
//                    try {
//                        final String title = jsonObject.getString("title"),
//                                album = jsonObject.getString("album"),
//                                getLinkUrl = jsonObject.getString("getLinkUrl");
//                        final JSONObject audio = jsonObject.getJSONObject("audio");
//                        final List<String> items = new ArrayList<>(),
//                                urls = new ArrayList<>(), extensions = new ArrayList<>();
//                        urls.add(audio.getString("url"));
//                        extensions.add(audio.getString("extension"));
//                        items.add("Audio@mp3 - 320k");
//                        items.add("Audio@" + audio.getString("extension") + " - " + audio.getString("bitrate"));
//                        final JSONArray videos = jsonObject.getJSONArray("videos");
//                        for (int i = 0; i < videos.length(); i++) {
//                            items.add("Video@" + videos.getJSONObject(i).getString("extension")
//                                    + " - " + videos.getJSONObject(i).getString("resolution"));
//                            urls.add(videos.getJSONObject(i).getString("url"));
//                            extensions.add(videos.getJSONObject(i).getString("extension"));
//                        }
//                        MaterialDialog dialog = new MaterialDialog.Builder(this)
//                                .items(items)
//                                .title("Choose download link")
//                                .positiveText("OK")
//                                .autoDismiss(false)
//                                .itemsCallbackSingleChoice(1, new MaterialDialog.ListCallbackSingleChoice() {
//                                    @Override
//                                    public boolean onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
//                                        return true;
//                                    }
//                                })
//                                .onPositive(new MaterialDialog.SingleButtonCallback() {
//                                    @Override
//                                    public void onClick(@NonNull final MaterialDialog dialog, @NonNull DialogAction which) {
//                                        int index = dialog.getSelectedIndex();
//                                        Utils.logEvent("download");
//                                        if (index == 0) {
//                                            final ProgressDialog convertMp3Dialog = ProgressDialog.show(MainActivity.this, "", "Converting to mp3...");
//                                            convertMp3Dialog.setCancelable(true);
//                                            mWebView.setWebViewClient(new WebViewClient() {
//                                                @Override
//                                                public void onPageFinished(WebView view, String url) {
//                                                    super.onPageFinished(view, url);
//                                                    Log.e(TAG, "onPageFinished: " + url);
//                                                    if (convertMp3Dialog.isShowing() && url.contains("middle.php")) {
//                                                        try {
//                                                            convertMp3Dialog.dismiss();
//                                                        } catch (Exception ignore) {
//                                                        }
//                                                        Uri uri = Uri.parse(url);
//                                                        String server = uri.getQueryParameters("server").get(0);
//                                                        String hash = uri.getQueryParameters("hash").get(0);
//                                                        String file = null;
//                                                        try {
//                                                            file = URLEncoder.encode(uri.getQueryParameters("file").get(0), "utf-8");
//                                                        } catch (UnsupportedEncodingException e) {
//                                                            e.printStackTrace();
//                                                        }
//                                                        Intent intent = new Intent(MainActivity.this, DownloadService.class);
//                                                        intent.setAction(DownloadService.ACTION_NEW_DOWNLOAD);
//                                                        intent.putExtra(DownloadService.URL, String.format("http://%s.listentoyoutube.com/download/%s/%s", server, hash, file));
//                                                        intent.putExtra(DownloadService.TITLE, title);
//                                                        intent.putExtra(DownloadService.ALBUM, album);
//                                                        intent.putExtra(DownloadService.FILE_NAME, Utils.getValidFileName(title) + ".mp3");
//                                                        intent.putExtra(DownloadService.TYPE, DownloadService.TYPE_MUSIC);
//                                                        startService(intent);
//                                                    }
//                                                }
//                                            });
//                                            mWebView.loadUrl(getLinkUrl);
//                                        } else {
//                                            String url = urls.get(index - 1);
//                                            Intent intent = new Intent(MainActivity.this, DownloadService.class);
//                                            intent.setAction(DownloadService.ACTION_NEW_DOWNLOAD);
//                                            intent.putExtra(DownloadService.URL, url);
//                                            intent.putExtra(DownloadService.TITLE, title);
//                                            intent.putExtra(DownloadService.ALBUM, album);
//                                            intent.putExtra(DownloadService.FILE_NAME, Utils.getValidFileName(title)
//                                                    + "." + extensions.get(index - 1));
//                                            intent.putExtra(DownloadService.TYPE, index == 1 ?
//                                                    DownloadService.TYPE_MUSIC : DownloadService.TYPE_VIDEO);
//                                            startService(intent);
//                                        }
//                                        dialog.dismiss();
//                                    }
//                                })
//                                .negativeText("Cancel")
//                                .onNegative(new MaterialDialog.SingleButtonCallback() {
//                                    @Override
//                                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
//                                        dialog.dismiss();
//                                    }
//                                }).build();
//                        dialog.show();
//                    } catch (JSONException e) {
//                        Toast.makeText(MainActivity.this, "Fetch urls error.", Toast.LENGTH_SHORT).show();
//                    }
//                } else {
//                    Toast.makeText(this, "Fetch urls error by some reasons:\n" +
//                            "- Your network connectivity\n" +
//                            "- Video not available for US", Toast.LENGTH_LONG).show();
//                }
//                break;
            case JOB_TYPE_FETCH_RELATED_VIDEO:
                if (result != null) {
                    List<YoutubeData> youtubeDatas = (List<YoutubeData>) result;
                    if (playbackService != null &&
                            youtubeDatas.get(0).id.equals(flagCheckFetchRelatedVideoCallback))
                        playbackService.playYoutubeList(youtubeDatas, false);
                }
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        searchView.out();
        switch (currentTabIndex) {
            case SEARCH_ONLINE_TAB:
                if (TextUtils.isEmpty(query))
                    break;
                searchOnlineQueryText = query;
                nextPageToken = null;
                searchOnlineAdapter.removeAll();
                progressDialog = ProgressDialog.show(this, null, "Search in progress...");
                new YoutubeQuery(JOB_TYPE_SEARCH, this).execute(query, null);
                Utils.logEvent("search");
                break;
            case LOCAL_FILE_TAB:
                progressDialog = ProgressDialog.show(this, null, "Search in progress...");
                localFileAdapter.filter(query);
                if (progressDialog != null && progressDialog.isShowing())
                    progressDialog.dismiss();
                progressDialog = null;
                break;
            case PLAY_LIST_TAB:
                playListAdapter.filter(query);
                break;
        }

        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE) {
            grantedPermission = Utils.checkPermissions(this);
            if (grantedPermission) {
                if (!JNILib.checkJNILibs())
                    showDownloadLibsDialog();
                new FetchLocalFileList(this, JOB_TYPE_FETCH_LOCAL_FILE, this).execute();
                new FetchPlayList(this, JOB_TYPE_FETCH_PLAY_LIST, this).execute();
            } else
                Toast.makeText(MainActivity.this, "This app can't work without granting storage access permission.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterManagers();
        Tracking.stopUsage(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Tracking.startUsage(this);
    }

    private void checkForUpdates() {
        // Remove this for store builds!
        UpdateManager.register(this);
    }

    private void unregisterManagers() {
        UpdateManager.unregister();
    }

    @Override
    public void onDismiss(DownloadLibDialog dialog) {
        dialog.dismiss();
        if (JNILib.checkJNILibs() && playbackService == null)
            bindPlaybackService();
    }

    private class MusicStoreObserver extends ContentObserver {
        public MusicStoreObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (grantedPermission) {
                new FetchLocalFileList(MainActivity.this, JOB_TYPE_FETCH_LOCAL_FILE, MainActivity.this)
                        .execute();
                loadLocalFile = true;
            }
        }
    }
}
