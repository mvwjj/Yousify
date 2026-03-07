#include <jni.h>
#include <faiss/IndexFlat.h>
#include <faiss/index_io.h>
#include <android/asset_manager_jni.h>
#include <sys/mman.h>
#include <fstream>
#include <vector>
#include <string>
#include <memory>

using namespace faiss;

namespace {
struct CacheHolder {
    std::unique_ptr<IndexFlatIP> index;
    std::vector<long> ids;
};

CacheHolder* getHolder(jlong handle) {
    return reinterpret_cast<CacheHolder*>(handle);
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_mvwj_Yousify_ann_VectorCache_nativeInit(JNIEnv* env, jobject thiz, jstring path_) {
    const char* path = env->GetStringUTFChars(path_, 0);
    CacheHolder* holder = new CacheHolder();
    // mmap or load index
    std::ifstream fin(path, std::ios::binary);
    if (fin.good()) {
        holder->index.reset(dynamic_cast<IndexFlatIP*>(faiss::read_index(path)));
    } else {
        holder->index = std::make_unique<IndexFlatIP>(384);
    }
    env->ReleaseStringUTFChars(path_, path);
    return reinterpret_cast<jlong>(holder);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mvwj_Yousify_ann_VectorCache_add(JNIEnv* env, jobject thiz, jlong handle, jlong id, jfloatArray vec_) {
    auto* holder = getHolder(handle);
    jfloat* vec = env->GetFloatArrayElements(vec_, 0);
    holder->index->add(1, vec);
    holder->ids.push_back(id);
    env->ReleaseFloatArrayElements(vec_, vec, 0);
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mvwj_Yousify_ann_VectorCache_search(JNIEnv* env, jobject thiz, jlong handle, jfloatArray vec_, jint k) {
    auto* holder = getHolder(handle);
    jfloat* vec = env->GetFloatArrayElements(vec_, 0);
    std::vector<faiss::Index::idx_t> I(k);
    std::vector<float> D(k);
    holder->index->search(1, vec, k, D.data(), I.data());
    env->ReleaseFloatArrayElements(vec_, vec, 0);
    jlongArray result = env->NewLongArray(k);
    std::vector<jlong> out(k);
    for (int i = 0; i < k; ++i) out[i] = holder->ids[I[i]];
    env->SetLongArrayRegion(result, 0, k, out.data());
    return result;
}
