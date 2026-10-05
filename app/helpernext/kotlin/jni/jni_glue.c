#include <jni.h>
#include <stdint.h>
#include <stdbool.h>
#include <limits.h>
#include <string.h>
#include <stdlib.h>
#if defined(__ANDROID__)
#include <pthread.h>
#endif

#include "qqmusic_api_helper_next.h"
static void boltffi_jni_throw_runtime(JNIEnv *env, const char *message) {
    jclass exception_class = (*env)->FindClass(env, "java/lang/RuntimeException");
    if (exception_class == NULL) {
        return;
    }
    (*env)->ThrowNew(env, exception_class, message);
    (*env)->DeleteLocalRef(env, exception_class);
}

static void boltffi_jni_throw_illegal_argument(JNIEnv *env, const char *message) {
    jclass exception_class = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    if (exception_class == NULL) {
        return;
    }
    (*env)->ThrowNew(env, exception_class, message);
    (*env)->DeleteLocalRef(env, exception_class);
}

static void boltffi_jni_throw_error_buffer(JNIEnv *env, FfiBuf_u8 buffer) {
    if (buffer.len > ((uintptr_t)INT32_MAX)) {
        boltffi_free_buf(buffer);
        boltffi_jni_throw_runtime(env, "BoltFFI error buffer was too large");
        return;
    }
    jbyteArray bytes = (*env)->NewByteArray(env, (jsize)buffer.len);
    if (bytes != NULL && buffer.len != 0) {
        (*env)->SetByteArrayRegion(env, bytes, 0, (jsize)buffer.len, (const jbyte *)buffer.ptr);
    }
    boltffi_free_buf(buffer);
    if (bytes == NULL || (*env)->ExceptionCheck(env)) {
        return;
    }
    jclass exception_class = (*env)->FindClass(env, "com/example/qqmusic_api_helper_next/BoltFfiErrorBufferException");
    if (exception_class == NULL) {
        (*env)->DeleteLocalRef(env, bytes);
        return;
    }
    jmethodID constructor = (*env)->GetMethodID(env, exception_class, "<init>", "([B)V");
    if (constructor == NULL) {
        (*env)->DeleteLocalRef(env, exception_class);
        (*env)->DeleteLocalRef(env, bytes);
        return;
    }
    jthrowable exception = (jthrowable)(*env)->NewObject(env, exception_class, constructor, bytes);
    if (exception != NULL) {
        (*env)->Throw(env, exception);
        (*env)->DeleteLocalRef(env, exception);
    }
    (*env)->DeleteLocalRef(env, exception_class);
    (*env)->DeleteLocalRef(env, bytes);
}

static jbyteArray boltffi_jni_buffer_to_byte_array(JNIEnv *env, FfiBuf_u8 buffer) {
    if (buffer.ptr == NULL) {
        if (buffer.len != 0) {
            boltffi_jni_throw_runtime(env, "BoltFFI buffer pointer was null with non-zero length");
            return NULL;
        }
        return (*env)->NewByteArray(env, 0);
    }
    if (buffer.len > (uintptr_t)INT32_MAX) {
        boltffi_free_buf(buffer);
        boltffi_jni_throw_runtime(env, "BoltFFI buffer too large for Java byte array");
        return NULL;
    }
    jbyteArray array = (*env)->NewByteArray(env, (jsize)buffer.len);
    if (array == NULL) {
        boltffi_free_buf(buffer);
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, array, 0, (jsize)buffer.len, (const jbyte *)buffer.ptr);
    boltffi_free_buf(buffer);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->DeleteLocalRef(env, array);
        return NULL;
    }
    return array;
}

static inline jbyteArray boltffi_jni_bytes_to_byte_array(JNIEnv *env, const uint8_t *bytes, uintptr_t len) {
    if (bytes == NULL && len != 0) {
        boltffi_jni_throw_runtime(env, "BoltFFI byte slice pointer was null with non-zero length");
        return NULL;
    }
    if (len > (uintptr_t)INT32_MAX) {
        boltffi_jni_throw_runtime(env, "BoltFFI byte slice too large for Java byte array");
        return NULL;
    }
    jbyteArray array = (*env)->NewByteArray(env, (jsize)len);
    if (array == NULL) {
        return NULL;
    }
    if (len != 0) {
        (*env)->SetByteArrayRegion(env, array, 0, (jsize)len, (const jbyte *)bytes);
    }
    return array;
}

static inline FfiBuf_u8 boltffi_jni_byte_array_to_buffer(JNIEnv *env, jbyteArray array) {
    FfiBuf_u8 empty = {0};
    if (array == NULL) {
        boltffi_jni_throw_runtime(env, "BoltFFI byte array return was null");
        return empty;
    }
    jsize len = (*env)->GetArrayLength(env, array);
    if (len == 0) {
        return empty;
    }
    FfiBuf_u8 buffer = boltffi_buf_with_len((uintptr_t)len);
    if (buffer.ptr == NULL) {
        boltffi_jni_throw_runtime(env, "failed to allocate BoltFFI byte array return");
        return empty;
    }
    (*env)->GetByteArrayRegion(env, array, 0, len, (jbyte *)buffer.ptr);
    return buffer;
}

static bool boltffi_jni_direct_buffer_address(JNIEnv *env, jobject buffer, jlong required_capacity, void **address) {
    if (buffer == NULL) {
        boltffi_jni_throw_illegal_argument(env, "BoltFFI direct buffer argument was null");
        return false;
    }
    if (required_capacity < 0) {
        boltffi_jni_throw_illegal_argument(env, "BoltFFI direct buffer length was negative");
        return false;
    }
    jlong capacity = (*env)->GetDirectBufferCapacity(env, buffer);
    if (capacity < 0) {
        boltffi_jni_throw_illegal_argument(env, "BoltFFI argument was not a direct buffer");
        return false;
    }
    if (capacity < required_capacity) {
        boltffi_jni_throw_illegal_argument(env, "BoltFFI direct buffer capacity was too small");
        return false;
    }
    *address = (*env)->GetDirectBufferAddress(env, buffer);
    if (*address == NULL && required_capacity != 0) {
        boltffi_jni_throw_illegal_argument(env, "BoltFFI direct buffer address was unavailable");
        return false;
    }
    return true;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1collection_1write_1create_1playlist(JNIEnv *env, jclass cls, jobject dirname, jint __boltffi_dirname_len) {
    (void)cls;

    void *__boltffi_dirname_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, dirname, (jlong)__boltffi_dirname_len, &__boltffi_dirname_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_collection_write_create_playlist((const uint8_t *)__boltffi_dirname_ptr, (uintptr_t)__boltffi_dirname_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1collection_1write_1delete_1playlist(JNIEnv *env, jclass cls, jlong dirid) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_collection_write_delete_playlist(dirid, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jboolean JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1collection_1write_1add_1playlist_1songs(JNIEnv *env, jclass cls, jlong dirid, jbyteArray song_info, jobject tid, jint __boltffi_tid_len) {
    (void)cls;

    jbyte *__boltffi_song_info_ptr = NULL;
    jsize __boltffi_song_info_len = 0;
    void *__boltffi_tid_ptr = NULL;
    bool __boltffi_return = (bool){0};

    if (song_info == NULL) {
        boltffi_jni_throw_illegal_argument(env, "BoltFFI array argument was null");
        goto __boltffi_error;
    }
    __boltffi_song_info_len = (*env)->GetArrayLength(env, song_info);
    __boltffi_song_info_ptr = (*env)->GetByteArrayElements(env, song_info, NULL);
    if (__boltffi_song_info_ptr == NULL) {
        goto __boltffi_error;
    }

    if (!boltffi_jni_direct_buffer_address(env, tid, (jlong)__boltffi_tid_len, &__boltffi_tid_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_collection_write_add_playlist_songs(dirid, (const uint8_t *)__boltffi_song_info_ptr, (uintptr_t)__boltffi_song_info_len, (const uint8_t *)__boltffi_tid_ptr, (uintptr_t)__boltffi_tid_len, &__boltffi_return);

    if (__boltffi_song_info_ptr != NULL) {
        (*env)->ReleaseByteArrayElements(env, song_info, __boltffi_song_info_ptr, JNI_ABORT);
        __boltffi_song_info_ptr = NULL;
    }
    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return JNI_FALSE;
    }

    return (jboolean)__boltffi_return;
__boltffi_error:
    if (__boltffi_song_info_ptr != NULL) {
        (*env)->ReleaseByteArrayElements(env, song_info, __boltffi_song_info_ptr, JNI_ABORT);
        __boltffi_song_info_ptr = NULL;
    }
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1collection_1write_1remove_1playlist_1songs(JNIEnv *env, jclass cls, jlong dirid, jbyteArray song_info, jobject tid, jint __boltffi_tid_len) {
    (void)cls;

    jbyte *__boltffi_song_info_ptr = NULL;
    jsize __boltffi_song_info_len = 0;
    void *__boltffi_tid_ptr = NULL;
    bool __boltffi_return = (bool){0};

    if (song_info == NULL) {
        boltffi_jni_throw_illegal_argument(env, "BoltFFI array argument was null");
        goto __boltffi_error;
    }
    __boltffi_song_info_len = (*env)->GetArrayLength(env, song_info);
    __boltffi_song_info_ptr = (*env)->GetByteArrayElements(env, song_info, NULL);
    if (__boltffi_song_info_ptr == NULL) {
        goto __boltffi_error;
    }

    if (!boltffi_jni_direct_buffer_address(env, tid, (jlong)__boltffi_tid_len, &__boltffi_tid_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_collection_write_remove_playlist_songs(dirid, (const uint8_t *)__boltffi_song_info_ptr, (uintptr_t)__boltffi_song_info_len, (const uint8_t *)__boltffi_tid_ptr, (uintptr_t)__boltffi_tid_len, &__boltffi_return);

    if (__boltffi_song_info_ptr != NULL) {
        (*env)->ReleaseByteArrayElements(env, song_info, __boltffi_song_info_ptr, JNI_ABORT);
        __boltffi_song_info_ptr = NULL;
    }
    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return JNI_FALSE;
    }

    return (jboolean)__boltffi_return;
__boltffi_error:
    if (__boltffi_song_info_ptr != NULL) {
        (*env)->ReleaseByteArrayElements(env, song_info, __boltffi_song_info_ptr, JNI_ABORT);
        __boltffi_song_info_ptr = NULL;
    }
    return JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1collection_1write_1fav_1album(JNIEnv *env, jclass cls, jlongArray album_ids) {
    (void)cls;

    jlong *__boltffi_album_ids_ptr = NULL;
    jsize __boltffi_album_ids_len = 0;
    jlong __boltffi_album_ids_stack[8];
    bool __boltffi_album_ids_needs_release = false;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (album_ids == NULL) {
        boltffi_jni_throw_illegal_argument(env, "BoltFFI array argument was null");
        goto __boltffi_error;
    }
    __boltffi_album_ids_len = (*env)->GetArrayLength(env, album_ids);
    if (__boltffi_album_ids_len <= (jsize)8) {
        (*env)->GetLongArrayRegion(env, album_ids, 0, __boltffi_album_ids_len, __boltffi_album_ids_stack);
        if ((*env)->ExceptionCheck(env)) {
            goto __boltffi_error;
        }
        __boltffi_album_ids_ptr = __boltffi_album_ids_stack;
    } else {
        __boltffi_album_ids_ptr = (*env)->GetLongArrayElements(env, album_ids, NULL);
        if (__boltffi_album_ids_ptr == NULL) {
            goto __boltffi_error;
        }
        __boltffi_album_ids_needs_release = true;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_collection_write_fav_album((const int64_t *)__boltffi_album_ids_ptr, (uintptr_t)__boltffi_album_ids_len, &__boltffi_return);

    if (__boltffi_album_ids_ptr != NULL) {
        if (__boltffi_album_ids_needs_release) {
            (*env)->ReleaseLongArrayElements(env, album_ids, __boltffi_album_ids_ptr, JNI_ABORT);
        }
        __boltffi_album_ids_ptr = NULL;
    }
    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    if (__boltffi_album_ids_ptr != NULL) {
        if (__boltffi_album_ids_needs_release) {
            (*env)->ReleaseLongArrayElements(env, album_ids, __boltffi_album_ids_ptr, JNI_ABORT);
        }
        __boltffi_album_ids_ptr = NULL;
    }
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1collection_1write_1unfav_1album(JNIEnv *env, jclass cls, jlongArray album_ids) {
    (void)cls;

    jlong *__boltffi_album_ids_ptr = NULL;
    jsize __boltffi_album_ids_len = 0;
    jlong __boltffi_album_ids_stack[8];
    bool __boltffi_album_ids_needs_release = false;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (album_ids == NULL) {
        boltffi_jni_throw_illegal_argument(env, "BoltFFI array argument was null");
        goto __boltffi_error;
    }
    __boltffi_album_ids_len = (*env)->GetArrayLength(env, album_ids);
    if (__boltffi_album_ids_len <= (jsize)8) {
        (*env)->GetLongArrayRegion(env, album_ids, 0, __boltffi_album_ids_len, __boltffi_album_ids_stack);
        if ((*env)->ExceptionCheck(env)) {
            goto __boltffi_error;
        }
        __boltffi_album_ids_ptr = __boltffi_album_ids_stack;
    } else {
        __boltffi_album_ids_ptr = (*env)->GetLongArrayElements(env, album_ids, NULL);
        if (__boltffi_album_ids_ptr == NULL) {
            goto __boltffi_error;
        }
        __boltffi_album_ids_needs_release = true;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_collection_write_unfav_album((const int64_t *)__boltffi_album_ids_ptr, (uintptr_t)__boltffi_album_ids_len, &__boltffi_return);

    if (__boltffi_album_ids_ptr != NULL) {
        if (__boltffi_album_ids_needs_release) {
            (*env)->ReleaseLongArrayElements(env, album_ids, __boltffi_album_ids_ptr, JNI_ABORT);
        }
        __boltffi_album_ids_ptr = NULL;
    }
    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    if (__boltffi_album_ids_ptr != NULL) {
        if (__boltffi_album_ids_needs_release) {
            (*env)->ReleaseLongArrayElements(env, album_ids, __boltffi_album_ids_ptr, JNI_ABORT);
        }
        __boltffi_album_ids_ptr = NULL;
    }
    return NULL;
}

JNIEXPORT jboolean JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1collection_1write_1fav_1playlist(JNIEnv *env, jclass cls, jlong playlist_id) {
    (void)cls;

    bool __boltffi_return = (bool){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_collection_write_fav_playlist(playlist_id, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return JNI_FALSE;
    }

    return (jboolean)__boltffi_return;
}

JNIEXPORT jboolean JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1collection_1write_1unfav_1playlist(JNIEnv *env, jclass cls, jlong playlist_id) {
    (void)cls;

    bool __boltffi_return = (bool){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_collection_write_unfav_playlist(playlist_id, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return JNI_FALSE;
    }

    return (jboolean)__boltffi_return;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1comment_1fetch_1comment_1count(JNIEnv *env, jclass cls, jlong biz_id, jobject biz_type, jint __boltffi_biz_type_len, jobject biz_sub_type, jint __boltffi_biz_sub_type_len) {
    (void)cls;

    void *__boltffi_biz_type_ptr = NULL;
    void *__boltffi_biz_sub_type_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, biz_type, (jlong)__boltffi_biz_type_len, &__boltffi_biz_type_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, biz_sub_type, (jlong)__boltffi_biz_sub_type_len, &__boltffi_biz_sub_type_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_comment_fetch_comment_count(biz_id, (const uint8_t *)__boltffi_biz_type_ptr, (uintptr_t)__boltffi_biz_type_len, (const uint8_t *)__boltffi_biz_sub_type_ptr, (uintptr_t)__boltffi_biz_sub_type_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1comment_1fetch_1hot_1comments(JNIEnv *env, jclass cls, jlong biz_id, jobject page, jint __boltffi_page_len, jobject page_size, jint __boltffi_page_size_len, jobject last_comment_seq_no, jint __boltffi_last_comment_seq_no_len, jobject biz_type, jint __boltffi_biz_type_len, jobject biz_sub_type, jint __boltffi_biz_sub_type_len) {
    (void)cls;

    void *__boltffi_page_ptr = NULL;
    void *__boltffi_page_size_ptr = NULL;
    void *__boltffi_last_comment_seq_no_ptr = NULL;
    void *__boltffi_biz_type_ptr = NULL;
    void *__boltffi_biz_sub_type_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page_size, (jlong)__boltffi_page_size_len, &__boltffi_page_size_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, last_comment_seq_no, (jlong)__boltffi_last_comment_seq_no_len, &__boltffi_last_comment_seq_no_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, biz_type, (jlong)__boltffi_biz_type_len, &__boltffi_biz_type_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, biz_sub_type, (jlong)__boltffi_biz_sub_type_len, &__boltffi_biz_sub_type_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_comment_fetch_hot_comments(biz_id, (const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, (const uint8_t *)__boltffi_page_size_ptr, (uintptr_t)__boltffi_page_size_len, (const uint8_t *)__boltffi_last_comment_seq_no_ptr, (uintptr_t)__boltffi_last_comment_seq_no_len, (const uint8_t *)__boltffi_biz_type_ptr, (uintptr_t)__boltffi_biz_type_len, (const uint8_t *)__boltffi_biz_sub_type_ptr, (uintptr_t)__boltffi_biz_sub_type_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1comment_1fetch_1new_1comments(JNIEnv *env, jclass cls, jlong biz_id, jobject page, jint __boltffi_page_len, jobject page_size, jint __boltffi_page_size_len, jobject last_comment_seq_no, jint __boltffi_last_comment_seq_no_len, jobject biz_type, jint __boltffi_biz_type_len, jobject biz_sub_type, jint __boltffi_biz_sub_type_len) {
    (void)cls;

    void *__boltffi_page_ptr = NULL;
    void *__boltffi_page_size_ptr = NULL;
    void *__boltffi_last_comment_seq_no_ptr = NULL;
    void *__boltffi_biz_type_ptr = NULL;
    void *__boltffi_biz_sub_type_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page_size, (jlong)__boltffi_page_size_len, &__boltffi_page_size_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, last_comment_seq_no, (jlong)__boltffi_last_comment_seq_no_len, &__boltffi_last_comment_seq_no_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, biz_type, (jlong)__boltffi_biz_type_len, &__boltffi_biz_type_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, biz_sub_type, (jlong)__boltffi_biz_sub_type_len, &__boltffi_biz_sub_type_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_comment_fetch_new_comments(biz_id, (const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, (const uint8_t *)__boltffi_page_size_ptr, (uintptr_t)__boltffi_page_size_len, (const uint8_t *)__boltffi_last_comment_seq_no_ptr, (uintptr_t)__boltffi_last_comment_seq_no_len, (const uint8_t *)__boltffi_biz_type_ptr, (uintptr_t)__boltffi_biz_type_len, (const uint8_t *)__boltffi_biz_sub_type_ptr, (uintptr_t)__boltffi_biz_sub_type_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1comment_1fetch_1recommend_1comments(JNIEnv *env, jclass cls, jlong biz_id, jobject page, jint __boltffi_page_len, jobject page_size, jint __boltffi_page_size_len, jobject last_comment_seq_no, jint __boltffi_last_comment_seq_no_len, jobject biz_type, jint __boltffi_biz_type_len, jobject biz_sub_type, jint __boltffi_biz_sub_type_len) {
    (void)cls;

    void *__boltffi_page_ptr = NULL;
    void *__boltffi_page_size_ptr = NULL;
    void *__boltffi_last_comment_seq_no_ptr = NULL;
    void *__boltffi_biz_type_ptr = NULL;
    void *__boltffi_biz_sub_type_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page_size, (jlong)__boltffi_page_size_len, &__boltffi_page_size_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, last_comment_seq_no, (jlong)__boltffi_last_comment_seq_no_len, &__boltffi_last_comment_seq_no_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, biz_type, (jlong)__boltffi_biz_type_len, &__boltffi_biz_type_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, biz_sub_type, (jlong)__boltffi_biz_sub_type_len, &__boltffi_biz_sub_type_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_comment_fetch_recommend_comments(biz_id, (const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, (const uint8_t *)__boltffi_page_size_ptr, (uintptr_t)__boltffi_page_size_len, (const uint8_t *)__boltffi_last_comment_seq_no_ptr, (uintptr_t)__boltffi_last_comment_seq_no_len, (const uint8_t *)__boltffi_biz_type_ptr, (uintptr_t)__boltffi_biz_type_len, (const uint8_t *)__boltffi_biz_sub_type_ptr, (uintptr_t)__boltffi_biz_sub_type_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1comment_1fetch_1moment_1comments(JNIEnv *env, jclass cls, jlong biz_id, jobject page_size, jint __boltffi_page_size_len, jobject last_comment_seq_no, jint __boltffi_last_comment_seq_no_len, jobject biz_type, jint __boltffi_biz_type_len, jobject biz_sub_type, jint __boltffi_biz_sub_type_len) {
    (void)cls;

    void *__boltffi_page_size_ptr = NULL;
    void *__boltffi_last_comment_seq_no_ptr = NULL;
    void *__boltffi_biz_type_ptr = NULL;
    void *__boltffi_biz_sub_type_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, page_size, (jlong)__boltffi_page_size_len, &__boltffi_page_size_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, last_comment_seq_no, (jlong)__boltffi_last_comment_seq_no_len, &__boltffi_last_comment_seq_no_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, biz_type, (jlong)__boltffi_biz_type_len, &__boltffi_biz_type_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, biz_sub_type, (jlong)__boltffi_biz_sub_type_len, &__boltffi_biz_sub_type_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_comment_fetch_moment_comments(biz_id, (const uint8_t *)__boltffi_page_size_ptr, (uintptr_t)__boltffi_page_size_len, (const uint8_t *)__boltffi_last_comment_seq_no_ptr, (uintptr_t)__boltffi_last_comment_seq_no_len, (const uint8_t *)__boltffi_biz_type_ptr, (uintptr_t)__boltffi_biz_type_len, (const uint8_t *)__boltffi_biz_sub_type_ptr, (uintptr_t)__boltffi_biz_sub_type_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1comment_1add_1comment(JNIEnv *env, jclass cls, jlong biz_id, jobject content, jint __boltffi_content_len, jobject reply_cmt_id, jint __boltffi_reply_cmt_id_len, jobject biz_type, jint __boltffi_biz_type_len, jobject biz_sub_type, jint __boltffi_biz_sub_type_len) {
    (void)cls;

    void *__boltffi_content_ptr = NULL;
    void *__boltffi_reply_cmt_id_ptr = NULL;
    void *__boltffi_biz_type_ptr = NULL;
    void *__boltffi_biz_sub_type_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, content, (jlong)__boltffi_content_len, &__boltffi_content_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, reply_cmt_id, (jlong)__boltffi_reply_cmt_id_len, &__boltffi_reply_cmt_id_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, biz_type, (jlong)__boltffi_biz_type_len, &__boltffi_biz_type_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, biz_sub_type, (jlong)__boltffi_biz_sub_type_len, &__boltffi_biz_sub_type_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_comment_add_comment(biz_id, (const uint8_t *)__boltffi_content_ptr, (uintptr_t)__boltffi_content_len, (const uint8_t *)__boltffi_reply_cmt_id_ptr, (uintptr_t)__boltffi_reply_cmt_id_len, (const uint8_t *)__boltffi_biz_type_ptr, (uintptr_t)__boltffi_biz_type_len, (const uint8_t *)__boltffi_biz_sub_type_ptr, (uintptr_t)__boltffi_biz_sub_type_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jboolean JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1comment_1delete_1comment(JNIEnv *env, jclass cls, jobject cm_id, jint __boltffi_cm_id_len) {
    (void)cls;

    void *__boltffi_cm_id_ptr = NULL;
    bool __boltffi_return = (bool){0};

    if (!boltffi_jni_direct_buffer_address(env, cm_id, (jlong)__boltffi_cm_id_len, &__boltffi_cm_id_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_comment_delete_comment((const uint8_t *)__boltffi_cm_id_ptr, (uintptr_t)__boltffi_cm_id_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return JNI_FALSE;
    }

    return (jboolean)__boltffi_return;
__boltffi_error:
    return JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1library_1extra_1fetch_1new_1albums(JNIEnv *env, jclass cls, jobject area, jint __boltffi_area_len, jobject num, jint __boltffi_num_len, jobject page, jint __boltffi_page_len) {
    (void)cls;

    void *__boltffi_area_ptr = NULL;
    void *__boltffi_num_ptr = NULL;
    void *__boltffi_page_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, area, (jlong)__boltffi_area_len, &__boltffi_area_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, num, (jlong)__boltffi_num_len, &__boltffi_num_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_new_albums((const uint8_t *)__boltffi_area_ptr, (uintptr_t)__boltffi_area_len, (const uint8_t *)__boltffi_num_ptr, (uintptr_t)__boltffi_num_len, (const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1library_1extra_1fetch_1singing_1annotations(JNIEnv *env, jclass cls, jlong song_id) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_singing_annotations(song_id, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1library_1extra_1fetch_1multi_1style_1lyrics(JNIEnv *env, jclass cls, jlong song_id) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_multi_style_lyrics(song_id, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1library_1extra_1has_1ai_1dictionary(JNIEnv *env, jclass cls, jlong song_id) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_library_extra_has_ai_dictionary(song_id, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1library_1extra_1fetch_1ai_1dictionary(JNIEnv *env, jclass cls, jlong song_id) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_ai_dictionary(song_id, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1library_1extra_1fetch_1user_1liked_1songs(JNIEnv *env, jclass cls, jobject euin, jint __boltffi_euin_len, jobject page, jint __boltffi_page_len, jobject num, jint __boltffi_num_len) {
    (void)cls;

    void *__boltffi_euin_ptr = NULL;
    void *__boltffi_page_ptr = NULL;
    void *__boltffi_num_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, euin, (jlong)__boltffi_euin_len, &__boltffi_euin_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, num, (jlong)__boltffi_num_len, &__boltffi_num_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_user_liked_songs((const uint8_t *)__boltffi_euin_ptr, (uintptr_t)__boltffi_euin_len, (const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, (const uint8_t *)__boltffi_num_ptr, (uintptr_t)__boltffi_num_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1login_1extra_1fetch_1wx_1qrcode(JNIEnv *env, jclass cls) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_login_extra_fetch_wx_qrcode(&__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1login_1extra_1check_1wx_1qrcode(JNIEnv *env, jclass cls, jobject identifier, jint __boltffi_identifier_len) {
    (void)cls;

    void *__boltffi_identifier_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, identifier, (jlong)__boltffi_identifier_len, &__boltffi_identifier_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_login_extra_check_wx_qrcode((const uint8_t *)__boltffi_identifier_ptr, (uintptr_t)__boltffi_identifier_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1login_1extra_1send_1phone_1authcode(JNIEnv *env, jclass cls, jobject phone, jint __boltffi_phone_len, jobject encrypted_phone, jint __boltffi_encrypted_phone_len, jobject country_code, jint __boltffi_country_code_len) {
    (void)cls;

    void *__boltffi_phone_ptr = NULL;
    void *__boltffi_encrypted_phone_ptr = NULL;
    void *__boltffi_country_code_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, phone, (jlong)__boltffi_phone_len, &__boltffi_phone_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, encrypted_phone, (jlong)__boltffi_encrypted_phone_len, &__boltffi_encrypted_phone_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, country_code, (jlong)__boltffi_country_code_len, &__boltffi_country_code_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_login_extra_send_phone_authcode((const uint8_t *)__boltffi_phone_ptr, (uintptr_t)__boltffi_phone_len, (const uint8_t *)__boltffi_encrypted_phone_ptr, (uintptr_t)__boltffi_encrypted_phone_len, (const uint8_t *)__boltffi_country_code_ptr, (uintptr_t)__boltffi_country_code_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1login_1extra_1phone_1login(JNIEnv *env, jclass cls, jobject phone, jint __boltffi_phone_len, jobject encrypted_phone, jint __boltffi_encrypted_phone_len, jobject auth_code, jint __boltffi_auth_code_len) {
    (void)cls;

    void *__boltffi_phone_ptr = NULL;
    void *__boltffi_encrypted_phone_ptr = NULL;
    void *__boltffi_auth_code_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, phone, (jlong)__boltffi_phone_len, &__boltffi_phone_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, encrypted_phone, (jlong)__boltffi_encrypted_phone_len, &__boltffi_encrypted_phone_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, auth_code, (jlong)__boltffi_auth_code_len, &__boltffi_auth_code_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_login_extra_phone_login((const uint8_t *)__boltffi_phone_ptr, (uintptr_t)__boltffi_phone_len, (const uint8_t *)__boltffi_encrypted_phone_ptr, (uintptr_t)__boltffi_encrypted_phone_len, (const uint8_t *)__boltffi_auth_code_ptr, (uintptr_t)__boltffi_auth_code_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1login_1extra_1refresh_1credential(JNIEnv *env, jclass cls) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_login_extra_refresh_credential(&__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1mv_1fetch_1mv_1detail(JNIEnv *env, jclass cls, jobject vids, jint __boltffi_vids_len) {
    (void)cls;

    void *__boltffi_vids_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, vids, (jlong)__boltffi_vids_len, &__boltffi_vids_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_mv_fetch_mv_detail((const uint8_t *)__boltffi_vids_ptr, (uintptr_t)__boltffi_vids_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1mv_1resolve_1mv_1urls(JNIEnv *env, jclass cls, jobject vids, jint __boltffi_vids_len) {
    (void)cls;

    void *__boltffi_vids_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, vids, (jlong)__boltffi_vids_len, &__boltffi_vids_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_mv_resolve_mv_urls((const uint8_t *)__boltffi_vids_ptr, (uintptr_t)__boltffi_vids_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1mv_1fetch_1mv_1list(JNIEnv *env, jclass cls, jobject area, jint __boltffi_area_len, jobject version, jint __boltffi_version_len, jobject order, jint __boltffi_order_len, jobject num, jint __boltffi_num_len, jobject page, jint __boltffi_page_len) {
    (void)cls;

    void *__boltffi_area_ptr = NULL;
    void *__boltffi_version_ptr = NULL;
    void *__boltffi_order_ptr = NULL;
    void *__boltffi_num_ptr = NULL;
    void *__boltffi_page_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, area, (jlong)__boltffi_area_len, &__boltffi_area_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, version, (jlong)__boltffi_version_len, &__boltffi_version_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, order, (jlong)__boltffi_order_len, &__boltffi_order_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, num, (jlong)__boltffi_num_len, &__boltffi_num_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_mv_fetch_mv_list((const uint8_t *)__boltffi_area_ptr, (uintptr_t)__boltffi_area_len, (const uint8_t *)__boltffi_version_ptr, (uintptr_t)__boltffi_version_len, (const uint8_t *)__boltffi_order_ptr, (uintptr_t)__boltffi_order_len, (const uint8_t *)__boltffi_num_ptr, (uintptr_t)__boltffi_num_len, (const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1recommend_1extra_1fetch_1home_1feed(JNIEnv *env, jclass cls, jobject page, jint __boltffi_page_len, jobject direction, jint __boltffi_direction_len, jobject s_num, jint __boltffi_s_num_len, jobject v_cache, jint __boltffi_v_cache_len) {
    (void)cls;

    void *__boltffi_page_ptr = NULL;
    void *__boltffi_direction_ptr = NULL;
    void *__boltffi_s_num_ptr = NULL;
    void *__boltffi_v_cache_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, direction, (jlong)__boltffi_direction_len, &__boltffi_direction_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, s_num, (jlong)__boltffi_s_num_len, &__boltffi_s_num_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, v_cache, (jlong)__boltffi_v_cache_len, &__boltffi_v_cache_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_recommend_extra_fetch_home_feed((const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, (const uint8_t *)__boltffi_direction_ptr, (uintptr_t)__boltffi_direction_len, (const uint8_t *)__boltffi_s_num_ptr, (uintptr_t)__boltffi_s_num_len, (const uint8_t *)__boltffi_v_cache_ptr, (uintptr_t)__boltffi_v_cache_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1recommend_1extra_1fetch_1radar_1recommend(JNIEnv *env, jclass cls, jobject page, jint __boltffi_page_len) {
    (void)cls;

    void *__boltffi_page_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_recommend_extra_fetch_radar_recommend((const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1recommend_1extra_1fetch_1recommend_1playlists(JNIEnv *env, jclass cls, jobject page, jint __boltffi_page_len, jobject num, jint __boltffi_num_len) {
    (void)cls;

    void *__boltffi_page_ptr = NULL;
    void *__boltffi_num_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, num, (jlong)__boltffi_num_len, &__boltffi_num_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_recommend_extra_fetch_recommend_playlists((const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, (const uint8_t *)__boltffi_num_ptr, (uintptr_t)__boltffi_num_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1search_1extra_1fetch_1search_1hotkeys(JNIEnv *env, jclass cls) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_search_extra_fetch_search_hotkeys(&__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1search_1extra_1complete_1search(JNIEnv *env, jclass cls, jobject keyword, jint __boltffi_keyword_len) {
    (void)cls;

    void *__boltffi_keyword_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, keyword, (jlong)__boltffi_keyword_len, &__boltffi_keyword_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_search_extra_complete_search((const uint8_t *)__boltffi_keyword_ptr, (uintptr_t)__boltffi_keyword_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1search_1extra_1quick_1search(JNIEnv *env, jclass cls, jobject keyword, jint __boltffi_keyword_len) {
    (void)cls;

    void *__boltffi_keyword_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, keyword, (jlong)__boltffi_keyword_len, &__boltffi_keyword_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_search_extra_quick_search((const uint8_t *)__boltffi_keyword_ptr, (uintptr_t)__boltffi_keyword_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1search_1extra_1general_1search(JNIEnv *env, jclass cls, jobject keyword, jint __boltffi_keyword_len, jobject page, jint __boltffi_page_len, jobject num, jint __boltffi_num_len, jobject searchid, jint __boltffi_searchid_len, jobject page_start, jint __boltffi_page_start_len, jobject highlight, jint __boltffi_highlight_len) {
    (void)cls;

    void *__boltffi_keyword_ptr = NULL;
    void *__boltffi_page_ptr = NULL;
    void *__boltffi_num_ptr = NULL;
    void *__boltffi_searchid_ptr = NULL;
    void *__boltffi_page_start_ptr = NULL;
    void *__boltffi_highlight_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, keyword, (jlong)__boltffi_keyword_len, &__boltffi_keyword_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, num, (jlong)__boltffi_num_len, &__boltffi_num_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, searchid, (jlong)__boltffi_searchid_len, &__boltffi_searchid_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page_start, (jlong)__boltffi_page_start_len, &__boltffi_page_start_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, highlight, (jlong)__boltffi_highlight_len, &__boltffi_highlight_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_search_extra_general_search((const uint8_t *)__boltffi_keyword_ptr, (uintptr_t)__boltffi_keyword_len, (const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, (const uint8_t *)__boltffi_num_ptr, (uintptr_t)__boltffi_num_len, (const uint8_t *)__boltffi_searchid_ptr, (uintptr_t)__boltffi_searchid_len, (const uint8_t *)__boltffi_page_start_ptr, (uintptr_t)__boltffi_page_start_len, (const uint8_t *)__boltffi_highlight_ptr, (uintptr_t)__boltffi_highlight_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1search_1extra_1search_1extra(JNIEnv *env, jclass cls, jobject keyword, jint __boltffi_keyword_len, jobject search_type, jint __boltffi_search_type_len, jobject page, jint __boltffi_page_len, jobject num, jint __boltffi_num_len, jobject searchid, jint __boltffi_searchid_len, jobject selectors, jint __boltffi_selectors_len, jobject highlight, jint __boltffi_highlight_len) {
    (void)cls;

    void *__boltffi_keyword_ptr = NULL;
    void *__boltffi_search_type_ptr = NULL;
    void *__boltffi_page_ptr = NULL;
    void *__boltffi_num_ptr = NULL;
    void *__boltffi_searchid_ptr = NULL;
    void *__boltffi_selectors_ptr = NULL;
    void *__boltffi_highlight_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, keyword, (jlong)__boltffi_keyword_len, &__boltffi_keyword_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, search_type, (jlong)__boltffi_search_type_len, &__boltffi_search_type_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, num, (jlong)__boltffi_num_len, &__boltffi_num_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, searchid, (jlong)__boltffi_searchid_len, &__boltffi_searchid_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, selectors, (jlong)__boltffi_selectors_len, &__boltffi_selectors_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, highlight, (jlong)__boltffi_highlight_len, &__boltffi_highlight_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_search_extra_search_extra((const uint8_t *)__boltffi_keyword_ptr, (uintptr_t)__boltffi_keyword_len, (const uint8_t *)__boltffi_search_type_ptr, (uintptr_t)__boltffi_search_type_len, (const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, (const uint8_t *)__boltffi_num_ptr, (uintptr_t)__boltffi_num_len, (const uint8_t *)__boltffi_searchid_ptr, (uintptr_t)__boltffi_searchid_len, (const uint8_t *)__boltffi_selectors_ptr, (uintptr_t)__boltffi_selectors_len, (const uint8_t *)__boltffi_highlight_ptr, (uintptr_t)__boltffi_highlight_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1singer_1extra_1fetch_1singer_1list(JNIEnv *env, jclass cls, jobject area, jint __boltffi_area_len, jobject sex, jint __boltffi_sex_len, jobject genre, jint __boltffi_genre_len) {
    (void)cls;

    void *__boltffi_area_ptr = NULL;
    void *__boltffi_sex_ptr = NULL;
    void *__boltffi_genre_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, area, (jlong)__boltffi_area_len, &__boltffi_area_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, sex, (jlong)__boltffi_sex_len, &__boltffi_sex_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, genre, (jlong)__boltffi_genre_len, &__boltffi_genre_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_singer_list((const uint8_t *)__boltffi_area_ptr, (uintptr_t)__boltffi_area_len, (const uint8_t *)__boltffi_sex_ptr, (uintptr_t)__boltffi_sex_len, (const uint8_t *)__boltffi_genre_ptr, (uintptr_t)__boltffi_genre_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1singer_1extra_1fetch_1singer_1index(JNIEnv *env, jclass cls, jobject area, jint __boltffi_area_len, jobject sex, jint __boltffi_sex_len, jobject genre, jint __boltffi_genre_len, jobject index, jint __boltffi_index_len, jobject page, jint __boltffi_page_len, jobject num, jint __boltffi_num_len) {
    (void)cls;

    void *__boltffi_area_ptr = NULL;
    void *__boltffi_sex_ptr = NULL;
    void *__boltffi_genre_ptr = NULL;
    void *__boltffi_index_ptr = NULL;
    void *__boltffi_page_ptr = NULL;
    void *__boltffi_num_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, area, (jlong)__boltffi_area_len, &__boltffi_area_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, sex, (jlong)__boltffi_sex_len, &__boltffi_sex_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, genre, (jlong)__boltffi_genre_len, &__boltffi_genre_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, index, (jlong)__boltffi_index_len, &__boltffi_index_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, num, (jlong)__boltffi_num_len, &__boltffi_num_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_singer_index((const uint8_t *)__boltffi_area_ptr, (uintptr_t)__boltffi_area_len, (const uint8_t *)__boltffi_sex_ptr, (uintptr_t)__boltffi_sex_len, (const uint8_t *)__boltffi_genre_ptr, (uintptr_t)__boltffi_genre_len, (const uint8_t *)__boltffi_index_ptr, (uintptr_t)__boltffi_index_len, (const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, (const uint8_t *)__boltffi_num_ptr, (uintptr_t)__boltffi_num_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1singer_1extra_1fetch_1similar_1artists(JNIEnv *env, jclass cls, jobject singer_mid, jint __boltffi_singer_mid_len, jobject number, jint __boltffi_number_len) {
    (void)cls;

    void *__boltffi_singer_mid_ptr = NULL;
    void *__boltffi_number_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, singer_mid, (jlong)__boltffi_singer_mid_len, &__boltffi_singer_mid_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, number, (jlong)__boltffi_number_len, &__boltffi_number_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_similar_artists((const uint8_t *)__boltffi_singer_mid_ptr, (uintptr_t)__boltffi_singer_mid_len, (const uint8_t *)__boltffi_number_ptr, (uintptr_t)__boltffi_number_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1singer_1extra_1fetch_1artist_1tab(JNIEnv *env, jclass cls, jobject singer_mid, jint __boltffi_singer_mid_len, jobject tab_type, jint __boltffi_tab_type_len, jobject page, jint __boltffi_page_len, jobject num, jint __boltffi_num_len) {
    (void)cls;

    void *__boltffi_singer_mid_ptr = NULL;
    void *__boltffi_tab_type_ptr = NULL;
    void *__boltffi_page_ptr = NULL;
    void *__boltffi_num_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, singer_mid, (jlong)__boltffi_singer_mid_len, &__boltffi_singer_mid_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, tab_type, (jlong)__boltffi_tab_type_len, &__boltffi_tab_type_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, num, (jlong)__boltffi_num_len, &__boltffi_num_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_artist_tab((const uint8_t *)__boltffi_singer_mid_ptr, (uintptr_t)__boltffi_singer_mid_len, (const uint8_t *)__boltffi_tab_type_ptr, (uintptr_t)__boltffi_tab_type_len, (const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, (const uint8_t *)__boltffi_num_ptr, (uintptr_t)__boltffi_num_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1singer_1extra_1fetch_1artist_1display_1name(JNIEnv *env, jclass cls, jobject singer_mid, jint __boltffi_singer_mid_len) {
    (void)cls;

    void *__boltffi_singer_mid_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, singer_mid, (jlong)__boltffi_singer_mid_len, &__boltffi_singer_mid_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_artist_display_name((const uint8_t *)__boltffi_singer_mid_ptr, (uintptr_t)__boltffi_singer_mid_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1singer_1extra_1fetch_1artist_1mvs(JNIEnv *env, jclass cls, jobject singer_mid, jint __boltffi_singer_mid_len, jobject num, jint __boltffi_num_len, jobject page, jint __boltffi_page_len) {
    (void)cls;

    void *__boltffi_singer_mid_ptr = NULL;
    void *__boltffi_num_ptr = NULL;
    void *__boltffi_page_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, singer_mid, (jlong)__boltffi_singer_mid_len, &__boltffi_singer_mid_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, num, (jlong)__boltffi_num_len, &__boltffi_num_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_artist_mvs((const uint8_t *)__boltffi_singer_mid_ptr, (uintptr_t)__boltffi_singer_mid_len, (const uint8_t *)__boltffi_num_ptr, (uintptr_t)__boltffi_num_len, (const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1song_1asset_1query_1songs(JNIEnv *env, jclass cls, jobject songs, jint __boltffi_songs_len) {
    (void)cls;

    void *__boltffi_songs_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, songs, (jlong)__boltffi_songs_len, &__boltffi_songs_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_song_asset_query_songs((const uint8_t *)__boltffi_songs_ptr, (uintptr_t)__boltffi_songs_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1song_1asset_1fetch_1cdn_1dispatch(JNIEnv *env, jclass cls) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_song_asset_fetch_cdn_dispatch(&__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1song_1asset_1resolve_1song_1urls(JNIEnv *env, jclass cls, jobject file_info, jint __boltffi_file_info_len, jobject file_type, jint __boltffi_file_type_len) {
    (void)cls;

    void *__boltffi_file_info_ptr = NULL;
    void *__boltffi_file_type_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, file_info, (jlong)__boltffi_file_info_len, &__boltffi_file_info_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, file_type, (jlong)__boltffi_file_type_len, &__boltffi_file_type_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_song_asset_resolve_song_urls((const uint8_t *)__boltffi_file_info_ptr, (uintptr_t)__boltffi_file_info_len, (const uint8_t *)__boltffi_file_type_ptr, (uintptr_t)__boltffi_file_type_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1song_1asset_1fetch_1other_1versions(JNIEnv *env, jclass cls, jobject value, jint __boltffi_value_len) {
    (void)cls;

    void *__boltffi_value_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, value, (jlong)__boltffi_value_len, &__boltffi_value_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_song_asset_fetch_other_versions((const uint8_t *)__boltffi_value_ptr, (uintptr_t)__boltffi_value_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1song_1asset_1fetch_1song_1producer(JNIEnv *env, jclass cls, jobject value, jint __boltffi_value_len) {
    (void)cls;

    void *__boltffi_value_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, value, (jlong)__boltffi_value_len, &__boltffi_value_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_song_asset_fetch_song_producer((const uint8_t *)__boltffi_value_ptr, (uintptr_t)__boltffi_value_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1song_1asset_1fetch_1song_1fav_1count(JNIEnv *env, jclass cls, jlongArray song_ids) {
    (void)cls;

    jlong *__boltffi_song_ids_ptr = NULL;
    jsize __boltffi_song_ids_len = 0;
    jlong __boltffi_song_ids_stack[8];
    bool __boltffi_song_ids_needs_release = false;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (song_ids == NULL) {
        boltffi_jni_throw_illegal_argument(env, "BoltFFI array argument was null");
        goto __boltffi_error;
    }
    __boltffi_song_ids_len = (*env)->GetArrayLength(env, song_ids);
    if (__boltffi_song_ids_len <= (jsize)8) {
        (*env)->GetLongArrayRegion(env, song_ids, 0, __boltffi_song_ids_len, __boltffi_song_ids_stack);
        if ((*env)->ExceptionCheck(env)) {
            goto __boltffi_error;
        }
        __boltffi_song_ids_ptr = __boltffi_song_ids_stack;
    } else {
        __boltffi_song_ids_ptr = (*env)->GetLongArrayElements(env, song_ids, NULL);
        if (__boltffi_song_ids_ptr == NULL) {
            goto __boltffi_error;
        }
        __boltffi_song_ids_needs_release = true;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_song_asset_fetch_song_fav_count((const int64_t *)__boltffi_song_ids_ptr, (uintptr_t)__boltffi_song_ids_len, &__boltffi_return);

    if (__boltffi_song_ids_ptr != NULL) {
        if (__boltffi_song_ids_needs_release) {
            (*env)->ReleaseLongArrayElements(env, song_ids, __boltffi_song_ids_ptr, JNI_ABORT);
        }
        __boltffi_song_ids_ptr = NULL;
    }
    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    if (__boltffi_song_ids_ptr != NULL) {
        if (__boltffi_song_ids_needs_release) {
            (*env)->ReleaseLongArrayElements(env, song_ids, __boltffi_song_ids_ptr, JNI_ABORT);
        }
        __boltffi_song_ids_ptr = NULL;
    }
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1song_1related_1fetch_1similar_1songs(JNIEnv *env, jclass cls, jlong song_id) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_similar_songs(song_id, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1song_1related_1fetch_1song_1labels(JNIEnv *env, jclass cls, jlong song_id) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_song_labels(song_id, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1song_1related_1fetch_1related_1playlists(JNIEnv *env, jclass cls, jlong song_id, jobject last, jint __boltffi_last_len) {
    (void)cls;

    void *__boltffi_last_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, last, (jlong)__boltffi_last_len, &__boltffi_last_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_related_playlists(song_id, (const uint8_t *)__boltffi_last_ptr, (uintptr_t)__boltffi_last_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1song_1related_1fetch_1related_1mvs(JNIEnv *env, jclass cls, jlong song_id, jobject last_mvid, jint __boltffi_last_mvid_len) {
    (void)cls;

    void *__boltffi_last_mvid_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, last_mvid, (jlong)__boltffi_last_mvid_len, &__boltffi_last_mvid_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_related_mvs(song_id, (const uint8_t *)__boltffi_last_mvid_ptr, (uintptr_t)__boltffi_last_mvid_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1song_1related_1fetch_1sheet_1music(JNIEnv *env, jclass cls, jobject mid, jint __boltffi_mid_len, jobject ttype, jint __boltffi_ttype_len) {
    (void)cls;

    void *__boltffi_mid_ptr = NULL;
    void *__boltffi_ttype_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, mid, (jlong)__boltffi_mid_len, &__boltffi_mid_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, ttype, (jlong)__boltffi_ttype_len, &__boltffi_ttype_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_sheet_music((const uint8_t *)__boltffi_mid_ptr, (uintptr_t)__boltffi_mid_len, (const uint8_t *)__boltffi_ttype_ptr, (uintptr_t)__boltffi_ttype_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1song_1related_1has_1sheet_1music(JNIEnv *env, jclass cls, jobject mid, jint __boltffi_mid_len) {
    (void)cls;

    void *__boltffi_mid_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, mid, (jlong)__boltffi_mid_len, &__boltffi_mid_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_song_related_has_sheet_music((const uint8_t *)__boltffi_mid_ptr, (uintptr_t)__boltffi_mid_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1user_1asset_1fetch_1fav_1playlists(JNIEnv *env, jclass cls, jobject euin, jint __boltffi_euin_len, jobject page, jint __boltffi_page_len, jobject num, jint __boltffi_num_len) {
    (void)cls;

    void *__boltffi_euin_ptr = NULL;
    void *__boltffi_page_ptr = NULL;
    void *__boltffi_num_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, euin, (jlong)__boltffi_euin_len, &__boltffi_euin_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, num, (jlong)__boltffi_num_len, &__boltffi_num_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_fav_playlists((const uint8_t *)__boltffi_euin_ptr, (uintptr_t)__boltffi_euin_len, (const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, (const uint8_t *)__boltffi_num_ptr, (uintptr_t)__boltffi_num_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1user_1asset_1fetch_1fav_1albums(JNIEnv *env, jclass cls, jobject euin, jint __boltffi_euin_len, jobject page, jint __boltffi_page_len, jobject num, jint __boltffi_num_len) {
    (void)cls;

    void *__boltffi_euin_ptr = NULL;
    void *__boltffi_page_ptr = NULL;
    void *__boltffi_num_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, euin, (jlong)__boltffi_euin_len, &__boltffi_euin_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, num, (jlong)__boltffi_num_len, &__boltffi_num_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_fav_albums((const uint8_t *)__boltffi_euin_ptr, (uintptr_t)__boltffi_euin_len, (const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, (const uint8_t *)__boltffi_num_ptr, (uintptr_t)__boltffi_num_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1user_1asset_1fetch_1fav_1mvs(JNIEnv *env, jclass cls, jobject euin, jint __boltffi_euin_len, jobject page, jint __boltffi_page_len, jobject num, jint __boltffi_num_len) {
    (void)cls;

    void *__boltffi_euin_ptr = NULL;
    void *__boltffi_page_ptr = NULL;
    void *__boltffi_num_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, euin, (jlong)__boltffi_euin_len, &__boltffi_euin_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, num, (jlong)__boltffi_num_len, &__boltffi_num_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_fav_mvs((const uint8_t *)__boltffi_euin_ptr, (uintptr_t)__boltffi_euin_len, (const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, (const uint8_t *)__boltffi_num_ptr, (uintptr_t)__boltffi_num_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1user_1asset_1fetch_1music_1gene(JNIEnv *env, jclass cls, jobject euin, jint __boltffi_euin_len) {
    (void)cls;

    void *__boltffi_euin_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, euin, (jlong)__boltffi_euin_len, &__boltffi_euin_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_music_gene((const uint8_t *)__boltffi_euin_ptr, (uintptr_t)__boltffi_euin_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1user_1asset_1fetch_1dislike_1list(JNIEnv *env, jclass cls, jobject cmd, jint __boltffi_cmd_len, jobject page, jint __boltffi_page_len, jobject lastid, jint __boltffi_lastid_len) {
    (void)cls;

    void *__boltffi_cmd_ptr = NULL;
    void *__boltffi_page_ptr = NULL;
    void *__boltffi_lastid_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, cmd, (jlong)__boltffi_cmd_len, &__boltffi_cmd_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, lastid, (jlong)__boltffi_lastid_len, &__boltffi_lastid_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_dislike_list((const uint8_t *)__boltffi_cmd_ptr, (uintptr_t)__boltffi_cmd_len, (const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, (const uint8_t *)__boltffi_lastid_ptr, (uintptr_t)__boltffi_lastid_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jboolean JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1user_1asset_1add_1dislike(JNIEnv *env, jclass cls, jlong id_type, jlongArray values) {
    (void)cls;

    jlong *__boltffi_values_ptr = NULL;
    jsize __boltffi_values_len = 0;
    jlong __boltffi_values_stack[8];
    bool __boltffi_values_needs_release = false;
    bool __boltffi_return = (bool){0};

    if (values == NULL) {
        boltffi_jni_throw_illegal_argument(env, "BoltFFI array argument was null");
        goto __boltffi_error;
    }
    __boltffi_values_len = (*env)->GetArrayLength(env, values);
    if (__boltffi_values_len <= (jsize)8) {
        (*env)->GetLongArrayRegion(env, values, 0, __boltffi_values_len, __boltffi_values_stack);
        if ((*env)->ExceptionCheck(env)) {
            goto __boltffi_error;
        }
        __boltffi_values_ptr = __boltffi_values_stack;
    } else {
        __boltffi_values_ptr = (*env)->GetLongArrayElements(env, values, NULL);
        if (__boltffi_values_ptr == NULL) {
            goto __boltffi_error;
        }
        __boltffi_values_needs_release = true;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_user_asset_add_dislike(id_type, (const int64_t *)__boltffi_values_ptr, (uintptr_t)__boltffi_values_len, &__boltffi_return);

    if (__boltffi_values_ptr != NULL) {
        if (__boltffi_values_needs_release) {
            (*env)->ReleaseLongArrayElements(env, values, __boltffi_values_ptr, JNI_ABORT);
        }
        __boltffi_values_ptr = NULL;
    }
    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return JNI_FALSE;
    }

    return (jboolean)__boltffi_return;
__boltffi_error:
    if (__boltffi_values_ptr != NULL) {
        if (__boltffi_values_needs_release) {
            (*env)->ReleaseLongArrayElements(env, values, __boltffi_values_ptr, JNI_ABORT);
        }
        __boltffi_values_ptr = NULL;
    }
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1user_1asset_1cancel_1dislike(JNIEnv *env, jclass cls, jlong id_type, jlongArray values) {
    (void)cls;

    jlong *__boltffi_values_ptr = NULL;
    jsize __boltffi_values_len = 0;
    jlong __boltffi_values_stack[8];
    bool __boltffi_values_needs_release = false;
    bool __boltffi_return = (bool){0};

    if (values == NULL) {
        boltffi_jni_throw_illegal_argument(env, "BoltFFI array argument was null");
        goto __boltffi_error;
    }
    __boltffi_values_len = (*env)->GetArrayLength(env, values);
    if (__boltffi_values_len <= (jsize)8) {
        (*env)->GetLongArrayRegion(env, values, 0, __boltffi_values_len, __boltffi_values_stack);
        if ((*env)->ExceptionCheck(env)) {
            goto __boltffi_error;
        }
        __boltffi_values_ptr = __boltffi_values_stack;
    } else {
        __boltffi_values_ptr = (*env)->GetLongArrayElements(env, values, NULL);
        if (__boltffi_values_ptr == NULL) {
            goto __boltffi_error;
        }
        __boltffi_values_needs_release = true;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_user_asset_cancel_dislike(id_type, (const int64_t *)__boltffi_values_ptr, (uintptr_t)__boltffi_values_len, &__boltffi_return);

    if (__boltffi_values_ptr != NULL) {
        if (__boltffi_values_needs_release) {
            (*env)->ReleaseLongArrayElements(env, values, __boltffi_values_ptr, JNI_ABORT);
        }
        __boltffi_values_ptr = NULL;
    }
    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return JNI_FALSE;
    }

    return (jboolean)__boltffi_return;
__boltffi_error:
    if (__boltffi_values_ptr != NULL) {
        if (__boltffi_values_needs_release) {
            (*env)->ReleaseLongArrayElements(env, values, __boltffi_values_ptr, JNI_ABORT);
        }
        __boltffi_values_ptr = NULL;
    }
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1user_1asset_1clear_1dislike_1songs(JNIEnv *env, jclass cls) {
    (void)cls;

    bool __boltffi_return = (bool){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_user_asset_clear_dislike_songs(&__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return JNI_FALSE;
    }

    return (jboolean)__boltffi_return;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1user_1relation_1fetch_1user_1homepage(JNIEnv *env, jclass cls, jobject euin, jint __boltffi_euin_len) {
    (void)cls;

    void *__boltffi_euin_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, euin, (jlong)__boltffi_euin_len, &__boltffi_euin_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_user_homepage((const uint8_t *)__boltffi_euin_ptr, (uintptr_t)__boltffi_euin_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1user_1relation_1fetch_1vip_1info(JNIEnv *env, jclass cls) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_vip_info(&__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1user_1relation_1fetch_1follow_1singers(JNIEnv *env, jclass cls, jobject euin, jint __boltffi_euin_len, jobject page, jint __boltffi_page_len, jobject num, jint __boltffi_num_len) {
    (void)cls;

    void *__boltffi_euin_ptr = NULL;
    void *__boltffi_page_ptr = NULL;
    void *__boltffi_num_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, euin, (jlong)__boltffi_euin_len, &__boltffi_euin_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, num, (jlong)__boltffi_num_len, &__boltffi_num_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_follow_singers((const uint8_t *)__boltffi_euin_ptr, (uintptr_t)__boltffi_euin_len, (const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, (const uint8_t *)__boltffi_num_ptr, (uintptr_t)__boltffi_num_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1user_1relation_1fetch_1follow_1singers_1at_1offset(JNIEnv *env, jclass cls, jobject euin, jint __boltffi_euin_len, jlong offset, jobject num, jint __boltffi_num_len) {
    (void)cls;

    void *__boltffi_euin_ptr = NULL;
    void *__boltffi_num_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, euin, (jlong)__boltffi_euin_len, &__boltffi_euin_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, num, (jlong)__boltffi_num_len, &__boltffi_num_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_follow_singers_at_offset((const uint8_t *)__boltffi_euin_ptr, (uintptr_t)__boltffi_euin_len, offset, (const uint8_t *)__boltffi_num_ptr, (uintptr_t)__boltffi_num_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1user_1relation_1fetch_1fans(JNIEnv *env, jclass cls, jobject euin, jint __boltffi_euin_len, jobject page, jint __boltffi_page_len, jobject num, jint __boltffi_num_len) {
    (void)cls;

    void *__boltffi_euin_ptr = NULL;
    void *__boltffi_page_ptr = NULL;
    void *__boltffi_num_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, euin, (jlong)__boltffi_euin_len, &__boltffi_euin_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, num, (jlong)__boltffi_num_len, &__boltffi_num_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_fans((const uint8_t *)__boltffi_euin_ptr, (uintptr_t)__boltffi_euin_len, (const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, (const uint8_t *)__boltffi_num_ptr, (uintptr_t)__boltffi_num_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1user_1relation_1fetch_1friends(JNIEnv *env, jclass cls, jobject page, jint __boltffi_page_len, jobject num, jint __boltffi_num_len) {
    (void)cls;

    void *__boltffi_page_ptr = NULL;
    void *__boltffi_num_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, num, (jlong)__boltffi_num_len, &__boltffi_num_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_friends((const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, (const uint8_t *)__boltffi_num_ptr, (uintptr_t)__boltffi_num_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1user_1relation_1fetch_1followed_1users(JNIEnv *env, jclass cls, jobject euin, jint __boltffi_euin_len, jobject page, jint __boltffi_page_len, jobject num, jint __boltffi_num_len) {
    (void)cls;

    void *__boltffi_euin_ptr = NULL;
    void *__boltffi_page_ptr = NULL;
    void *__boltffi_num_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, euin, (jlong)__boltffi_euin_len, &__boltffi_euin_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, page, (jlong)__boltffi_page_len, &__boltffi_page_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, num, (jlong)__boltffi_num_len, &__boltffi_num_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_followed_users((const uint8_t *)__boltffi_euin_ptr, (uintptr_t)__boltffi_euin_len, (const uint8_t *)__boltffi_page_ptr, (uintptr_t)__boltffi_page_len, (const uint8_t *)__boltffi_num_ptr, (uintptr_t)__boltffi_num_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1port_1user_1relation_1fetch_1created_1playlists(JNIEnv *env, jclass cls, jobject uin, jint __boltffi_uin_len) {
    (void)cls;

    void *__boltffi_uin_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, uin, (jlong)__boltffi_uin_len, &__boltffi_uin_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_created_playlists((const uint8_t *)__boltffi_uin_ptr, (uintptr_t)__boltffi_uin_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1login_1status(JNIEnv *env, jclass cls) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_login_status(&__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT void JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1import_1credential(JNIEnv *env, jclass cls, jobject uin, jint __boltffi_uin_len, jobject qm_keyst, jint __boltffi_qm_keyst_len) {
    (void)cls;

    void *__boltffi_uin_ptr = NULL;
    void *__boltffi_qm_keyst_ptr = NULL;

    if (!boltffi_jni_direct_buffer_address(env, uin, (jlong)__boltffi_uin_len, &__boltffi_uin_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, qm_keyst, (jlong)__boltffi_qm_keyst_len, &__boltffi_qm_keyst_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_import_credential((const uint8_t *)__boltffi_uin_ptr, (uintptr_t)__boltffi_uin_len, (const uint8_t *)__boltffi_qm_keyst_ptr, (uintptr_t)__boltffi_qm_keyst_len);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return;
    }

    return;
__boltffi_error:
    return;
}

JNIEXPORT void JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1import_1credential_1with_1encrypt_1uin(JNIEnv *env, jclass cls, jobject uin, jint __boltffi_uin_len, jobject qm_keyst, jint __boltffi_qm_keyst_len, jobject encrypt_uin, jint __boltffi_encrypt_uin_len) {
    (void)cls;

    void *__boltffi_uin_ptr = NULL;
    void *__boltffi_qm_keyst_ptr = NULL;
    void *__boltffi_encrypt_uin_ptr = NULL;

    if (!boltffi_jni_direct_buffer_address(env, uin, (jlong)__boltffi_uin_len, &__boltffi_uin_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, qm_keyst, (jlong)__boltffi_qm_keyst_len, &__boltffi_qm_keyst_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, encrypt_uin, (jlong)__boltffi_encrypt_uin_len, &__boltffi_encrypt_uin_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_import_credential_with_encrypt_uin((const uint8_t *)__boltffi_uin_ptr, (uintptr_t)__boltffi_uin_len, (const uint8_t *)__boltffi_qm_keyst_ptr, (uintptr_t)__boltffi_qm_keyst_len, (const uint8_t *)__boltffi_encrypt_uin_ptr, (uintptr_t)__boltffi_encrypt_uin_len);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return;
    }

    return;
__boltffi_error:
    return;
}

JNIEXPORT void JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1logout(JNIEnv *env, jclass cls) {
    (void)cls;

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_logout();

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return;
    }

    return;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1liked_1songs(JNIEnv *env, jclass cls, jint page, jint limit) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_liked_songs(page, limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1playlist_1tracks(JNIEnv *env, jclass cls, jlong list_id, jint offset, jint limit) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_playlist_tracks(list_id, offset, limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1playlist_1tracks_1page(JNIEnv *env, jclass cls, jlong list_id, jobject dir_id, jint __boltffi_dir_id_len, jint offset, jint limit) {
    (void)cls;

    void *__boltffi_dir_id_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, dir_id, (jlong)__boltffi_dir_id_len, &__boltffi_dir_id_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_playlist_tracks_page(list_id, (const uint8_t *)__boltffi_dir_id_ptr, (uintptr_t)__boltffi_dir_id_len, offset, limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1set_1liked_1by_1id(JNIEnv *env, jclass cls, jlong song_id, jboolean liked) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_set_liked_by_id(song_id, liked, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1user_1playlists(JNIEnv *env, jclass cls, jint limit) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_user_playlists(limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1liked_1albums(JNIEnv *env, jclass cls, jint limit) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_liked_albums(limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1followed_1artists(JNIEnv *env, jclass cls, jint page, jint limit) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_followed_artists(page, limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1component_1info(JNIEnv *env, jclass cls) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_component_info(&__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1guard_1status(JNIEnv *env, jclass cls) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_guard_status(&__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1song_1detail(JNIEnv *env, jclass cls, jobject song_mid, jint __boltffi_song_mid_len) {
    (void)cls;

    void *__boltffi_song_mid_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, song_mid, (jlong)__boltffi_song_mid_len, &__boltffi_song_mid_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_song_detail((const uint8_t *)__boltffi_song_mid_ptr, (uintptr_t)__boltffi_song_mid_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1album_1detail(JNIEnv *env, jclass cls, jobject album_mid, jint __boltffi_album_mid_len, jobject album_id, jint __boltffi_album_id_len) {
    (void)cls;

    void *__boltffi_album_mid_ptr = NULL;
    void *__boltffi_album_id_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, album_mid, (jlong)__boltffi_album_mid_len, &__boltffi_album_mid_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, album_id, (jlong)__boltffi_album_id_len, &__boltffi_album_id_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_album_detail((const uint8_t *)__boltffi_album_mid_ptr, (uintptr_t)__boltffi_album_mid_len, (const uint8_t *)__boltffi_album_id_ptr, (uintptr_t)__boltffi_album_id_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1album_1tracks(JNIEnv *env, jclass cls, jobject album_mid, jint __boltffi_album_mid_len, jobject album_id, jint __boltffi_album_id_len, jlong offset, jlong limit) {
    (void)cls;

    void *__boltffi_album_mid_ptr = NULL;
    void *__boltffi_album_id_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, album_mid, (jlong)__boltffi_album_mid_len, &__boltffi_album_mid_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, album_id, (jlong)__boltffi_album_id_len, &__boltffi_album_id_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_album_tracks((const uint8_t *)__boltffi_album_mid_ptr, (uintptr_t)__boltffi_album_mid_len, (const uint8_t *)__boltffi_album_id_ptr, (uintptr_t)__boltffi_album_id_len, offset, limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1artist_1songs(JNIEnv *env, jclass cls, jobject singer_mid, jint __boltffi_singer_mid_len, jobject sort, jint __boltffi_sort_len, jlong page, jlong limit) {
    (void)cls;

    void *__boltffi_singer_mid_ptr = NULL;
    void *__boltffi_sort_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, singer_mid, (jlong)__boltffi_singer_mid_len, &__boltffi_singer_mid_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, sort, (jlong)__boltffi_sort_len, &__boltffi_sort_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_artist_songs((const uint8_t *)__boltffi_singer_mid_ptr, (uintptr_t)__boltffi_singer_mid_len, (const uint8_t *)__boltffi_sort_ptr, (uintptr_t)__boltffi_sort_len, page, limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1artist_1songs_1page(JNIEnv *env, jclass cls, jobject singer_mid, jint __boltffi_singer_mid_len, jobject sort, jint __boltffi_sort_len, jint offset, jint limit) {
    (void)cls;

    void *__boltffi_singer_mid_ptr = NULL;
    void *__boltffi_sort_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, singer_mid, (jlong)__boltffi_singer_mid_len, &__boltffi_singer_mid_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, sort, (jlong)__boltffi_sort_len, &__boltffi_sort_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_artist_songs_page((const uint8_t *)__boltffi_singer_mid_ptr, (uintptr_t)__boltffi_singer_mid_len, (const uint8_t *)__boltffi_sort_ptr, (uintptr_t)__boltffi_sort_len, offset, limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1artist_1albums(JNIEnv *env, jclass cls, jobject singer_mid, jint __boltffi_singer_mid_len, jobject sort, jint __boltffi_sort_len, jlong page, jlong limit) {
    (void)cls;

    void *__boltffi_singer_mid_ptr = NULL;
    void *__boltffi_sort_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, singer_mid, (jlong)__boltffi_singer_mid_len, &__boltffi_singer_mid_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, sort, (jlong)__boltffi_sort_len, &__boltffi_sort_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_artist_albums((const uint8_t *)__boltffi_singer_mid_ptr, (uintptr_t)__boltffi_singer_mid_len, (const uint8_t *)__boltffi_sort_ptr, (uintptr_t)__boltffi_sort_len, page, limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1artist_1albums_1page(JNIEnv *env, jclass cls, jobject singer_mid, jint __boltffi_singer_mid_len, jobject sort, jint __boltffi_sort_len, jint offset, jint limit) {
    (void)cls;

    void *__boltffi_singer_mid_ptr = NULL;
    void *__boltffi_sort_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, singer_mid, (jlong)__boltffi_singer_mid_len, &__boltffi_singer_mid_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, sort, (jlong)__boltffi_sort_len, &__boltffi_sort_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_artist_albums_page((const uint8_t *)__boltffi_singer_mid_ptr, (uintptr_t)__boltffi_singer_mid_len, (const uint8_t *)__boltffi_sort_ptr, (uintptr_t)__boltffi_sort_len, offset, limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1artist_1detail(JNIEnv *env, jclass cls, jobject singer_mid, jint __boltffi_singer_mid_len) {
    (void)cls;

    void *__boltffi_singer_mid_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, singer_mid, (jlong)__boltffi_singer_mid_len, &__boltffi_singer_mid_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_artist_detail((const uint8_t *)__boltffi_singer_mid_ptr, (uintptr_t)__boltffi_singer_mid_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1toplist_1categories(JNIEnv *env, jclass cls) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_toplist_categories(&__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1toplist_1tracks(JNIEnv *env, jclass cls, jlong top_id, jlong offset, jlong limit) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_toplist_tracks(top_id, offset, limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1radio_1stations(JNIEnv *env, jclass cls) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_radio_stations(&__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1radio_1tracks(JNIEnv *env, jclass cls, jlong station_id, jlong limit, jboolean first_play) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_radio_tracks(station_id, limit, first_play, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1radio_1track_1batch(JNIEnv *env, jclass cls, jlong station_id, jboolean first_play) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_radio_track_batch(station_id, first_play, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1new_1songs(JNIEnv *env, jclass cls, jlong region_type) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_new_songs(region_type, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1recommend_1feed(JNIEnv *env, jclass cls) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_recommend_feed(&__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1lyric(JNIEnv *env, jclass cls, jobject song_mid, jint __boltffi_song_mid_len, jobject song_id, jint __boltffi_song_id_len, jboolean word_timing, jboolean translation) {
    (void)cls;

    void *__boltffi_song_mid_ptr = NULL;
    void *__boltffi_song_id_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, song_mid, (jlong)__boltffi_song_mid_len, &__boltffi_song_mid_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, song_id, (jlong)__boltffi_song_id_len, &__boltffi_song_id_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_lyric((const uint8_t *)__boltffi_song_mid_ptr, (uintptr_t)__boltffi_song_mid_len, (const uint8_t *)__boltffi_song_id_ptr, (uintptr_t)__boltffi_song_id_len, word_timing, translation, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1resolve_1song_1url(JNIEnv *env, jclass cls, jobject song_mid, jint __boltffi_song_mid_len, jobject media_mid, jint __boltffi_media_mid_len, jlong song_type, jobject preferred_quality, jint __boltffi_preferred_quality_len) {
    (void)cls;

    void *__boltffi_song_mid_ptr = NULL;
    void *__boltffi_media_mid_ptr = NULL;
    void *__boltffi_preferred_quality_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, song_mid, (jlong)__boltffi_song_mid_len, &__boltffi_song_mid_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, media_mid, (jlong)__boltffi_media_mid_len, &__boltffi_media_mid_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, preferred_quality, (jlong)__boltffi_preferred_quality_len, &__boltffi_preferred_quality_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_resolve_song_url((const uint8_t *)__boltffi_song_mid_ptr, (uintptr_t)__boltffi_song_mid_len, (const uint8_t *)__boltffi_media_mid_ptr, (uintptr_t)__boltffi_media_mid_len, song_type, (const uint8_t *)__boltffi_preferred_quality_ptr, (uintptr_t)__boltffi_preferred_quality_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1call_1with_1platform(JNIEnv *env, jclass cls, jobject method, jint __boltffi_method_len, jobject params_json, jint __boltffi_params_json_len, jobject platform, jint __boltffi_platform_len) {
    (void)cls;

    void *__boltffi_method_ptr = NULL;
    void *__boltffi_params_json_ptr = NULL;
    void *__boltffi_platform_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, method, (jlong)__boltffi_method_len, &__boltffi_method_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, params_json, (jlong)__boltffi_params_json_len, &__boltffi_params_json_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, platform, (jlong)__boltffi_platform_len, &__boltffi_platform_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_call_with_platform((const uint8_t *)__boltffi_method_ptr, (uintptr_t)__boltffi_method_len, (const uint8_t *)__boltffi_params_json_ptr, (uintptr_t)__boltffi_params_json_len, (const uint8_t *)__boltffi_platform_ptr, (uintptr_t)__boltffi_platform_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT void JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1set_1liked(JNIEnv *env, jclass cls, jobject song_mid, jint __boltffi_song_mid_len, jlong song_type, jboolean liked) {
    (void)cls;

    void *__boltffi_song_mid_ptr = NULL;

    if (!boltffi_jni_direct_buffer_address(env, song_mid, (jlong)__boltffi_song_mid_len, &__boltffi_song_mid_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_set_liked((const uint8_t *)__boltffi_song_mid_ptr, (uintptr_t)__boltffi_song_mid_len, song_type, liked);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return;
    }

    return;
__boltffi_error:
    return;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1start_1login(JNIEnv *env, jclass cls) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_start_login(&__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1poll_1login(JNIEnv *env, jclass cls, jobject identifier, jint __boltffi_identifier_len) {
    (void)cls;

    void *__boltffi_identifier_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, identifier, (jlong)__boltffi_identifier_len, &__boltffi_identifier_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_poll_login((const uint8_t *)__boltffi_identifier_ptr, (uintptr_t)__boltffi_identifier_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1search_1songs(JNIEnv *env, jclass cls, jobject keyword, jint __boltffi_keyword_len, jlong page, jlong limit) {
    (void)cls;

    void *__boltffi_keyword_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, keyword, (jlong)__boltffi_keyword_len, &__boltffi_keyword_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_search_songs((const uint8_t *)__boltffi_keyword_ptr, (uintptr_t)__boltffi_keyword_len, page, limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1search_1artists(JNIEnv *env, jclass cls, jobject keyword, jint __boltffi_keyword_len, jlong page, jlong limit) {
    (void)cls;

    void *__boltffi_keyword_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, keyword, (jlong)__boltffi_keyword_len, &__boltffi_keyword_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_search_artists((const uint8_t *)__boltffi_keyword_ptr, (uintptr_t)__boltffi_keyword_len, page, limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1search_1albums(JNIEnv *env, jclass cls, jobject keyword, jint __boltffi_keyword_len, jlong page, jlong limit) {
    (void)cls;

    void *__boltffi_keyword_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, keyword, (jlong)__boltffi_keyword_len, &__boltffi_keyword_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_search_albums((const uint8_t *)__boltffi_keyword_ptr, (uintptr_t)__boltffi_keyword_len, page, limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1search_1playlists(JNIEnv *env, jclass cls, jobject keyword, jint __boltffi_keyword_len, jlong page, jlong limit) {
    (void)cls;

    void *__boltffi_keyword_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, keyword, (jlong)__boltffi_keyword_len, &__boltffi_keyword_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_search_playlists((const uint8_t *)__boltffi_keyword_ptr, (uintptr_t)__boltffi_keyword_len, page, limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1search_1track_1artwork(JNIEnv *env, jclass cls, jobject title, jint __boltffi_title_len, jobject artist, jint __boltffi_artist_len, jobject album, jint __boltffi_album_len, jlong limit) {
    (void)cls;

    void *__boltffi_title_ptr = NULL;
    void *__boltffi_artist_ptr = NULL;
    void *__boltffi_album_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, title, (jlong)__boltffi_title_len, &__boltffi_title_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, artist, (jlong)__boltffi_artist_len, &__boltffi_artist_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, album, (jlong)__boltffi_album_len, &__boltffi_album_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_search_track_artwork((const uint8_t *)__boltffi_title_ptr, (uintptr_t)__boltffi_title_len, (const uint8_t *)__boltffi_artist_ptr, (uintptr_t)__boltffi_artist_len, (const uint8_t *)__boltffi_album_ptr, (uintptr_t)__boltffi_album_len, limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1search_1artist_1artwork(JNIEnv *env, jclass cls, jobject name, jint __boltffi_name_len, jlong limit) {
    (void)cls;

    void *__boltffi_name_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, name, (jlong)__boltffi_name_len, &__boltffi_name_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_search_artist_artwork((const uint8_t *)__boltffi_name_ptr, (uintptr_t)__boltffi_name_len, limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1search_1album_1artwork(JNIEnv *env, jclass cls, jobject album, jint __boltffi_album_len, jobject artist, jint __boltffi_artist_len, jlong limit) {
    (void)cls;

    void *__boltffi_album_ptr = NULL;
    void *__boltffi_artist_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, album, (jlong)__boltffi_album_len, &__boltffi_album_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, artist, (jlong)__boltffi_artist_len, &__boltffi_artist_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_search_album_artwork((const uint8_t *)__boltffi_album_ptr, (uintptr_t)__boltffi_album_len, (const uint8_t *)__boltffi_artist_ptr, (uintptr_t)__boltffi_artist_len, limit, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1set_1rate_1limit(JNIEnv *env, jclass cls, jboolean enabled, jlong window_seconds, jlong max_requests) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_set_rate_limit(enabled, window_seconds, max_requests, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1set_1breaker(JNIEnv *env, jclass cls, jboolean enabled, jlong failure_threshold, jlong failure_window_seconds, jlong open_seconds) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_set_breaker(enabled, failure_threshold, failure_window_seconds, open_seconds, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1aria2_1status(JNIEnv *env, jclass cls, jboolean ensure) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_aria2_status(ensure, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1aria2_1restart(JNIEnv *env, jclass cls) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_aria2_restart(&__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1aria2_1configure(JNIEnv *env, jclass cls, jlong split, jlong max_connection_per_server, jlong max_concurrent_downloads, jlong min_split_size_mib, jlong max_overall_download_limit_kib, jlong port) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_aria2_configure(split, max_connection_per_server, max_concurrent_downloads, min_split_size_mib, max_overall_download_limit_kib, port, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1aria2_1add(JNIEnv *env, jclass cls, jobject url, jint __boltffi_url_len, jobject out, jint __boltffi_out_len) {
    (void)cls;

    void *__boltffi_url_ptr = NULL;
    void *__boltffi_out_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, url, (jlong)__boltffi_url_len, &__boltffi_url_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, out, (jlong)__boltffi_out_len, &__boltffi_out_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_aria2_add((const uint8_t *)__boltffi_url_ptr, (uintptr_t)__boltffi_url_len, (const uint8_t *)__boltffi_out_ptr, (uintptr_t)__boltffi_out_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1aria2_1tell(JNIEnv *env, jclass cls, jobject gid, jint __boltffi_gid_len) {
    (void)cls;

    void *__boltffi_gid_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, gid, (jlong)__boltffi_gid_len, &__boltffi_gid_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_aria2_tell((const uint8_t *)__boltffi_gid_ptr, (uintptr_t)__boltffi_gid_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1aria2_1list(JNIEnv *env, jclass cls) {
    (void)cls;

    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_aria2_list(&__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1aria2_1pause(JNIEnv *env, jclass cls, jobject gid, jint __boltffi_gid_len) {
    (void)cls;

    void *__boltffi_gid_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, gid, (jlong)__boltffi_gid_len, &__boltffi_gid_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_aria2_pause((const uint8_t *)__boltffi_gid_ptr, (uintptr_t)__boltffi_gid_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1aria2_1unpause(JNIEnv *env, jclass cls, jobject gid, jint __boltffi_gid_len) {
    (void)cls;

    void *__boltffi_gid_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, gid, (jlong)__boltffi_gid_len, &__boltffi_gid_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_aria2_unpause((const uint8_t *)__boltffi_gid_ptr, (uintptr_t)__boltffi_gid_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1aria2_1cancel(JNIEnv *env, jclass cls, jobject gid, jint __boltffi_gid_len) {
    (void)cls;

    void *__boltffi_gid_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, gid, (jlong)__boltffi_gid_len, &__boltffi_gid_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_aria2_cancel((const uint8_t *)__boltffi_gid_ptr, (uintptr_t)__boltffi_gid_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1api_1fetch_1artist_1biography(JNIEnv *env, jclass cls, jobject name, jint __boltffi_name_len, jobject singer_mid, jint __boltffi_singer_mid_len) {
    (void)cls;

    void *__boltffi_name_ptr = NULL;
    void *__boltffi_singer_mid_ptr = NULL;
    FfiBuf_u8 __boltffi_return = (FfiBuf_u8){0};

    if (!boltffi_jni_direct_buffer_address(env, name, (jlong)__boltffi_name_len, &__boltffi_name_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, singer_mid, (jlong)__boltffi_singer_mid_len, &__boltffi_singer_mid_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_api_fetch_artist_biography((const uint8_t *)__boltffi_name_ptr, (uintptr_t)__boltffi_name_len, (const uint8_t *)__boltffi_singer_mid_ptr, (uintptr_t)__boltffi_singer_mid_len, &__boltffi_return);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return NULL;
    }

    return boltffi_jni_buffer_to_byte_array(env, __boltffi_return);
__boltffi_error:
    return NULL;
}

JNIEXPORT void JNICALL Java_com_example_qqmusic_1api_1helper_1next_Native_boltffi_1function_1qqmusic_1api_1helper_1next_1initialize(JNIEnv *env, jclass cls, jobject data_dir, jint __boltffi_data_dir_len, jobject platform, jint __boltffi_platform_len) {
    (void)cls;

    void *__boltffi_data_dir_ptr = NULL;
    void *__boltffi_platform_ptr = NULL;

    if (!boltffi_jni_direct_buffer_address(env, data_dir, (jlong)__boltffi_data_dir_len, &__boltffi_data_dir_ptr)) {
        goto __boltffi_error;
    }
    if (!boltffi_jni_direct_buffer_address(env, platform, (jlong)__boltffi_platform_len, &__boltffi_platform_ptr)) {
        goto __boltffi_error;
    }

    FfiBuf_u8 error = boltffi_function_qqmusic_api_helper_next_initialize((const uint8_t *)__boltffi_data_dir_ptr, (uintptr_t)__boltffi_data_dir_len, (const uint8_t *)__boltffi_platform_ptr, (uintptr_t)__boltffi_platform_len);

    if (error.ptr != NULL || error.len != 0) {
        boltffi_jni_throw_error_buffer(env, error);
        return;
    }

    return;
__boltffi_error:
    return;
}
