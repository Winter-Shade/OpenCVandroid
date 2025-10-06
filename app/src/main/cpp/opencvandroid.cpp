#include "jni.h"
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <vector>
#include <string>

using namespace cv;

extern "C" {
JNIEXPORT void JNICALL Java_com_wintershade_app_MainActivity_FindFeatures(JNIEnv * jniEnv, jobject, jlong addrGray, jlong addrRGBA)
{
    Mat* mGray = (Mat*)addrGray;
    Mat* mRGBA = (Mat*)addrRGBA;

    Mat edges;

    Canny(*mGray, edges, 50, 150, 3);

    cvtColor(edges, *mRGBA, COLOR_GRAY2RGBA);
}
}