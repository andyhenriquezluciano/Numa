/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Numa.
 *
 * Numa is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Numa is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

// JNI for dev.sfg.numa.mobile.social.PartyPeerNative: one WebRTC data channel to a
// Listening Party peer, on the same libdatachannel stack Connect uses.

#include "connect/event_loop.h"
#include "connect/webrtc_transport.h"
#include "jni_util.h"

#include <jni.h>

#include <memory>
#include <exception>
#include <stdexcept>
#include <string>
#include <utility>

namespace {

using numa::connect::EventLoop;
using numa::connect::WebRtcConnectTransport;
using numa::jni::currentEnv;
using numa::jni::javaString;
using numa::jni::text;

constexpr const char *kPartyChannelLabel = "numa-party";

class PartyPeer {
public:
  PartyPeer(JNIEnv *env, jobject listener, const std::string &iceServers, bool offerer)
      : m_listener(env->NewGlobalRef(listener)), m_loop(std::make_shared<EventLoop>()) {
    try {
    jclass type = env->GetObjectClass(listener);
    m_onSignal = env->GetMethodID(type, "onSignal", "(Ljava/lang/String;)V");
    m_onOpen = env->GetMethodID(type, "onOpen", "()V");
    m_onText = env->GetMethodID(type, "onText", "(Ljava/lang/String;)V");
    m_onClosed = env->GetMethodID(type, "onClosed", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(type);

    numa::connect::Json servers = numa::connect::Json::parse(iceServers, nullptr, false);
    m_transport = std::make_shared<WebRtcConnectTransport>(servers, offerer, kPartyChannelLabel, false);
    numa::connect::ConnectTransport::Callbacks callbacks;
    callbacks.opened = [this] { call(m_onOpen, nullptr); };
    callbacks.text = [this](std::string value) { call(m_onText, &value); };
    callbacks.closed = [this](std::string reason) { call(m_onClosed, &reason); };
    callbacks.signal = [this](std::string json) { call(m_onSignal, &json); };
    m_transport->bind(m_loop, std::move(callbacks));
    } catch (...) {
      m_loop->stop();
      if (m_listener) env->DeleteGlobalRef(m_listener);
      m_listener = nullptr;
      throw;
    }
  }

  WebRtcConnectTransport &transport() { return *m_transport; }

  // Joins the loop thread, so no callback can reach the listener afterwards.
  void shutdown(JNIEnv *env) {
    m_transport->close();
    m_loop->stop();
    m_transport.reset();
    if (m_listener)
      env->DeleteGlobalRef(m_listener);
    m_listener = nullptr;
  }

private:
  void call(jmethodID method, const std::string *argument) {
    JNIEnv *env = currentEnv();
    if (!env || !m_listener)
      return;
    if (argument) {
      jstring value = javaString(env, *argument);
      env->CallVoidMethod(m_listener, method, value);
      env->DeleteLocalRef(value);
    } else {
      env->CallVoidMethod(m_listener, method);
    }
    // A Kotlin exception must not unwind into the loop thread.
    if (env->ExceptionCheck()) {
      env->ExceptionDescribe();
      env->ExceptionClear();
    }
  }

  jobject m_listener = nullptr;
  jmethodID m_onSignal = nullptr;
  jmethodID m_onOpen = nullptr;
  jmethodID m_onText = nullptr;
  jmethodID m_onClosed = nullptr;
  std::shared_ptr<EventLoop> m_loop;
  std::shared_ptr<WebRtcConnectTransport> m_transport;
};

void throwJava(JNIEnv *env, const char *message) {
  if (env->ExceptionCheck()) return;
  jclass type = env->FindClass("java/lang/IllegalStateException");
  if (type) { env->ThrowNew(type, message); env->DeleteLocalRef(type); }
}

PartyPeer *peer(jlong value) { return reinterpret_cast<PartyPeer *>(value); }

} // namespace

#define PARTY_JNI(name) Java_dev_sfg_numa_mobile_social_PartyPeerNative_##name

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
  numa::jni::g_vm = vm;
  return JNI_VERSION_1_6;
}

JNIEXPORT jlong JNICALL PARTY_JNI(create)(JNIEnv *env, jclass, jobject listener, jstring iceServers, jboolean offerer) {
  try {
    return reinterpret_cast<jlong>(new PartyPeer(env, listener, text(env, iceServers), offerer == JNI_TRUE));
  } catch (const std::exception &e) { throwJava(env, e.what()); }
  catch (...) { throwJava(env, "Could not create party peer"); }
  return 0;
}

JNIEXPORT void JNICALL PARTY_JNI(destroy)(JNIEnv *env, jclass, jlong value) {
  try {
    std::unique_ptr<PartyPeer> target(peer(value));
    if (target) target->shutdown(env);
  } catch (const std::exception &e) { throwJava(env, e.what()); }
  catch (...) { throwJava(env, "Could not destroy party peer"); }
}

JNIEXPORT void JNICALL PARTY_JNI(start)(JNIEnv *env, jclass, jlong value) {
  try { if (auto *target = peer(value)) target->transport().start(); }
  catch (const std::exception &e) { throwJava(env, e.what()); }
  catch (...) { throwJava(env, "Could not start party peer"); }
}

JNIEXPORT void JNICALL PARTY_JNI(signal)(JNIEnv *env, jclass, jlong value, jstring json) {
  try {
    auto data = numa::connect::Json::parse(text(env, json), nullptr, false);
    if (!data.is_object()) throw std::invalid_argument("Invalid party signal");
    if (auto *target = peer(value)) target->transport().applySignal(data);
  } catch (const std::exception &e) { throwJava(env, e.what()); }
  catch (...) { throwJava(env, "Could not apply party signal"); }
}

JNIEXPORT jboolean JNICALL PARTY_JNI(sendText)(JNIEnv *env, jclass, jlong value, jstring message) {
  try { return peer(value) && peer(value)->transport().sendText(text(env, message)) ? JNI_TRUE : JNI_FALSE; }
  catch (const std::exception &e) { throwJava(env, e.what()); }
  catch (...) { throwJava(env, "Could not send party message"); }
  return JNI_FALSE;
}

} // extern "C"
