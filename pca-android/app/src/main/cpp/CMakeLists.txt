cmake_minimum_required(VERSION 3.22)
project(pca-native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# -----------------------------------------------------------------------------
# whisper.cpp — fetched on first configure. v1.7.1 is the most recent tag at
# the time of writing that compiles cleanly with NDK r26 for arm64-v8a and
# carries the Whisper Large-v3 + turbo decoder paths used in spec §12.
# -----------------------------------------------------------------------------
include(FetchContent)

# Configure whisper.cpp build options BEFORE FetchContent_MakeAvailable.
# These map onto the options whisper.cpp exposes in its top-level CMakeLists.
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
# Targeting Exynos 2100 (SM-G998B/DS): no Hexagon DSP / no Qualcomm QNN.
# CPU NEON optimisations are enabled by default on arm64 via NDK toolchain.
# Mali GPU via OpenCL would require CLBlast and the gains on mobile Mali are
# marginal — keep off until measured.
set(WHISPER_OPENCL         OFF CACHE BOOL "" FORCE)
set(WHISPER_COREML         OFF CACHE BOOL "" FORCE)
set(WHISPER_CUBLAS         OFF CACHE BOOL "" FORCE)
set(WHISPER_HIPBLAS        OFF CACHE BOOL "" FORCE)

# PCA-S-40: supply-chain hygiene. GIT_TAG is mutable — upstream could
# force-push a different commit under the same tag name. For higher trust,
# replace GIT_TAG with a full commit SHA (and drop GIT_SHALLOW since
# fetching by SHA needs full history). The tag form below is the canonical
# release pointer; the SHA form is a hardening option that locks our build
# to a single audited commit.
#
#   GIT_TAG     <40-char-commit-sha>      # exact commit (recommended hardened)
#   GIT_SHALLOW FALSE                     # full history needed to fetch by SHA
FetchContent_Declare(
    whisper
    GIT_REPOSITORY https://github.com/ggerganov/whisper.cpp.git
    GIT_TAG        v1.7.1
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(whisper)

# -----------------------------------------------------------------------------
# Our JNI bridge: pca_whisper_jni.so
# -----------------------------------------------------------------------------
add_library(pca_whisper_jni SHARED
    whisper_jni.cpp
)

target_include_directories(pca_whisper_jni PRIVATE
    ${whisper_SOURCE_DIR}
    ${whisper_SOURCE_DIR}/include
    ${whisper_SOURCE_DIR}/ggml/include
)

target_link_libraries(pca_whisper_jni
    PRIVATE
    whisper
    android
    log
)

# Strip aggressively in release for a slimmer .so footprint inside the APK.
target_compile_options(pca_whisper_jni PRIVATE
    -ffunction-sections
    -fdata-sections
    $<$<CONFIG:Release>:-O3>
)
target_link_options(pca_whisper_jni PRIVATE
    -Wl,--gc-sections
    $<$<CONFIG:Release>:-Wl,-s>
)
