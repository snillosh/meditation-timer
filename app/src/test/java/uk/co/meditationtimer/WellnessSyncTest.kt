package uk.co.meditationtimer

import android.app.Application
import android.content.Context
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class WellnessSyncTest {
    private lateinit var context: Context
    private lateinit var settings: WellnessSettings
    private lateinit var store: Store

    @Before
    fun before() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("still.db")
        context.getSharedPreferences("wellness_sync", 0).edit().clear().commit()
        settings = WellnessSettings(context)
        settings.save(WellnessConnection.DEFAULT_SERVER, null, true)
        store = Store(context)
        val instant = Instant.parse("2026-11-17T12:00:00Z")
        store.importHistory(
            listOf(
                Completed(
                    "saved",
                    instant.toEpochMilli(),
                    60000,
                    instant.atOffset(ZoneOffset.UTC).toLocalDate(),
                    "UTC",
                )
            )
        )
    }

    @After
    fun after() {
        store.close()
        context.deleteDatabase("still.db")
    }

    private fun sync(request: (String, String, JSONObject?) -> WellnessHttpClient.Response) =
        WellnessSynchronizer(context, request, { "test-credential" })

    private fun ack(event: JSONObject?, code: Int = 201) =
        WellnessHttpClient.Response(
            code,
            JSONObject()
                .put("eventId", event!!.getString("eventId"))
                .put("status", if (code == 201) "accepted" else "duplicate")
                .put("receivedAt", "2026-11-17T12:00:00Z")
                .toString(),
        )

    @Test
    fun offlineRetriesKeepHistoryAndStableEventIdentityUntilAcknowledged() {
        var first = ""
        assertEquals(
            SyncOutcome.RETRY,
            sync { _, _, event ->
                    first = event!!.getString("eventId")
                    throw IOException("offline")
                }
                .run(),
        )
        assertEquals(1, store.wellnessPending(WellnessConnection.DEFAULT_SERVER).size)
        assertEquals(
            SyncOutcome.SUCCESS,
            sync { _, _, event ->
                    assertEquals(first, event!!.getString("eventId"))
                    ack(event, 200)
                }
                .run(),
        )
        assertTrue(store.wellnessPending(WellnessConnection.DEFAULT_SERVER).isEmpty())
        assertEquals(1, store.history().size)
        assertTrue(settings.lastSuccess() > 0)
    }

    @Test
    fun disabledSyncDoesNotReadCredentialsOrSendHistory() {
        settings.save(WellnessConnection.DEFAULT_SERVER, null, false)
        assertEquals(
            SyncOutcome.SUCCESS,
            WellnessSynchronizer(
                    context,
                    { _, _, _ ->
                        fail("network")
                        error("network")
                    },
                    {
                        fail("credential")
                        null
                    },
                )
                .run(),
        )
    }

    @Test
    fun rejectedKeyPausesUntilManualRetry() {
        var count = 0
        val worker = sync { _, _, _ ->
            count++
            WellnessHttpClient.Response(401, "")
        }
        assertEquals(SyncOutcome.SUCCESS, worker.run())
        worker.run()
        assertEquals(1, count)
        assertEquals(1, store.wellnessPending(WellnessConnection.DEFAULT_SERVER).size)
        settings.manualRetry()
        assertEquals(SyncOutcome.SUCCESS, sync { _, _, event -> ack(event) }.run())
    }

    @Test
    fun serverBackoffAndMalformedAcknowledgementNeverDropSession() {
        assertEquals(
            SyncOutcome.RETRY,
            sync { _, _, _ -> WellnessHttpClient.Response(429, "", 120) }.run(),
        )
        assertTrue(settings.retryAt() > System.currentTimeMillis() + 110000)
        sync { _, _, _ ->
                fail("backoff ignored")
                error("backoff")
            }
            .run()
        settings.manualRetry()
        assertEquals(
            SyncOutcome.RETRY,
            sync { _, _, _ -> WellnessHttpClient.Response(201, "{}") }.run(),
        )
        assertEquals(1, store.wellnessPending(WellnessConnection.DEFAULT_SERVER).size)
    }

    @Test
    fun payloadRejectionRetainsHistoryAndCanBeRetriedExplicitly() {
        sync { _, _, _ -> WellnessHttpClient.Response(400, "") }.run()
        assertEquals(1, store.history().size)
        assertEquals(1, store.wellnessCounts(WellnessConnection.DEFAULT_SERVER).second)
        assertTrue(store.wellnessPending(WellnessConnection.DEFAULT_SERVER).isEmpty())
        store.wellnessRetryRejected(WellnessConnection.DEFAULT_SERVER)
        sync { _, _, event -> ack(event) }.run()
        assertEquals(0, store.wellnessCounts(WellnessConnection.DEFAULT_SERVER).first)
    }

    @Test
    fun settingsChangesDuringFlightDoNotAcknowledgeOldDestination() {
        sync { _, _, event ->
                settings.save("https://other.example.com", null, true)
                ack(event)
            }
            .run()
        assertEquals(1, store.wellnessPending(WellnessConnection.DEFAULT_SERVER).size)
        assertEquals(1, store.wellnessPending("https://other.example.com").size)
    }

    @Test
    fun deletionDuringFlightCannotRestoreLocalHistory() {
        sync { _, _, event ->
                store.delete("saved")
                ack(event)
            }
            .run()
        assertTrue(store.history().isEmpty())
        assertEquals(0, store.wellnessCounts(WellnessConnection.DEFAULT_SERVER).first)
    }

    @Test
    fun explicitConnectionTestWhileDisabledDoesNotUploadHistory() {
        settings.save(WellnessConnection.DEFAULT_SERVER, null, false)
        assertEquals(
            SyncOutcome.SUCCESS,
            sync { _, _, event ->
                    assertNull(event)
                    WellnessHttpClient.Response(
                        200,
                        """{"application":"meditation","permissions":["meditation:write"],"supportedEvents":{"meditation.session.completed":[1]}}""",
                    )
                }
                .run(true),
        )
        assertTrue(settings.status().startsWith("Connection verified"))
        assertEquals(1, store.wellnessPending(WellnessConnection.DEFAULT_SERVER).size)
    }
}
