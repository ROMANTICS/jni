#include "org_romantics_jni_Main.h"

JNIEXPORT jint JNICALL Java_org_romantics_jni_Main_add
  (JNIEnv * env, jclass c, jint i, jint j)
  {
    return i+ j    ;
  }
JNIEXPORT jint JNICALL Java_org_romantics_jni_Main_sub  (JNIEnv * env, jclass c, jint i, jint  j)
  {
    return i- j    ;
  }
//JNIEXPORT jint JNICALL Java_org_romantics_jni_Main_div1  (JNIEnv * env, jclass c, jint i, jint  j)
//  {
//    return i/ j    ;
//  }
//JNIEXPORT jint JNICALL Java_org_romantics_jni_Main_mul  (JNIEnv * env, jclass c, jint i, jint  j)
//  {
//    return i* j    ;
//  }