package com.sysadmindoc.nimbus.util

/**
 * A short, redacted label for a caught exception.
 *
 * Exception messages routinely carry the thing that failed: a request URL with
 * coordinates in the query string, a file path, an API key echoed back by a
 * provider. Logging `e.message` from background workers would put all of that
 * in logcat and in any crash report attached to it. The class name alone says
 * what went wrong well enough to act on, and carries none of it.
 */
fun Throwable.failureClass(): String = this::class.java.simpleName.ifBlank { "Exception" }
