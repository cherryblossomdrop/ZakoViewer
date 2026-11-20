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

target_link_libraries(
    player
    ${log-lib}
    ${android-lib}
)
