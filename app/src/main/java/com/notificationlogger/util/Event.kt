package com.notificationlogger.util

/**
 * Wraps a value that should only be consumed ONCE.
 * Used for one-shot UI events (navigation, share sheet, toasts) so that
 * LiveData observers don't re-fire the action on screen rotation or resume.
 */
class Event<out T>(private val content: T) {

    private var hasBeenHandled = false

    /**
     * Returns the content and marks it as handled.
     * Returns null if it has already been consumed.
     */
    fun getIfNotHandled(): T? {
        return if (hasBeenHandled) null
        else { hasBeenHandled = true; content }
    }

    /** Peek at the content without marking it as handled. */
    fun peek(): T = content
}
