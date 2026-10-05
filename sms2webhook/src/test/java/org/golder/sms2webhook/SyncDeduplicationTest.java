package org.golder.sms2webhook;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Covers the deduplication rule across repeated syncs of the same inbox.
 *
 * <p>This is the bug the terminal-outcome check fixes. A message the server
 * refused permanently used to be written to the cache and then ignored by the
 * "have we sent this?" test, which compared the cached status against an exact
 * 200. So every sync re-uploaded every message the server had already refused,
 * indefinitely, and the same held for any 2xx that was not exactly 200.
 *
 * <p>The loop is mirrored rather than driven, because running it for real needs a
 * ContentResolver, a WorkManager and a live webhook. What is under test is the
 * decision {@code SmsStoreWorker} makes per message, which is what the bug was in.
 */
public class SyncDeduplicationTest {

    /** Status the cache reports for a key it does not hold. */
    private static final int NOT_CACHED = -1;

    /** hash to the status of its last terminal outcome. */
    private final Map<String, Integer> cache = new HashMap<>();

    /** Statuses the webhook will return, in order, one per upload attempt. */
    private final List<Integer> responses = new ArrayList<>();

    private int responseIndex;

    @Before
    public void setUp() {
        cache.clear();
        responses.clear();
        responseIndex = 0;
    }

    /** Queues the statuses the next upload attempts will receive. */
    private void serverAnswers(int... statuses) {
        responses.addAll(Arrays.stream(statuses).boxed().toList());
    }

    /** Mirrors {@code DigestCache.get}. */
    private int cached(String hash) {
        Integer status = cache.get(hash);
        return status == null ? NOT_CACHED : status;
    }

    /** Mirrors {@code DigestCache.set}. */
    private void record(String hash, int status) {
        cache.put(hash, status);
    }

    /**
     * Runs one pass of the sync over the given messages, in order.
     *
     * <p>Mirrors the worker loop: skip anything the cache already reports as
     * terminal, upload the rest, stop the pass on a transient failure without
     * recording it, and record every other outcome.
     *
     * @return how many uploads were attempted
     */
    private int syncPass(List<String> hashes) {
        int uploads = 0;
        for (String hash : hashes) {
            if (SmsStoreWorkerStatusHandling.isTerminal(cached(hash))) {
                continue;
            }
            if (responseIndex >= responses.size()) {
                // The sync wanted to upload something no test had queued a status
                // for, which is the re-upload regression showing up. Reported
                // plainly rather than as an index error, because that is what it is.
                throw new AssertionError("unexpected upload of " + hash
                        + " on pass " + (responseIndex + 1)
                        + "; the queue held " + responses.size() + " response(s)");
            }
            int status = responses.get(responseIndex++);
            uploads++;
            if (SmsStoreWorkerStatusHandling.isTransient(status)) {
                // The worker stops and retries this message, so it neither advances
                // past it nor records an outcome for it.
                break;
            }
            record(hash, status);
        }
        return uploads;
    }

    @Test
    public void aDeliveredMessageIsNotUploadedAgain() {
        serverAnswers(200);
        List<String> inbox = List.of("a");

        assertEquals(1, syncPass(inbox));
        assertEquals(0, syncPass(inbox));
        assertEquals("no second attempt should have reached the server", 1, responseIndex);
    }

    @Test
    public void aRefusedMessageIsNotUploadedAgain() {
        // The regression. Under the old exact-200 test the second pass uploaded it
        // once more, and so did every pass after that.
        serverAnswers(400);
        List<String> inbox = List.of("a");

        assertEquals(1, syncPass(inbox));
        assertEquals(0, syncPass(inbox));
        assertEquals(1, responseIndex);
    }

    @Test
    public void aNoContentDeliveryIsNotUploadedAgain() {
        // 204 is a delivery. Comparing against 200 exactly re-sent it every sync.
        serverAnswers(204);
        List<String> inbox = List.of("a");

        assertEquals(1, syncPass(inbox));
        assertEquals(0, syncPass(inbox));
        assertEquals(1, responseIndex);
    }

    @Test
    public void onlyTheRefusedMessageIsUploadedAgain() {
        // Three messages, one refused: the next pass re-sends nothing.
        serverAnswers(200, 400, 200);
        List<String> inbox = List.of("a", "b", "c");

        assertEquals(3, syncPass(inbox));
        assertEquals(0, syncPass(inbox));
        assertEquals(3, responseIndex);
    }

    @Test
    public void repeatedPassesNeverReupload() {
        serverAnswers(200, 200, 400, 204, 422);
        List<String> inbox = List.of("a", "b", "c", "d", "e");

        assertEquals(5, syncPass(inbox));
        for (int pass = 0; pass < 10; pass++) {
            assertEquals("pass " + pass + " re-uploaded something", 0, syncPass(inbox));
        }
        assertEquals(5, responseIndex);
    }

    @Test
    public void aTransientFailureIsNeitherRecordedNorSkipped() {
        serverAnswers(200, 503, 200);
        List<String> inbox = List.of("a", "b");

        // First pass delivers a, then hits a 503 on b and stops.
        assertEquals(2, syncPass(inbox));
        assertEquals("a transient failure must not be recorded as an outcome",
                NOT_CACHED, cached("b"));

        // The retry resumes at b rather than skipping it, and this time it lands.
        assertEquals(1, syncPass(inbox));
        assertEquals(200, cached("b"));
    }

    @Test
    public void rateLimitingDoesNotDiscardTheMessage() {
        serverAnswers(429, 200);
        List<String> inbox = List.of("a");

        assertEquals(1, syncPass(inbox));
        assertEquals(NOT_CACHED, cached("a"));
        assertEquals(1, syncPass(inbox));
        assertEquals(200, cached("a"));
    }

    @Test
    public void aMistypedApiKeyDoesNotDiscardTheBacklog() {
        // The server answers 401 for an unrecognised key. The user then corrects
        // it in Settings, and every message must still upload. Caching a 401 as a
        // final outcome marked the whole inbox permanently refused, so fixing the
        // key uploaded none of it.
        serverAnswers(401, 200, 200);
        List<String> inbox = List.of("a", "b");

        // Stops at the first refusal rather than sending a request per message.
        assertEquals(1, syncPass(inbox));
        assertEquals(NOT_CACHED, cached("a"));
        assertEquals(NOT_CACHED, cached("b"));

        // Key corrected: the pass resumes where it stopped and delivers both.
        assertEquals(2, syncPass(inbox));
        assertEquals(200, cached("a"));
        assertEquals(200, cached("b"));
    }

    @Test
    public void aWrongUrlDoesNotDiscardTheBacklog() {
        // Odoo answers 404 when the URL does not resolve to its /sms/upload route.
        serverAnswers(404, 200);
        List<String> inbox = List.of("a");

        assertEquals(1, syncPass(inbox));
        assertEquals(NOT_CACHED, cached("a"));

        assertEquals(1, syncPass(inbox));
        assertEquals(200, cached("a"));
    }

    @Test
    public void aRejectedPayloadIsNotRetried() {
        // The contrast: a 400 describes the message, so the same answer comes
        // back forever and re-sending it achieves nothing.
        serverAnswers(400);
        List<String> inbox = List.of("a");

        assertEquals(1, syncPass(inbox));
        assertEquals(400, cached("a"));
        assertEquals("a refused payload must not be re-sent", 0, syncPass(inbox));
        assertEquals(1, responseIndex);
    }

    @Test
    public void aFullRescanOfAnAlreadySyncedInboxSendsNothing() {
        // What the toolbar's sync button does: rewind the position and walk the
        // whole inbox again. Every message must be recognised, or a rescan
        // duplicates the entire inbox on the server.
        serverAnswers(200, 200, 200);
        List<String> inbox = List.of("a", "b", "c");

        assertEquals(3, syncPass(inbox));
        assertEquals(0, syncPass(inbox));
        assertEquals(3, responseIndex);
    }

    @Test
    public void aNewMessageAfterAFullSyncIsStillUploaded() {
        serverAnswers(200, 200);
        List<String> inbox = List.of("a");

        assertEquals(1, syncPass(inbox));
        // A message arrives at the end of the inbox.
        List<String> grown = List.of("a", "b");
        assertEquals("only the new message should be uploaded", 1, syncPass(grown));
        assertEquals(2, responseIndex);
        assertEquals(200, cached("b"));
    }
}