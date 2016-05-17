/*****************************************************************************
 * MediaLibrary.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc;

import android.content.Context;
import android.os.Handler;

import org.videolan.libvlc.Media;

import java.io.File;
import java.io.FileFilter;
import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MediaLibrary {
    public final static String TAG = "VLC/MediaLibrary";

    public static final int MEDIA_ITEMS_UPDATED = 100;

    private static MediaLibrary mInstance;
    private final ArrayList<Media> mItemList;
    private final ArrayList<Handler> mUpdateHandler;
    private final ReadWriteLock mItemListLock;
    private boolean isStopping = false;
    private boolean mRestart = false;
    protected Thread mLoadingThread;

    private MediaLibrary() {
        mInstance = this;
        mItemList = new ArrayList<Media>();
        mUpdateHandler = new ArrayList<Handler>();
        mItemListLock = new ReentrantReadWriteLock();
    }

    public void loadMediaItems(Context context, boolean restart) {
        if (restart && isWorking()) {
            /* do a clean restart if a scan is ongoing */
            mRestart = true;
            isStopping = true;
        } else {
            loadMediaItems();
        }
    }

    public void loadMediaItems() {
        if (mLoadingThread == null || mLoadingThread.getState() == State.TERMINATED) {
            isStopping = false;
//            VideoGridFragment.actionScanStart();
//            mLoadingThread = new Thread(new GetMediaItemsRunnable());
//            mLoadingThread.start();
        }
    }

    public void stop() {
        isStopping = true;
    }

    public boolean isWorking() {
        if (mLoadingThread != null &&
            mLoadingThread.isAlive() &&
            mLoadingThread.getState() != State.TERMINATED &&
            mLoadingThread.getState() != State.NEW)
            return true;
        return false;
    }

    public synchronized static MediaLibrary getInstance() {
        if (mInstance == null)
            mInstance = new MediaLibrary();
        return mInstance;
    }

    public void addUpdateHandler(Handler handler) {
        mUpdateHandler.add(handler);
    }

    public void removeUpdateHandler(Handler handler) {
        mUpdateHandler.remove(handler);
    }

    public ArrayList<Media> getVideoItems() {
        ArrayList<Media> videoItems = new ArrayList<Media>();
        mItemListLock.readLock().lock();
        for (int i = 0; i < mItemList.size(); i++) {
            Media item = mItemList.get(i);
            if (item != null && item.getType() == Media.TYPE_VIDEO) {
                videoItems.add(item);
            }
        }
        mItemListLock.readLock().unlock();
        return videoItems;
    }

    public ArrayList<Media> getAudioItems() {
        ArrayList<Media> audioItems = new ArrayList<Media>();
        mItemListLock.readLock().lock();
        for (int i = 0; i < mItemList.size(); i++) {
            Media item = mItemList.get(i);
            if (item.getType() == Media.TYPE_AUDIO) {
                audioItems.add(item);
            }
        }
        mItemListLock.readLock().unlock();
        return audioItems;
    }

    public ArrayList<Media> getAudioItems(String name, String name2, int mode) {
        ArrayList<Media> audioItems = new ArrayList<Media>();
        mItemListLock.readLock().lock();
        for (int i = 0; i < mItemList.size(); i++) {
            Media item = mItemList.get(i);
            if (item.getType() == Media.TYPE_AUDIO) {

                boolean valid = false;
                switch (mode) {
//                    case AudioBrowserFragment.MODE_ARTIST:
//                        valid = name.equals(item.getArtist()) && (name2 == null || name2.equals(item.getAlbum()));
//                        break;
//                    case AudioBrowserFragment.MODE_ALBUM:
//                        valid = name.equals(item.getAlbum());
//                        break;
//                    case AudioBrowserFragment.MODE_GENRE:
//                        valid = name.equals(item.getGenre()) && (name2 == null || name2.equals(item.getAlbum()));
//                        break;
                    default:
                        break;
                }
                if (valid)
                    audioItems.add(item);

            }
        }
        mItemListLock.readLock().unlock();
        return audioItems;
    }

    public ArrayList<Media> getMediaItems() {
        return mItemList;
    }

    public Media getMediaItem(String location) {
        mItemListLock.readLock().lock();
        for (int i = 0; i < mItemList.size(); i++) {
            Media item = mItemList.get(i);
            if (item.getLocation().equals(location)) {
                mItemListLock.readLock().unlock();
                return item;
            }
        }
        mItemListLock.readLock().unlock();
        return null;
    }

    public ArrayList<Media> getMediaItems(List<String> pathList) {
        ArrayList<Media> items = new ArrayList<Media>();
        for (int i = 0; i < pathList.size(); i++) {
            Media item = getMediaItem(pathList.get(i));
            items.add(item);
        }
        return items;
    }

    /**
     * Filters all irrelevant files
     */
    private static class MediaItemFilter implements FileFilter {

        @Override
        public boolean accept(File f) {
            boolean accepted = false;
            if (!f.isHidden()) {
                if (f.isDirectory() && !Media.FOLDER_BLACKLIST.contains(f.getPath().toLowerCase(Locale.ENGLISH))) {
                    accepted = true;
                } else {
                    String fileName = f.getName().toLowerCase(Locale.ENGLISH);
                    int dotIndex = fileName.lastIndexOf(".");
                    if (dotIndex != -1) {
                        String fileExt = fileName.substring(dotIndex);
                        accepted = Media.AUDIO_EXTENSIONS.contains(fileExt) ||
                                   Media.VIDEO_EXTENSIONS.contains(fileExt);
                    }
                }
            }
            return accepted;
        }
    }
}
