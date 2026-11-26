package org.crux.ui.screen.home

import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Most distinct hosts kept per site; later new hosts are only counted in [SiteContacts.droppedRequests]. */
internal const val MAX_CONTACTED_HOSTS_PER_SITE = 300

/**
 * Two-label public suffixes common enough to matter for the hosts these sites load (ad and CDN
 * hosts on country domains). Without a full public-suffix list, anything else is taken as one
 * label, which is right for every .com, .net, .io, .ms and the sites' own domains.
 */
private val MULTI_LABEL_SUFFIXES =
    setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk",
        "co.in", "net.in", "org.in", "gov.in",
        "co.jp", "ne.jp", "or.jp",
        "com.au", "net.au", "org.au",
        "com.br", "com.mx", "com.ar", "com.tr", "com.cn", "com.hk", "com.tw", "com.sg", "com.my",
        "co.nz", "co.za", "co.kr", "co.id", "co.il",
    )

/** The registrable domain (eTLD+1) of [host]: `www.static.example.co.uk` becomes `example.co.uk`. */
internal fun registrableDomain(host: String): String {
    val h = host.lowercase().trimEnd('.')
    if (h.isEmpty() || ':' in h || h.all { it.isDigit() || it == '.' }) return h
    val labels = h.split('.').filter { it.isNotEmpty() }
    if (labels.size <= 2) return labels.joinToString(".")
    val lastTwo = labels.takeLast(2).joinToString(".")
    return if (lastTwo in MULTI_LABEL_SUFFIXES) labels.takeLast(3).joinToString(".") else lastTwo
}

/**
 * Sign-in, captcha and bot-check services that sites allow but do not own. A request to them is
 * a third party even though the page may load it.
 */
private val SHARED_SERVICE_DOMAINS =
    setOf("google.com", "youtube.com", "apple.com", "gstatic.com", "recaptcha.net", "arkoselabs.com", "funcaptcha.com", "arkose.com")

/** Each site's own registrable domains: its allowed hosts, minus the shared sign-in services. */
private val FIRST_PARTY_DOMAINS: Map<LiveWebSite, Set<String>> =
    LiveWebSite.entries.associateWith { site ->
        site.allowedHosts.map(::registrableDomain).toSet() - SHARED_SERVICE_DOMAINS
    }

/** True when [host] belongs to a company other than the one running [site]. */
internal fun isThirdPartyHost(
    site: LiveWebSite,
    host: String,
): Boolean = registrableDomain(host) !in FIRST_PARTY_DOMAINS.getValue(site)

/** One host a site's page contacted, as shown in the Traffic log. Never a path or a query. */
internal data class ContactedHost(
    val host: String,
    val thirdParty: Boolean,
    val requests: Int,
    val blocked: Int,
)

internal data class SiteContacts(
    val site: LiveWebSite,
    /** Third parties first, then by request count. */
    val hosts: List<ContactedHost>,
    /** Requests to new hosts after the per-site limit was reached. */
    val droppedRequests: Int,
) {
    val thirdPartyHosts: Int get() = hosts.count { it.thirdParty }
}

/**
 * In-memory tally of the hosts each site's page reached. It is written from WebView's network
 * threads, so every update is a lock-free hash lookup plus an atomic increment; the first-party
 * check runs once per new host. Nothing is persisted, and the tally starts over at local midnight.
 *
 * The counts are a close tally, not an audit trail: a request already in flight when the log is
 * cleared (or rolls over at midnight) may land in the book being dropped, and hosts arriving on
 * several threads at the same moment can take a site a few hosts past [maxHostsPerSite].
 */
internal class ContactLogBook(
    private val maxHostsPerSite: Int = MAX_CONTACTED_HOSTS_PER_SITE,
    private val clock: () -> Long = System::currentTimeMillis,
    private val nextMidnight: (Long) -> Long = ::nextLocalMidnight,
) {
    private class Tally(
        val thirdParty: Boolean,
    ) {
        val requests = AtomicInteger()
        val blocked = AtomicInteger()
    }

    private class SiteBook {
        val hosts = ConcurrentHashMap<String, Tally>()
        val dropped = AtomicInteger()
    }

    private val books = AtomicReferenceArray<SiteBook?>(LiveWebSite.entries.size)
    private val version = MutableStateFlow(0L)

    private val resetsAt = AtomicLong(nextMidnight(clock()))

    /** Bumped when a host appears for the first time or the log is cleared, not on every request. */
    val changes: StateFlow<Long> = version.asStateFlow()

    fun record(
        site: LiveWebSite,
        rawHost: String?,
        blocked: Boolean,
    ) {
        val host = rawHost?.lowercase()?.trimEnd('.')?.takeIf { it.isNotEmpty() } ?: return
        rollOverIfNewDay()
        val book = bookFor(site)
        val tally =
            book.hosts[host] ?: run {
                if (book.hosts.size >= maxHostsPerSite) {
                    book.dropped.incrementAndGet()
                    return
                }
                var added = false
                val created =
                    book.hosts.computeIfAbsent(host) {
                        added = true
                        Tally(isThirdPartyHost(site, host))
                    }
                if (added) bumpVersion()
                created
            }
        tally.requests.incrementAndGet()
        if (blocked) tally.blocked.incrementAndGet()
    }

    fun snapshot(): List<SiteContacts> {
        rollOverIfNewDay()
        return LiveWebSite.entries.mapNotNull { site ->
            val book = books.get(site.ordinal) ?: return@mapNotNull null
            val hosts =
                book.hosts
                    .map { (host, tally) -> ContactedHost(host, tally.thirdParty, tally.requests.get(), tally.blocked.get()) }
                    .sortedWith(compareByDescending<ContactedHost> { it.thirdParty }.thenByDescending { it.requests }.thenBy { it.host })
            val dropped = book.dropped.get()
            if (hosts.isEmpty() && dropped == 0) null else SiteContacts(site, hosts, dropped)
        }
    }

    fun clear() {
        resetsAt.set(nextMidnight(clock()))
        wipe()
    }

    private fun bookFor(site: LiveWebSite): SiteBook {
        books.get(site.ordinal)?.let { return it }
        val fresh = SiteBook()
        return if (books.compareAndSet(site.ordinal, null, fresh)) fresh else books.get(site.ordinal) ?: fresh
    }

    /** Only the thread that moves the deadline on wipes, so a midnight rollover happens once. */
    private fun rollOverIfNewDay() {
        val now = clock()
        val due = resetsAt.get()
        if (now >= due && resetsAt.compareAndSet(due, nextMidnight(now))) wipe()
    }

    private fun wipe() {
        for (i in 0 until books.length()) books.set(i, null)
        bumpVersion()
    }

    private fun bumpVersion() {
        version.update { it + 1 }
    }
}

/** The next local midnight after [now], in epoch milliseconds. */
internal fun nextLocalMidnight(now: Long): Long =
    Calendar
        .getInstance()
        .apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

/** "What this site contacted": the one process-wide [ContactLogBook] the web tabs write to. */
internal object LiveWebContactLog {
    private val book = ContactLogBook()

    val changes: StateFlow<Long> get() = book.changes

    fun record(
        site: LiveWebSite,
        host: String?,
        blocked: Boolean,
    ) = book.record(site, host, blocked)

    fun snapshot(): List<SiteContacts> = book.snapshot()

    fun clear() = book.clear()
}
