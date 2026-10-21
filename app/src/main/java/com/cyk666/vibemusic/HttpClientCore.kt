package com.cyk666.vibemusic

import okhttp3.ConnectionPool
import okhttp3.Dispatcher

/**
 * Process-wide OkHttp plumbing.
 *
 * The app intentionally builds four clients: the authenticated API client, the
 * cookie-refresh client (must NOT carry the Bearer interceptor), the update
 * check client and the update-download fallback client. That separation is
 * deliberate and stays. What was accidental is that each builder also created
 * its own [Dispatcher] and [ConnectionPool] — four thread pools and four
 * idle-connection pools for a single-user app.
 *
 * Sharing these two objects changes no request semantics (interceptors, cookie
 * jars, timeouts and redirect policy all stay per-client) while collapsing the
 * pools to one set and letting requests to the same host reuse sockets.
 *
 * Both are left at OkHttp's defaults so concurrency limits are unchanged
 * (maxRequests=64, maxRequestsPerHost=5, 5 idle connections / 5 minutes).
 */
internal object HttpClientCore {
    val dispatcher: Dispatcher = Dispatcher()

    val connectionPool: ConnectionPool = ConnectionPool()
}
