package com.geeplayer.upnp.eventing

import android.util.Log

/**
 * GENA (General Event Notification Architecture) 订阅管理
 *
 * 管理推送端对服务状态变更的事件订阅
 */
class GennaSubscriberManager {
    private companion object {
        private const val TAG = "GennaSubscriberManager"
    }

    data class Subscriber(
        val sid: String,
        val callbackUrl: String,
        val timeout: Int,
        val serviceUrn: String,
        var expirationTime: Long
    )

    private val subscribers = mutableMapOf<String, Subscriber>()

    fun addSubscriber(sid: String, callbackUrl: String, timeout: Int, serviceUrn: String) {
        val expiration = System.currentTimeMillis() + (timeout * 1000L)
        subscribers[sid] = Subscriber(sid, callbackUrl, timeout, serviceUrn, expiration)
        Log.d(TAG, "Subscriber added: $sid, callback=$callbackUrl, timeout=$timeout")
    }

    fun removeSubscriber(sid: String) {
        subscribers.remove(sid)
        Log.d(TAG, "Subscriber removed: $sid")
    }

    fun getSubscribersForService(serviceUrn: String): List<Subscriber> {
        cleanupExpired()
        return subscribers.values.filter { it.serviceUrn == serviceUrn }
    }

    fun getAllSubscribers(): List<Subscriber> {
        cleanupExpired()
        return subscribers.values.toList()
    }

    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        subscribers.entries.removeIf { it.value.expirationTime < now }
    }

    fun cleanupAll() {
        subscribers.clear()
    }
}
