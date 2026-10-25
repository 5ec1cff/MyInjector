cmake_minimum_required(VERSION 3.22)
project(myinjector_miuihome LANGUAGES CXX ASM)
if(NOT ANDROID OR NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "The HYOS entry requires Android arm64-v8a")
endif()
add_library(myinjector_miuihome SHARED native_hook.cpp dart_hooks.S lsposed_hook_backend.cpp)
target_compile_features(myinjector_miuihome PRIVATE cxx_std_17)
target_compile_options(myinjector_miuihome PRIVATE
    -Wall -Wextra -Werror -fvisibility=hidden -fno-exceptions -fno-rtti
    -fno-threadsafe-statics -ffunction-sections -fdata-sections -mbranch-protection=standard)
target_link_options(myinjector_miuihome PRIVATE
    "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/exports.map"
    -Wl,--gc-sections -Wl,--no-undefined -Wl,-z,max-page-size=16384 -Wl,-z,relro -Wl,-z,now)
target_link_libraries(myinjector_miuihome PRIVATE log dl)
