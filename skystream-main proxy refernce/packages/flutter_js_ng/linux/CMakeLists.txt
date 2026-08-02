cmake_minimum_required(VERSION 3.10)


set(PROJECT_NAME "flutter_js")
project(${PROJECT_NAME} LANGUAGES CXX C)

set(PLUGIN_NAME "${PROJECT_NAME}_plugin")

set(CMAKE_C_COMPILER "gcc")
set(CMAKE_CXX_COMPILER "g++")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -DANDROID_STL=c++_static -DDEBUGGER_SERVER_PORT=${DEBUGGER_SERVER_PORT}")

set(QUICKJS_SRC_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../cxx")
add_definitions(-DCONFIG_VERSION="2024-01-01" -DQUICKJS -DCONFIG_BIGNUM -D_GNU_SOURCE)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -funsigned-char -Wno-unused-but-set-variable")

add_library(${PLUGIN_NAME} SHARED
  "flutter_js_plugin.cc"
  "${QUICKJS_SRC_DIR}/quickjs/cutils.c"
  "${QUICKJS_SRC_DIR}/quickjs/libregexp.c"
  "${QUICKJS_SRC_DIR}/quickjs/libunicode.c"
  "${QUICKJS_SRC_DIR}/quickjs/libbf.c"
  "${QUICKJS_SRC_DIR}/quickjs/quickjs.c"
  "${QUICKJS_SRC_DIR}/libfastdev_quickjs_runtime.cpp"
)

apply_standard_settings(${PLUGIN_NAME})
target_compile_features(${PLUGIN_NAME} PUBLIC cxx_std_17)
set_target_properties(${PLUGIN_NAME} PROPERTIES
  CXX_VISIBILITY_PRESET hidden)
target_compile_definitions(${PLUGIN_NAME} PRIVATE FLUTTER_PLUGIN_IMPL)
target_include_directories(${PLUGIN_NAME} INTERFACE
  "${CMAKE_CURRENT_SOURCE_DIR}/include")
target_include_directories(${PLUGIN_NAME} PRIVATE "${QUICKJS_SRC_DIR}/quickjs")

target_link_libraries(${PLUGIN_NAME} PRIVATE flutter)
target_link_libraries(${PLUGIN_NAME} PRIVATE PkgConfig::GTK)
target_link_libraries(${PLUGIN_NAME} PRIVATE m dl pthread)
