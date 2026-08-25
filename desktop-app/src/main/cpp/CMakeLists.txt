cmake_minimum_required(VERSION 3.15)
project(player_bridge CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(JNI REQUIRED)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${JNI_INCLUDE_DIRS}
)

if(WIN32)
    include_directories(${CMAKE_CURRENT_SOURCE_DIR}/webview2/build/native/include)
    
    set(SOURCES
        common/mpv_core.cpp
        windows/surface_win32.cpp
        windows/webview_win32.cpp
    )
    
    add_library(player_bridge SHARED ${SOURCES})
    target_link_libraries(player_bridge PRIVATE
        ole32 user32 dwmapi advapi32 gdi32 uuid
    )
    set_target_properties(player_bridge PROPERTIES
        PREFIX ""
        SUFFIX ".dll"
    )
elseif(UNIX AND NOT APPLE)
    set(SOURCES
        common/mpv_core.cpp
        linux/surface_linux.cpp
    )
    
    add_library(player_bridge SHARED ${SOURCES})
    target_link_libraries(player_bridge PRIVATE
        pthread dl
    )
    set_target_properties(player_bridge PROPERTIES
        PREFIX "lib"
        SUFFIX ".so"
    )
endif()
