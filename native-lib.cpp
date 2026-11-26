#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include "node.h"

// node's libUV requires all arguments in contiguous memory.
extern "C" jint JNICALL
Java_com_stjoseph_phone_MainActivity_startNodeWithArguments(
        JNIEnv *env, jobject /* this */, jobjectArray arguments) {
    jsize count = env->GetArrayLength(arguments);
    int size = 0;
    for (jsize i = 0; i < count; i++) {
        jstring s = (jstring) env->GetObjectArrayElement(arguments, i);
        const char *c = env->GetStringUTFChars(s, 0);
        size += strlen(c) + 1;
        env->ReleaseStringUTFChars(s, c);
    }
    char *buf = (char *) calloc(size, sizeof(char));
    char *argv[count];
    char *pos = buf;
    for (jsize i = 0; i < count; i++) {
        jstring s = (jstring) env->GetObjectArrayElement(arguments, i);
        const char *c = env->GetStringUTFChars(s, 0);
        size_t n = strlen(c);
        memcpy(pos, c, n);
        argv[i] = pos;
        pos += n + 1;
        env->ReleaseStringUTFChars(s, c);
    }
    return (jint) node::Start((int) count, argv);
}
