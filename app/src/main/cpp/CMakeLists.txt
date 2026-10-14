cmake_minimum_required(VERSION 3.22.1)
project("sysinfo-ndk")

add_library(sysinfo_ndk_probe SHARED native-camera-probe.cpp)

target_link_libraries(sysinfo_ndk_probe
    android
    camera2ndk
    log)
