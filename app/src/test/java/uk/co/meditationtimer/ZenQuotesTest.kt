package uk.co.meditationtimer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ZenQuotesTest {
    private val response="""[{"q":"A quiet moment.","a":"Test author","h":"<script>ignored</script>"}]"""

    @Test fun parsesPlainQuoteAndAuthorWithoutUsingHtml() {
        assertEquals(Quote("A quiet moment.","Test author"),ZenQuotes.parse(response))
    }
    @Test fun malformedEmptyAndRateLimitPayloadsStayHidden() {
        listOf("not json", "[]", "{}", "[{\"q\":\"\",\"a\":\"Author\"}]",
            "[{\"q\":\"Too many requests\",\"a\":\"zenquotes.io\"}]",
            "[{\"q\":5,\"a\":\"Author\"}]", response+"garbage",
            "[{\"q\":\"${"a".repeat(2001)}\",\"a\":\"Author\"}]").forEach {
            assertNull(ZenQuotes.parse(it))
        }
    }
    @Test fun successfulQuoteIsReusedForAnHourWithoutFurtherRequests() = runBlocking {
        var now=0L; var calls=0
        val client=ZenQuotes({now}) { calls++;response }
        assertNotNull(client.load())
        now=3599999
        assertNotNull(client.load());assertEquals(1,calls)
        now=3600000
        assertNotNull(client.load());assertEquals(2,calls)
    }
    @Test fun failureAndTimeoutStayHiddenAndRetryIsThrottled() = runBlocking {
        var now=0L; var calls=0
        val client=ZenQuotes({now}) { calls++; if(calls==1) throw java.net.SocketTimeoutException() else response }
        assertNull(client.load())
        now=59999;assertNull(client.load());assertEquals(1,calls)
        now=60000;assertNotNull(client.load());assertEquals(2,calls)
    }
    @Test fun failedRefreshDoesNotReturnExpiredQuote() = runBlocking {
        var now=0L; var successful=true
        val client=ZenQuotes({now}) { if(successful) response else null }
        assertNotNull(client.load())
        successful=false;now=3600000
        assertNull(client.load())
    }
}
