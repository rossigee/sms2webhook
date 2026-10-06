package org.golder.sms2webhook;

import java.util.Locale;

/**
 * The state the main screen shows above the sync button.
 *
 * <p>Replaces three counters. They answered "what has happened in my history"
 * rather than "is there anything outstanding", and the derived numbers were not
 * trustworthy: on a device with 624 messages and an empty cache they read
 * Uploaded 0, Failed 0, which looks identical to a fully synced device.
 *
 * <p>Pure and Android-free so the mapping can be pinned by unit tests, which is
 * the only place this logic can be asserted at all.
 */
final class SyncStatus {

    enum State {
        /** Messages the server permanently refused. Not retried, so it needs clearing the cache. */
        REFUSED,
        /** A sync is running. */
        SYNCING,
        /** Messages the provider holds that have not been delivered yet. */
        UNSYNCED,
        /** Everything the provider holds has been accepted by the server. */
        SYNCED,
        /**
         * The inbox could not be read, so nothing can be said about what is outstanding.
         *
         * <p>Distinct from {@link State#SYNCED} on purpose. A failed count used to
         * report zero, which is indistinguishable from a genuinely empty inbox: on a
         * device holding 438 messages with the SMS permission refused, the screen
         * confidently showed a green "0 uploaded".
         */
        CANNOT_READ
    }

    final State state;
    final int count;
    final int percent;

    private SyncStatus(State state, int count, int percent) {
        this.state = state;
        this.count = count;
        this.percent = percent;
    }

    /**
     * @param inbox     messages the provider is holding
     * @param uploaded  messages the server accepted, from the cache
     * @param refused   messages the server permanently refused, from the cache
     * @param syncing   whether a sync is running
     * @param percent   sync progress, 0-100
     */
    static SyncStatus of(int inbox, int uploaded, int refused, boolean syncing, int percent) {
        return of(inbox, uploaded, refused, syncing, percent, true);
    }

    /**
     * @param inboxReadable whether the provider could be queried at all. False when
     *                      the SMS permission is refused.
     */
    static SyncStatus of(int inbox, int uploaded, int refused, boolean syncing, int percent,
                         boolean inboxReadable) {
        if (!inboxReadable) {
            return new SyncStatus(State.CANNOT_READ, 0, percent);
        }

        // The cache can outlive the provider's messages, so an inbox smaller than
        // the recorded count is normal rather than an error, and a negative
        // remainder must never surface as a count.
        int undelivered = Math.max(0, inbox - uploaded);

        if (refused > 0) {
            return new SyncStatus(State.REFUSED, refused, percent);
        }
        if (syncing) {
            return new SyncStatus(State.SYNCING, undelivered, percent);
        }
        if (undelivered > 0) {
            return new SyncStatus(State.UNSYNCED, undelivered, percent);
        }
        return new SyncStatus(State.SYNCED, inbox, percent);
    }

    /**
     * The headline for the non-syncing states.
     *
     * <p>{@link State#SYNCING} is left to the caller, because its text carries a
     * percentage and therefore a string resource, which this class does not touch.
     */
    String headline() {
        switch (state) {
            case REFUSED:
                return String.format(Locale.getDefault(), "%d refused", count);
            case SYNCING:
                return String.format(Locale.getDefault(), "%d waiting", count);
            case UNSYNCED:
                return count == 1
                        ? "1 not uploaded yet"
                        : String.format(Locale.getDefault(), "%d not uploaded yet", count);
            case SYNCED:
                return count == 1
                        ? "1 uploaded"
                        : String.format(Locale.getDefault(), "%d uploaded", count);
            case CANNOT_READ:
            default:
                return "Can't read your messages";
        }
    }

    /** The supporting line, or null when there is nothing useful to add. */
    String detail() {
        switch (state) {
            case REFUSED:
                // Worth spelling out, because refused messages are never retried
                // and the count cannot go down on its own.
                return "The server declined these. They are not retried.";
            case SYNCING:
                return null;
            case UNSYNCED:
                return "Tap Sync to send them.";
            case CANNOT_READ:
                return "Grant SMS permission so this app can see them.";
            case SYNCED:
            default:
                return null;
        }
    }
}