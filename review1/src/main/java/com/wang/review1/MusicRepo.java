package com.wang.review1;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;

import java.util.ArrayList;
import java.util.List;

public class MusicRepo {

    private static List<MediaBrowserCompat.MediaItem> sMediaItems = new ArrayList<>();

    static {
        MediaBrowserCompat.MediaItem item = new MediaBrowserCompat.MediaItem(
                new MediaDescriptionCompat.Builder()
                        .setMediaId("LaLaLoveOnMyMind")
                        .setDescription("LaLaLoveOnMyMind")
                        .setTitle("LaLaLoveOnMyMind Title")
                        .setSubtitle("LaLaLoveOnMyMind Subtitle")
//                        .setIconBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_background))
                        .setIconUri(Uri.parse("android.resource://com.wang.review1/drawable/ic_launcher_background"))
                        .build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
        sMediaItems.add(item);
    }

    public static List<MediaBrowserCompat.MediaItem> getMediaItems() {
        return sMediaItems;
    }

//    public Uri getUriFromDrawableRes(Context context, int id) {
//        Resources resources = context.getResources();
//        String path = ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
//                + resources.getResourcePackageName(id) + "/"
//                + resources.getResourceTypeName(id) + "/"
//                + resources.getResourceEntryName(id);
//        return Uri.parse(path);
//    }
//
//    private static String getAlbumArtUri(String albumArtResName) {
//        return ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
//                BuildConfig.APPLICATION_ID + "/drawable/" + albumArtResName;
//    }
}
