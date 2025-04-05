// ISuperLyricDistributor.aidl
package com.hchen.superlyricapi;

// Declare any non-default types here with import statements
parcelable SuperLyricData;

interface ISuperLyricDistributor {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    // void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat,
    //        double aDouble, String aString);

     // 代表歌曲被暂停
    void onStop();

    // 当歌曲发生变化时调用
    void onSuperLyric(in SuperLyricData data);
}