package com.predictor.web.net

/**
 * The API base URL to use by default, which is the one truly platform-specific piece
 * of the client. On the web it's the page's own origin (same host as the API); on the
 * mobile apps it's the address the backend is reachable at. Each target provides its
 * own `actual`.
 */
expect fun defaultBaseUrl(): String
