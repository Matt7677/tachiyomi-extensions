package eu.kanade.tachiyomi.extension.ja.senmanga

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ChampionCross : ParsedHttpSource() {
    override val lang: String = "ja"
    override val supportsLatest = true
    override val name = "ChampionCross"
    override val baseUrl = "https://championcross.jp"

    override fun popularMangaSelector() = ".ranking-item"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.select("div.title-text").text().trim()
        thumbnail_url = element.select("source").attr("data-srcset")
            .split(",")[0].replace("//", "https://").replace(" 2x", "")
        setUrlWithoutDomain(element.select("a").attr("href"))
    }

    override fun popularMangaNextPageSelector() = "a.next"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/ranking/manga?page=$page")

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("h1.title").text()
        thumbnail_url = document.select("div.cover img").first()!!.attr("src")
        description = document.select("div.description").text()
    }

    override fun chapterListSelector() = "div.series-ep-list-item"

    @SuppressLint("DefaultLocale")
    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val chapterText = element.select("span.series-ep-list-item-h-text").text()
        chapter_number = chapterText.toFloatOrNull() ?: -1f
        setUrlWithoutDomain(element.select("a").attr("href"))
        date_upload = parseDate(element.select("time").attr("datetime"))
    }

    private fun parseDate(date: String): Long {
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            format.parse(date)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val chapterId = document.select("div#comici-viewer").attr("comici-viewer-id")
        val pageCount = document.select("script").last().data().let {
            val pattern = "'pageNum':(\\d+)".toRegex()
            pattern.find(it)?.groupValues?.get(1)?.toInt() ?: 0
        }
        val pages = mutableListOf<Page>()
        val pagesApiUrl = "$baseUrl/book/contentsInfo?user-id=1301053&comici-viewer-id=$chapterId&page-from=0&page-to=$pageCount"
        val pagesResponse = client.newCall(GET(pagesApiUrl)).execute().body?.string()
        val json = org.json.JSONObject(pagesResponse)
        val pageList = json.getJSONArray("result")
        for (i in 0 until pageList.length()) {
            val pageData = pageList.getJSONObject(i)
            pages.add(
                Page(i, "", pageData.getString("imageUrl"))
            )
        }
        return pages
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/directory/last_update?page=$page", headers)

    // Utility function for search
    override fun searchMangaRequest(page: Int, query: String, filters: eu.kanade.tachiyomi.source.model.FilterList) = GET(
        "$baseUrl/search?page=$page&s=$query",
        headers
    )
}
