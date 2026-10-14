#include <jni.h>
#include <string>
#include <camera/NdkCameraManager.h>
#include <camera/NdkCameraMetadata.h>
#include <android/log.h>

#define LOG_TAG "PixCaeliumNDK"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Echte native Camera2-NDK-Abfrage: listet Kamera-IDs + Capabilities
// ueber ACameraManager auf (C++), unabhaengig von der Java-API.
extern "C" JNIEXPORT jstring JNICALL
Java_com_pixcaelium_sysinfo_NativeCameraProbe_nativeCameraReport(JNIEnv* env, jclass) {
    std::string report;

    ACameraManager* manager = ACameraManager_create();
    if (manager == nullptr) {
        return env->NewStringUTF("NDK: ACameraManager_create fehlgeschlagen");
    }

    ACameraIdList* idList = nullptr;
    camera_status_t status = ACameraManager_getCameraIdList(manager, &idList);
    if (status != ACAMERA_OK || idList == nullptr) {
        ACameraManager_delete(manager);
        return env->NewStringUTF("NDK: getCameraIdList fehlgeschlagen");
    }

    char buf[256];
    snprintf(buf, sizeof(buf), "NDK-Kameras: %d", idList->numCameras);
    report += buf;

    for (int i = 0; i < idList->numCameras; i++) {
        const char* id = idList->cameraIds[i];
        report += "\n\nID=";
        report += id;

        ACameraMetadata* chars = nullptr;
        if (ACameraManager_getCameraCharacteristics(manager, id, &chars) == ACAMERA_OK) {
            ACameraMetadata_const_entry entry;
            if (ACameraMetadata_getConstEntry(chars, ACAMERA_LENS_FACING, &entry) == ACAMERA_OK) {
                snprintf(buf, sizeof(buf), " facing=%d", entry.data.i32[0]);
                report += buf;
            }
            if (ACameraMetadata_getConstEntry(chars, ACAMERA_REQUEST_AVAILABLE_CAPABILITIES, &entry) == ACAMERA_OK) {
                report += " caps=[";
                for (uint32_t k = 0; k < entry.count; k++) {
                    snprintf(buf, sizeof(buf), "%s%d", k > 0 ? ", " : "", entry.data.i32[k]);
                    report += buf;
                }
                report += "]";
            }
            if (ACameraMetadata_getConstEntry(chars, ACAMERA_CONTROL_ZOOM_RATIO_RANGE, &entry) == ACAMERA_OK
                    && entry.count == 2) {
                snprintf(buf, sizeof(buf), " zoomRange=[%.2f,%.2f]",
                         entry.data.f[0], entry.data.f[1]);
                report += buf;
            }
            ACameraMetadata_free(chars);
        }
        ALOGI("NDK probe ID=%s", id);
    }

    ACameraManager_deleteCameraIdList(idList);
    ACameraManager_delete(manager);
    return env->NewStringUTF(report.c_str());
}
