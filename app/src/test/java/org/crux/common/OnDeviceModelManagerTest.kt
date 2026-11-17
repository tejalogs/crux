package org.crux.common

import org.crux.data.ai.OnDeviceModelSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class OnDeviceModelManagerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun supportedModelsCatalogHasValidUrlsAndSizes() {
        val models = OnDeviceModelSpec.SupportedModels
        assertTrue(models.size >= 4)
        for (spec in models) {
            assertTrue("Expected .litertlm filename for ${spec.modelId}", spec.fileName.endsWith(".litertlm"))
            assertTrue("Expected positive size for ${spec.modelId}", spec.downloadSizeBytes > 0L)
            assertTrue("Untrusted URL for ${spec.modelId}", ModelDownloadNetwork.isTrustedModelUrl(spec.downloadUrl))
            assertEquals(spec, OnDeviceModelSpec.findById(spec.modelId))
            assertEquals(spec, OnDeviceModelSpec.findById(spec.displayName.lowercase()))
        }
        assertEquals(OnDeviceModelSpec.Gemma4E2B, OnDeviceModelSpec.findById("unknown-model-id"))
    }

    @Test
    fun formatModelBytesFormatsMbAndGbConsistently() {
        assertEquals("0 MB", OnDeviceModelSpec.formatModelBytes(0L))
        assertEquals("585 MB", OnDeviceModelSpec.formatModelBytes(613_722_264L))
        assertEquals("2.41 GB", OnDeviceModelSpec.formatModelBytes(2_588_147_712L))
    }

    @Test
    fun trustedHostValidatorAllowsHuggingFaceCdnsAndRejectsUntrusted() {
        assertTrue(
            ModelDownloadNetwork.isTrustedModelUrl(
                "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            ),
        )
        assertTrue(ModelDownloadNetwork.isTrustedModelUrl("https://cas-bridge.xethub.hf.co/blob/abc"))
        assertTrue(ModelDownloadNetwork.isTrustedModelUrl("https://d1234.cloudfront.net/file.litertlm"))

        assertFalse(
            ModelDownloadNetwork.isTrustedModelUrl(
                "http://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            ),
        )
        assertFalse(ModelDownloadNetwork.isTrustedModelUrl("https://evil.example.com/model.litertlm"))
        assertFalse(ModelDownloadNetwork.isTrustedModelUrl("not a url"))
        assertFalse(ModelDownloadNetwork.isTrustedModelUrl(null))
    }

    @Test
    fun contentRangeHeaderParserExtractsStartAndTotal() {
        assertEquals(1024L to 4096L, ModelDownloadNetwork.parseContentRangeStartAndTotal("bytes 1024-2047/4096"))
        assertEquals(0L to -1L, ModelDownloadNetwork.parseContentRangeStartAndTotal("bytes 0-511/*"))
        assertNull(ModelDownloadNetwork.parseContentRangeStartAndTotal("invalid"))
        assertNull(ModelDownloadNetwork.parseContentRangeStartAndTotal(null))
    }

    @Test
    fun modelStoreValidatesLiteRtLmHeaderAndFinalizesAtomically() {
        val dir = tempFolder.newFolder("models")
        val store = OnDeviceModelStore(modelsDir = dir, minValidModelBytes = 16L)
        val spec = OnDeviceModelSpec.Qwen3Compact

        assertEquals(OnDeviceModelState.NotDownloaded(spec, 0L), store.inspectIdleState(spec))

        val part = store.partFile(spec)
        part.writeBytes("LITERTLM".encodeToByteArray() + ByteArray(24))
        assertEquals(32L, store.partialBytes(spec))
        assertNull(store.installedModelFile(spec))

        assertThrows(IOException::class.java) {
            store.finalizePartFile(spec, expectedTotalBytes = 64L)
        }

        val installed = store.finalizePartFile(spec, expectedTotalBytes = 32L)
        assertTrue(installed.exists())
        assertFalse(part.exists())
        assertNotNull(store.installedModelFile(spec))
        assertEquals(32L, store.totalStorageUsedBytes())
        assertTrue(store.inspectIdleState(spec) is OnDeviceModelState.Downloaded)

        store.deleteModelFiles(spec)
        assertNull(store.installedModelFile(spec))
        assertEquals(0L, store.totalStorageUsedBytes())
    }

    @Test
    fun modelStoreRejectsHtmlOrCorruptPayloadWithoutLiteRtLmHeader() {
        val dir = tempFolder.newFolder("models_corrupt")
        val store = OnDeviceModelStore(modelsDir = dir, minValidModelBytes = 8L)
        val spec = OnDeviceModelSpec.Gemma4E2B

        store.finalFile(spec).writeBytes("<html>403 Forbidden</html>".encodeToByteArray())
        assertNull(store.installedModelFile(spec))

        store.partFile(spec).writeBytes("<!DOCTYPE html>error page".encodeToByteArray())
        assertThrows(IOException::class.java) {
            store.finalizePartFile(spec, expectedTotalBytes = store.partFile(spec).length())
        }
        assertFalse(store.partFile(spec).exists())
    }

    @Test
    fun sanitizeModelOutputRemovesThinkingTagsCleanly() {
        val raw = "<think>internal chain of thought</think>\n\nFinal concise answer."
        assertEquals("Final concise answer.", LiteRtLmRuntime.sanitizeModelOutput(raw))
    }
}
