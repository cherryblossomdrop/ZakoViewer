cmake_minimum_required(VERSION 3.22.1)

project("zakoviewer_player")

add_library(
    player
    SHARED
    mpv_jni.cpp
)

find_library(
    log-lib
    log
)

find_library(
    android-lib
    android
)

set(JNILIBS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
if(EXISTS "${JNILIBS_DIR}/libmpv.so")
    add_library(mpv SHARED IMPORTED)
    set_target_properties(mpv PROPERTIES IMPORTED_LOCATION "${JNILIBS_DIR}/libmpv.so")
    target_link_libraries(
        player
        mpv
        ${log-lib}
        ${android-lib}
        dl
    )
else()
    target_link_libraries(
        player
        ${log-lib}
        ${android-lib}
        dl
    )
endif()
