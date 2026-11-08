package org.crux.data.database.app.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ExportRedactionTest {
    @Test
    fun blanksCookiesAndKeepsOtherFields() {
        val out = redactCredentialForExport("""{"cookies":"sessionid=abc; csrftoken=x","userId":"42","username":"me"}""")
        val obj = Json.parseToJsonElement(out).jsonObject
        assertEquals("", obj.getValue("cookies").jsonPrimitive.content)
        assertEquals("42", obj.getValue("userId").jsonPrimitive.content)
        assertEquals("me", obj.getValue("username").jsonPrimitive.content)
    }

    @Test
    fun blanksNestedCookieFields() {
        val out = redactCredentialForExport("""{"web":{"Cookie":"a=b"},"id":"1"}""")
        val web =
            Json
                .parseToJsonElement(out)
                .jsonObject
                .getValue("web")
                .jsonObject
        assertEquals("", web.getValue("Cookie").jsonPrimitive.content)
    }

    @Test
    fun leavesCredentialWithoutCookiesUntouched() {
        assertEquals("{}", redactCredentialForExport("{}"))
        assertEquals("""{"cookies":"","userId":"1"}""", redactCredentialForExport("""{"cookies":"","userId":"1"}"""))
    }

    @Test
    fun replacesUnparseableCredentialWithEmptyObject() {
        assertEquals("{}", redactCredentialForExport("not json"))
        assertEquals("{}", redactCredentialForExport("[1,2]"))
    }

    @Test
    fun accountExportUsesRedaction() {
        val account =
            DbAccount(
                account_key = org.crux.model.MicroBlogKey("u", "instagram.com"),
                credential_json = """{"cookies":"secret","userId":"1"}""",
                platformId = "instagram",
                last_active = 1L,
            )
        assertEquals(false, account.redactedForExport().credential_json.contains("secret"))
    }
}
