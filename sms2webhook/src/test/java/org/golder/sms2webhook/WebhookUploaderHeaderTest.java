package org.golder.sms2webhook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for the {@code X-Already-Existed} header interpretation.
 *
 * <p>The failure these guard against is mislabelling a fresh upload as a
 * duplicate, so the cases that matter are the ones that must NOT read as
 * "true".
 */
public class WebhookUploaderHeaderTest {

    @Test
    public void recognisesExplicitTrue() {
        assertTrue(WebhookUploader.isAlreadyExistedHeader("true"));
    }

    @Test
    public void ignoresCase() {
        assertTrue(WebhookUploader.isAlreadyExistedHeader("TRUE"));
        assertTrue(WebhookUploader.isAlreadyExistedHeader("True"));
    }

    @Test
    public void toleratesSurroundingWhitespace() {
        assertTrue(WebhookUploader.isAlreadyExistedHeader(" true "));
        assertTrue(WebhookUploader.isAlreadyExistedHeader("\ttrue\n"));
    }

    @Test
    public void absentHeaderIsNotADuplicate() {
        assertFalse(WebhookUploader.isAlreadyExistedHeader(null));
    }

    @Test
    public void emptyHeaderIsNotADuplicate() {
        assertFalse(WebhookUploader.isAlreadyExistedHeader(""));
        assertFalse(WebhookUploader.isAlreadyExistedHeader("   "));
    }

    @Test
    public void otherValuesAreNotADuplicate() {
        assertFalse(WebhookUploader.isAlreadyExistedHeader("false"));
        assertFalse(WebhookUploader.isAlreadyExistedHeader("0"));
        assertFalse(WebhookUploader.isAlreadyExistedHeader("1"));
        assertFalse(WebhookUploader.isAlreadyExistedHeader("yes"));
        assertFalse(WebhookUploader.isAlreadyExistedHeader("null"));
    }

    @Test
    public void misleadingSubstringsAreNotADuplicate() {
        // Guards against a substring match such as contains("true").
        assertFalse(WebhookUploader.isAlreadyExistedHeader("untrue"));
        assertFalse(WebhookUploader.isAlreadyExistedHeader("not-true"));
        assertFalse(WebhookUploader.isAlreadyExistedHeader("true-ish"));
    }

    @Test
    public void newUploaderReportsNoDuplicateBeforeAnyUpload() {
        WebhookUploader uploader = new WebhookUploader("https://example.invalid/hook", "key");
        assertFalse(uploader.wasAlreadyExisted());
    }
}
