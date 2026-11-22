package com.autopilot.android.domain

import com.autopilot.android.domain.validation.UrlValidator
import org.junit.Assert.*
import org.junit.Test

class UrlValidatorTest {

    @Test
    fun normalizeAndValidate_validHttpAndHttps() {
        assertEquals("https://example.com", UrlValidator.normalizeAndValidate("https://example.com"))
        assertEquals("http://test.org/path", UrlValidator.normalizeAndValidate("http://test.org/path"))
    }

    @Test
    fun normalizeAndValidate_missingScheme_prefixesHttps() {
        assertEquals("https://google.com", UrlValidator.normalizeAndValidate("google.com"))
        assertEquals("https://news.ycombinator.com/item?id=1", UrlValidator.normalizeAndValidate("news.ycombinator.com/item?id=1"))
    }

    @Test
    fun normalizeAndValidate_invalidUrls_returnsNull() {
        assertNull(UrlValidator.normalizeAndValidate(""))
        assertNull(UrlValidator.normalizeAndValidate("   "))
        assertNull(UrlValidator.normalizeAndValidate("not_a_valid_domain"))
        assertNull(UrlValidator.normalizeAndValidate("http://"))
    }

    @Test
    fun parseMultiple_multilineAndSeparators() {
        val input = """
            https://site1.com
            site2.org, site3.net;
            invalid_domain
            https://site1.com
        """.trimIndent()

        val (valid, invalid) = UrlValidator.parseMultiple(input)

        assertEquals(3, valid.size)
        assertTrue(valid.contains("https://site1.com"))
        assertTrue(valid.contains("https://site2.org"))
        assertTrue(valid.contains("https://site3.net"))
        assertEquals(1, invalid.size)
        assertEquals("invalid_domain", invalid[0])
    }

    @Test
    fun extractHost_returnsCorrectDomain() {
        assertEquals("example.com", UrlValidator.extractHost("https://example.com/test"))
        assertEquals("sub.domain.org", UrlValidator.extractHost("http://sub.domain.org:8080/path"))
    }

    @Test
    fun isDomainAllowed_checksAllowedListAndSubdomains() {
        val allowed = listOf("example.com", "api.github.com")

        assertTrue(UrlValidator.isDomainAllowed("https://example.com/page", allowed))
        assertTrue(UrlValidator.isDomainAllowed("https://sub.example.com", allowed))
        assertTrue(UrlValidator.isDomainAllowed("https://api.github.com/v1", allowed))

        assertFalse(UrlValidator.isDomainAllowed("https://malicious.org", allowed))
        assertFalse(UrlValidator.isDomainAllowed("https://fakeexample.com", allowed))
    }

    @Test
    fun isDomainAllowed_emptyListAllowsAll() {
        assertTrue(UrlValidator.isDomainAllowed("https://anything.com", emptyList()))
    }
}
