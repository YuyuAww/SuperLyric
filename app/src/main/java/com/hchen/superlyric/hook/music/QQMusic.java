package com.hchen.superlyric.hook.music;

import com.hchen.collect.Collect;
import com.hchen.superlyric.base.BaseLyric;

/**
 * QQ 音乐
 */
@Collect(targetPackage = "com.tencent.qqmusic")
public class QQMusic extends BaseLyric {
    @Override
    protected void init() {
        MockFlyme.mock();
        MockFlyme.notificationLyric(this);
    }
}
