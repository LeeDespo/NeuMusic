#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#if defined(_MSC_VER)
#include <intrin.h>
#elif !defined(__cplusplus)
#include <stdatomic.h>
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    int32_t code;
} FfiStatus;

#ifdef __cplusplus
static inline FfiStatus boltffi_status_from_code(int32_t code) {
    FfiStatus status = { code };
    return status;
}
#define FFI_STATUS_OK boltffi_status_from_code(0)
#define FFI_STATUS_NULL_POINTER boltffi_status_from_code(1)
#define FFI_STATUS_BUFFER_TOO_SMALL boltffi_status_from_code(2)
#define FFI_STATUS_INVALID_ARG boltffi_status_from_code(3)
#define FFI_STATUS_CANCELLED boltffi_status_from_code(4)
#define FFI_STATUS_INTERNAL_ERROR boltffi_status_from_code(100)
#else
#define FFI_STATUS_OK ((FfiStatus){0})
#define FFI_STATUS_NULL_POINTER ((FfiStatus){1})
#define FFI_STATUS_BUFFER_TOO_SMALL ((FfiStatus){2})
#define FFI_STATUS_INVALID_ARG ((FfiStatus){3})
#define FFI_STATUS_CANCELLED ((FfiStatus){4})
#define FFI_STATUS_INTERNAL_ERROR ((FfiStatus){100})
#endif

typedef struct {
    uint8_t *ptr;
    uintptr_t len;
    uintptr_t cap;
    uintptr_t align;
} FfiBuf_u8;

typedef struct {
    uint8_t *ptr;
    uintptr_t len;
    uintptr_t cap;
} FfiString;

typedef struct {
    FfiString message;
} FfiError;

typedef struct {
    const uint8_t *ptr;
    uintptr_t len;
} FfiSpan;

typedef const void *RustFutureHandle;
typedef int8_t StreamPollResult;
typedef int32_t WaitResult;
typedef void (*RustFutureContinuationCallback)(uint64_t callback_data, int8_t poll_result);
typedef void (*StreamContinuationCallback)(uint64_t callback_data, StreamPollResult result);

#if defined(_MSC_VER)
static inline bool boltffi_atomic_u8_cas(uint8_t *state, uint8_t expected, uint8_t desired) {
    return _InterlockedCompareExchange8((volatile char *)state, (char)desired, (char)expected) == (char)expected;
}

static inline uint64_t boltffi_atomic_u64_exchange(uint64_t *slot, uint64_t value) {
    return (uint64_t)_InterlockedExchange64((volatile __int64 *)slot, (__int64)value);
}

static inline bool boltffi_atomic_u64_cas(uint64_t *slot, uint64_t expected, uint64_t desired) {
    return (uint64_t)_InterlockedCompareExchange64((volatile __int64 *)slot, (__int64)desired, (__int64)expected) == expected;
}

static inline uint64_t boltffi_atomic_u64_load(uint64_t *slot) {
    return (uint64_t)_InterlockedCompareExchange64((volatile __int64 *)slot, 0, 0);
}
#elif defined(__cplusplus) && (defined(__clang__) || defined(__GNUC__))
static inline bool boltffi_atomic_u8_cas(uint8_t *state, uint8_t expected, uint8_t desired) {
    return __atomic_compare_exchange_n(state, &expected, desired, false, __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE);
}

static inline uint64_t boltffi_atomic_u64_exchange(uint64_t *slot, uint64_t value) {
    return __atomic_exchange_n(slot, value, __ATOMIC_ACQ_REL);
}

static inline bool boltffi_atomic_u64_cas(uint64_t *slot, uint64_t expected, uint64_t desired) {
    return __atomic_compare_exchange_n(slot, &expected, desired, false, __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE);
}

static inline uint64_t boltffi_atomic_u64_load(uint64_t *slot) {
    return __atomic_load_n(slot, __ATOMIC_ACQUIRE);
}
#elif defined(__cplusplus)
#error "BoltFFI C++ atomics require GCC, Clang, or MSVC"
#else
static inline bool boltffi_atomic_u8_cas(uint8_t *state, uint8_t expected, uint8_t desired) {
    return atomic_compare_exchange_strong_explicit((_Atomic uint8_t *)state, &expected, desired, memory_order_acq_rel, memory_order_acquire);
}

static inline uint64_t boltffi_atomic_u64_exchange(uint64_t *slot, uint64_t value) {
    return atomic_exchange_explicit((_Atomic uint64_t *)slot, value, memory_order_acq_rel);
}

static inline bool boltffi_atomic_u64_cas(uint64_t *slot, uint64_t expected, uint64_t desired) {
    return atomic_compare_exchange_strong_explicit((_Atomic uint64_t *)slot, &expected, desired, memory_order_acq_rel, memory_order_acquire);
}

static inline uint64_t boltffi_atomic_u64_load(uint64_t *slot) {
    return atomic_load_explicit((_Atomic uint64_t *)slot, memory_order_acquire);
}
#endif

typedef struct {
    uint64_t handle;
    const void *vtable;
} BoltFFICallbackHandle;

void boltffi_free_string(FfiString string);
void boltffi_free_buf(FfiBuf_u8 buf);
FfiString boltffi_buf_into_string(FfiBuf_u8 buf);
FfiBuf_u8 boltffi_buf_from_bytes(const uint8_t *ptr, uintptr_t len);
FfiBuf_u8 boltffi_buf_with_len(uintptr_t len);
FfiBuf_u8 boltffi_callback_error(const uint8_t *message, uintptr_t length);
FfiStatus boltffi_last_error_message(FfiString *out);
void boltffi_clear_last_error(void);
typedef struct {
    int64_t song_id;
    int64_t song_type;
} ___SongInfoPair;
typedef struct {
    bool success;
} ___PlaylistFavWriteResponse;
typedef struct {
    int64_t split;
    int64_t max_connection_per_server;
    int64_t max_concurrent_downloads;
    int64_t min_split_size_mib;
    int64_t max_overall_download_limit_kib;
    int64_t port;
} ___Aria2OptionsModel;
typedef struct {
    uint32_t read;
    uint32_t interactive;
    uint32_t playback;
    uint32_t account;
    uint32_t write;
} ___RateLimitUsage;
typedef uint32_t ___HelperError;
#define HELPER_ERROR_NOT_LOGGED_IN ((___HelperError)0)
#define HELPER_ERROR_THROTTLED ((___HelperError)1)
#define HELPER_ERROR_UPSTREAM ((___HelperError)2)
#define HELPER_ERROR_UNSUPPORTED ((___HelperError)3)
#define HELPER_ERROR_INVALID_REQUEST ((___HelperError)4)
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_collection_write_create_playlist(const uint8_t *dirname_ptr, uintptr_t dirname_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_collection_write_delete_playlist(int64_t dirid, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_collection_write_add_playlist_songs(int64_t dirid, const uint8_t *song_info_ptr, uintptr_t song_info_byte_len, const uint8_t *tid_ptr, uintptr_t tid_len, bool *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_collection_write_remove_playlist_songs(int64_t dirid, const uint8_t *song_info_ptr, uintptr_t song_info_byte_len, const uint8_t *tid_ptr, uintptr_t tid_len, bool *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_collection_write_fav_album(const int64_t *album_ids_ptr, uintptr_t album_ids_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_collection_write_unfav_album(const int64_t *album_ids_ptr, uintptr_t album_ids_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_collection_write_fav_playlist(int64_t playlist_id, bool *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_collection_write_unfav_playlist(int64_t playlist_id, bool *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_comment_fetch_comment_count(int64_t biz_id, const uint8_t *biz_type_ptr, uintptr_t biz_type_len, const uint8_t *biz_sub_type_ptr, uintptr_t biz_sub_type_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_comment_fetch_hot_comments(int64_t biz_id, const uint8_t *page_ptr, uintptr_t page_len, const uint8_t *page_size_ptr, uintptr_t page_size_len, const uint8_t *last_comment_seq_no_ptr, uintptr_t last_comment_seq_no_len, const uint8_t *biz_type_ptr, uintptr_t biz_type_len, const uint8_t *biz_sub_type_ptr, uintptr_t biz_sub_type_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_comment_fetch_new_comments(int64_t biz_id, const uint8_t *page_ptr, uintptr_t page_len, const uint8_t *page_size_ptr, uintptr_t page_size_len, const uint8_t *last_comment_seq_no_ptr, uintptr_t last_comment_seq_no_len, const uint8_t *biz_type_ptr, uintptr_t biz_type_len, const uint8_t *biz_sub_type_ptr, uintptr_t biz_sub_type_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_comment_fetch_recommend_comments(int64_t biz_id, const uint8_t *page_ptr, uintptr_t page_len, const uint8_t *page_size_ptr, uintptr_t page_size_len, const uint8_t *last_comment_seq_no_ptr, uintptr_t last_comment_seq_no_len, const uint8_t *biz_type_ptr, uintptr_t biz_type_len, const uint8_t *biz_sub_type_ptr, uintptr_t biz_sub_type_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_comment_fetch_moment_comments(int64_t biz_id, const uint8_t *page_size_ptr, uintptr_t page_size_len, const uint8_t *last_comment_seq_no_ptr, uintptr_t last_comment_seq_no_len, const uint8_t *biz_type_ptr, uintptr_t biz_type_len, const uint8_t *biz_sub_type_ptr, uintptr_t biz_sub_type_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_comment_add_comment(int64_t biz_id, const uint8_t *content_ptr, uintptr_t content_len, const uint8_t *reply_cmt_id_ptr, uintptr_t reply_cmt_id_len, const uint8_t *biz_type_ptr, uintptr_t biz_type_len, const uint8_t *biz_sub_type_ptr, uintptr_t biz_sub_type_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_comment_delete_comment(const uint8_t *cm_id_ptr, uintptr_t cm_id_len, bool *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_new_albums(const uint8_t *area_ptr, uintptr_t area_len, const uint8_t *num_ptr, uintptr_t num_len, const uint8_t *page_ptr, uintptr_t page_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_singing_annotations(int64_t song_id, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_multi_style_lyrics(int64_t song_id, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_library_extra_has_ai_dictionary(int64_t song_id, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_ai_dictionary(int64_t song_id, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_user_liked_songs(const uint8_t *euin_ptr, uintptr_t euin_len, const uint8_t *page_ptr, uintptr_t page_len, const uint8_t *num_ptr, uintptr_t num_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_login_extra_fetch_wx_qrcode(FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_login_extra_check_wx_qrcode(const uint8_t *identifier_ptr, uintptr_t identifier_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_login_extra_send_phone_authcode(const uint8_t *phone_ptr, uintptr_t phone_len, const uint8_t *encrypted_phone_ptr, uintptr_t encrypted_phone_len, const uint8_t *country_code_ptr, uintptr_t country_code_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_login_extra_phone_login(const uint8_t *phone_ptr, uintptr_t phone_len, const uint8_t *encrypted_phone_ptr, uintptr_t encrypted_phone_len, const uint8_t *auth_code_ptr, uintptr_t auth_code_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_login_extra_refresh_credential(FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_mv_fetch_mv_detail(const uint8_t *vids_ptr, uintptr_t vids_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_mv_resolve_mv_urls(const uint8_t *vids_ptr, uintptr_t vids_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_mv_fetch_mv_list(const uint8_t *area_ptr, uintptr_t area_len, const uint8_t *version_ptr, uintptr_t version_len, const uint8_t *order_ptr, uintptr_t order_len, const uint8_t *num_ptr, uintptr_t num_len, const uint8_t *page_ptr, uintptr_t page_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_recommend_extra_fetch_home_feed(const uint8_t *page_ptr, uintptr_t page_len, const uint8_t *direction_ptr, uintptr_t direction_len, const uint8_t *s_num_ptr, uintptr_t s_num_len, const uint8_t *v_cache_ptr, uintptr_t v_cache_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_recommend_extra_fetch_radar_recommend(const uint8_t *page_ptr, uintptr_t page_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_recommend_extra_fetch_recommend_playlists(const uint8_t *page_ptr, uintptr_t page_len, const uint8_t *num_ptr, uintptr_t num_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_search_extra_fetch_search_hotkeys(FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_search_extra_complete_search(const uint8_t *keyword_ptr, uintptr_t keyword_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_search_extra_quick_search(const uint8_t *keyword_ptr, uintptr_t keyword_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_search_extra_general_search(const uint8_t *keyword_ptr, uintptr_t keyword_len, const uint8_t *page_ptr, uintptr_t page_len, const uint8_t *num_ptr, uintptr_t num_len, const uint8_t *searchid_ptr, uintptr_t searchid_len, const uint8_t *page_start_ptr, uintptr_t page_start_len, const uint8_t *highlight_ptr, uintptr_t highlight_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_search_extra_search_extra(const uint8_t *keyword_ptr, uintptr_t keyword_len, const uint8_t *search_type_ptr, uintptr_t search_type_len, const uint8_t *page_ptr, uintptr_t page_len, const uint8_t *num_ptr, uintptr_t num_len, const uint8_t *searchid_ptr, uintptr_t searchid_len, const uint8_t *selectors_ptr, uintptr_t selectors_len, const uint8_t *highlight_ptr, uintptr_t highlight_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_singer_list(const uint8_t *area_ptr, uintptr_t area_len, const uint8_t *sex_ptr, uintptr_t sex_len, const uint8_t *genre_ptr, uintptr_t genre_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_singer_index(const uint8_t *area_ptr, uintptr_t area_len, const uint8_t *sex_ptr, uintptr_t sex_len, const uint8_t *genre_ptr, uintptr_t genre_len, const uint8_t *index_ptr, uintptr_t index_len, const uint8_t *page_ptr, uintptr_t page_len, const uint8_t *num_ptr, uintptr_t num_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_similar_artists(const uint8_t *singer_mid_ptr, uintptr_t singer_mid_len, const uint8_t *number_ptr, uintptr_t number_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_artist_tab(const uint8_t *singer_mid_ptr, uintptr_t singer_mid_len, const uint8_t *tab_type_ptr, uintptr_t tab_type_len, const uint8_t *page_ptr, uintptr_t page_len, const uint8_t *num_ptr, uintptr_t num_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_artist_display_name(const uint8_t *singer_mid_ptr, uintptr_t singer_mid_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_artist_mvs(const uint8_t *singer_mid_ptr, uintptr_t singer_mid_len, const uint8_t *num_ptr, uintptr_t num_len, const uint8_t *page_ptr, uintptr_t page_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_song_asset_query_songs(const uint8_t *songs_ptr, uintptr_t songs_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_song_asset_fetch_cdn_dispatch(FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_song_asset_resolve_song_urls(const uint8_t *file_info_ptr, uintptr_t file_info_len, const uint8_t *file_type_ptr, uintptr_t file_type_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_song_asset_fetch_other_versions(const uint8_t *value_ptr, uintptr_t value_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_song_asset_fetch_song_producer(const uint8_t *value_ptr, uintptr_t value_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_song_asset_fetch_song_fav_count(const int64_t *song_ids_ptr, uintptr_t song_ids_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_similar_songs(int64_t song_id, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_song_labels(int64_t song_id, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_related_playlists(int64_t song_id, const uint8_t *last_ptr, uintptr_t last_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_related_mvs(int64_t song_id, const uint8_t *last_mvid_ptr, uintptr_t last_mvid_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_sheet_music(const uint8_t *mid_ptr, uintptr_t mid_len, const uint8_t *ttype_ptr, uintptr_t ttype_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_song_related_has_sheet_music(const uint8_t *mid_ptr, uintptr_t mid_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_fav_playlists(const uint8_t *euin_ptr, uintptr_t euin_len, const uint8_t *page_ptr, uintptr_t page_len, const uint8_t *num_ptr, uintptr_t num_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_fav_albums(const uint8_t *euin_ptr, uintptr_t euin_len, const uint8_t *page_ptr, uintptr_t page_len, const uint8_t *num_ptr, uintptr_t num_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_fav_mvs(const uint8_t *euin_ptr, uintptr_t euin_len, const uint8_t *page_ptr, uintptr_t page_len, const uint8_t *num_ptr, uintptr_t num_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_music_gene(const uint8_t *euin_ptr, uintptr_t euin_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_dislike_list(const uint8_t *cmd_ptr, uintptr_t cmd_len, const uint8_t *page_ptr, uintptr_t page_len, const uint8_t *lastid_ptr, uintptr_t lastid_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_user_asset_add_dislike(int64_t id_type, const int64_t *values_ptr, uintptr_t values_len, bool *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_user_asset_cancel_dislike(int64_t id_type, const int64_t *values_ptr, uintptr_t values_len, bool *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_user_asset_clear_dislike_songs(bool *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_user_homepage(const uint8_t *euin_ptr, uintptr_t euin_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_vip_info(FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_follow_singers(const uint8_t *euin_ptr, uintptr_t euin_len, const uint8_t *page_ptr, uintptr_t page_len, const uint8_t *num_ptr, uintptr_t num_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_follow_singers_at_offset(const uint8_t *euin_ptr, uintptr_t euin_len, int64_t offset, const uint8_t *num_ptr, uintptr_t num_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_fans(const uint8_t *euin_ptr, uintptr_t euin_len, const uint8_t *page_ptr, uintptr_t page_len, const uint8_t *num_ptr, uintptr_t num_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_friends(const uint8_t *page_ptr, uintptr_t page_len, const uint8_t *num_ptr, uintptr_t num_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_followed_users(const uint8_t *euin_ptr, uintptr_t euin_len, const uint8_t *page_ptr, uintptr_t page_len, const uint8_t *num_ptr, uintptr_t num_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_created_playlists(const uint8_t *uin_ptr, uintptr_t uin_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_login_status(FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_import_credential(const uint8_t *uin_ptr, uintptr_t uin_len, const uint8_t *qm_keyst_ptr, uintptr_t qm_keyst_len);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_import_credential_with_encrypt_uin(const uint8_t *uin_ptr, uintptr_t uin_len, const uint8_t *qm_keyst_ptr, uintptr_t qm_keyst_len, const uint8_t *encrypt_uin_ptr, uintptr_t encrypt_uin_len);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_logout(void);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_liked_songs(uint32_t page, uint32_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_playlist_tracks(int64_t list_id, uint32_t offset, uint32_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_playlist_tracks_page(int64_t list_id, const uint8_t *dir_id_ptr, uintptr_t dir_id_len, uint32_t offset, uint32_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_set_liked_by_id(int64_t song_id, bool liked, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_user_playlists(uint32_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_liked_albums(uint32_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_followed_artists(uint32_t page, uint32_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_component_info(FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_guard_status(FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_song_detail(const uint8_t *song_mid_ptr, uintptr_t song_mid_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_album_detail(const uint8_t *album_mid_ptr, uintptr_t album_mid_len, const uint8_t *album_id_ptr, uintptr_t album_id_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_album_tracks(const uint8_t *album_mid_ptr, uintptr_t album_mid_len, const uint8_t *album_id_ptr, uintptr_t album_id_len, int64_t offset, int64_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_artist_songs(const uint8_t *singer_mid_ptr, uintptr_t singer_mid_len, const uint8_t *sort_ptr, uintptr_t sort_len, int64_t page, int64_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_artist_songs_page(const uint8_t *singer_mid_ptr, uintptr_t singer_mid_len, const uint8_t *sort_ptr, uintptr_t sort_len, uint32_t offset, uint32_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_artist_albums(const uint8_t *singer_mid_ptr, uintptr_t singer_mid_len, const uint8_t *sort_ptr, uintptr_t sort_len, int64_t page, int64_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_artist_albums_page(const uint8_t *singer_mid_ptr, uintptr_t singer_mid_len, const uint8_t *sort_ptr, uintptr_t sort_len, uint32_t offset, uint32_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_artist_detail(const uint8_t *singer_mid_ptr, uintptr_t singer_mid_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_toplist_categories(FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_toplist_tracks(int64_t top_id, int64_t offset, int64_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_radio_stations(FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_radio_tracks(int64_t station_id, int64_t limit, bool first_play, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_radio_track_batch(int64_t station_id, bool first_play, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_new_songs(int64_t region_type, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_recommend_feed(FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_lyric(const uint8_t *song_mid_ptr, uintptr_t song_mid_len, const uint8_t *song_id_ptr, uintptr_t song_id_len, bool word_timing, bool translation, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_resolve_song_url(const uint8_t *song_mid_ptr, uintptr_t song_mid_len, const uint8_t *media_mid_ptr, uintptr_t media_mid_len, int64_t song_type, const uint8_t *preferred_quality_ptr, uintptr_t preferred_quality_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_call_with_platform(const uint8_t *method_ptr, uintptr_t method_len, const uint8_t *params_json_ptr, uintptr_t params_json_len, const uint8_t *platform_ptr, uintptr_t platform_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_set_liked(const uint8_t *song_mid_ptr, uintptr_t song_mid_len, int64_t song_type, bool liked);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_start_login(FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_poll_login(const uint8_t *identifier_ptr, uintptr_t identifier_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_search_songs(const uint8_t *keyword_ptr, uintptr_t keyword_len, int64_t page, int64_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_search_artists(const uint8_t *keyword_ptr, uintptr_t keyword_len, int64_t page, int64_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_search_albums(const uint8_t *keyword_ptr, uintptr_t keyword_len, int64_t page, int64_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_search_playlists(const uint8_t *keyword_ptr, uintptr_t keyword_len, int64_t page, int64_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_search_track_artwork(const uint8_t *title_ptr, uintptr_t title_len, const uint8_t *artist_ptr, uintptr_t artist_len, const uint8_t *album_ptr, uintptr_t album_len, int64_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_search_artist_artwork(const uint8_t *name_ptr, uintptr_t name_len, int64_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_search_album_artwork(const uint8_t *album_ptr, uintptr_t album_len, const uint8_t *artist_ptr, uintptr_t artist_len, int64_t limit, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_set_rate_limit(bool enabled, int64_t window_seconds, int64_t max_requests, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_set_breaker(bool enabled, int64_t failure_threshold, int64_t failure_window_seconds, int64_t open_seconds, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_aria2_status(bool ensure, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_aria2_restart(FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_aria2_configure(int64_t split, int64_t max_connection_per_server, int64_t max_concurrent_downloads, int64_t min_split_size_mib, int64_t max_overall_download_limit_kib, int64_t port, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_aria2_add(const uint8_t *url_ptr, uintptr_t url_len, const uint8_t *out_ptr, uintptr_t out_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_aria2_tell(const uint8_t *gid_ptr, uintptr_t gid_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_aria2_list(FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_aria2_pause(const uint8_t *gid_ptr, uintptr_t gid_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_aria2_unpause(const uint8_t *gid_ptr, uintptr_t gid_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_aria2_cancel(const uint8_t *gid_ptr, uintptr_t gid_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_api_fetch_artist_biography(const uint8_t *name_ptr, uintptr_t name_len, const uint8_t *singer_mid_ptr, uintptr_t singer_mid_len, FfiBuf_u8 *return_out);
FfiBuf_u8 boltffi_function_qqmusic_api_helper_next_initialize(const uint8_t *data_dir_ptr, uintptr_t data_dir_len, const uint8_t *platform_ptr, uintptr_t platform_len);

#ifdef __cplusplus
}
#endif