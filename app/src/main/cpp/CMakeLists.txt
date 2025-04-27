cmake_minimum_required(VERSION 3.18.1)
project(faisshelper)

add_library(faisshelper SHARED FaissHelper.cpp)

find_library(log-lib log)
find_library(android-lib android)

# Link against faiss-android
add_library(faiss SHARED IMPORTED)
set_target_properties(faiss PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libfaiss.so)

include_directories(${CMAKE_SOURCE_DIR}/include)

# ABI filter for armeabi-v7a and arm64-v8a
set(CMAKE_ANDROID_ARCH_ABI armeabi-v7a arm64-v8a)

# Link
 target_link_libraries(faisshelper
        faiss
        ${log-lib}
        ${android-lib})
