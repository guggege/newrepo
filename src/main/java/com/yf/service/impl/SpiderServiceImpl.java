package com.yf.service.impl;

import com.yf.dao.SpiderTaskMapper;
import com.yf.entity.Novel;
import com.yf.entity.NovelChapter;
import com.yf.entity.SpiderTask;
import com.yf.service.SpiderService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SpiderServiceImpl implements SpiderService {

    private static final String[] DINGDIAN_MIRROR_HOSTS = {
            "www.dingdlannn.cc",
            "www.diandingnnn.cc",
            "www.dingdiann.com",
            "www.dingdiann.cc"
    };

    private static final String[] CHAPTER_TRY_CHARSETS = {
            "GBK", "GB18030", "UTF-8", "GB2312"
    };

    @Resource
    private SpiderTaskMapper spiderTaskMapper;
    @Resource
    private SpiderCrawlPersistService spiderCrawlPersistService;

    @Value("${spider.http.connect-timeout-ms:45000}")
    private int spiderConnectTimeoutMs;
    @Value("${spider.http.read-timeout-ms:90000}")
    private int spiderReadTimeoutMs;
    @Value("${spider.task.skip-max-page-one:true}")
    private boolean spiderSkipMaxPageOne;

    private static final int SPIDER_LAST_RESULT_MAX = 480;

    @Override
    public void runTask(Long taskId) {
        SpiderTask task = spiderTaskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        String rawStart = task.getStartUrl();
        if (rawStart == null || rawStart.trim().isEmpty()) {
            throw new RuntimeException("开始URL不能为空");
        }
        String startUrl = normalizeDingdianMirrorHost(rawStart.trim());
        task.setTaskStatus(1);
        spiderTaskMapper.updateById(task);

        try {
            String html = getHtml(startUrl);
            Novel novel = parseNovel(startUrl, html, task.getSourceSite());
            spiderCrawlPersistService.saveOrMergeNovel(novel);

            List<NovelChapter> chapterList = parseChapterList(novel.getId(), startUrl, html, task);
            if (chapterList.isEmpty()) {
                throw new RuntimeException("未抓到章节，请检查目录选择器或站点结构");
            }
            Set<Integer> alreadyDone = spiderCrawlPersistService.chapterNosWithFullContent(novel.getId());
            List<NovelChapter> pending = new ArrayList<>();
            for (NovelChapter ch : chapterList) {
                if (alreadyDone.contains(ch.getChapterNo())) {
                    continue;
                }
                pending.add(ch);
            }
            int dirCount = chapterList.size();
            int dirSkip = dirCount - pending.size();
            pending.sort(Comparator.comparing(NovelChapter::getChapterNo, Comparator.nullsLast(Integer::compareTo)));
            Integer maxPage = task.getMaxPage();
            if (spiderSkipMaxPageOne && maxPage != null && maxPage == 1) {
                maxPage = 0;
            }
            if (maxPage != null && maxPage > 0 && pending.size() > maxPage) {
                pending = new ArrayList<>(pending.subList(0, maxPage));
            }
            int ok = 0;
            int fail = 0;
            StringBuilder errBuf = new StringBuilder();
            if (pending.isEmpty()) {
                task.setLastRunTime(new Date());
                task.setLastResult(truncateSpiderLastResult("目录" + dirCount + "章，其中" + dirSkip + "章已有正文，无需抓取"));
                task.setTaskStatus(0);
                spiderTaskMapper.updateById(task);
                return;
            }
            for (NovelChapter chapter : pending) {
                try {
                    String chapterHtml = getHtmlForChapter(chapter.getChapterUrl(), task);
                    String content = parseChapterContent(chapterHtml, task);
                    if (isBlankChapterText(content)) {
                        throw new RuntimeException("正文解析为空");
                    }
                    spiderCrawlPersistService.saveChapterAndContent(novel.getId(), chapter, content);
                    ok++;
                } catch (Exception ex) {
                    fail++;
                    if (errBuf.length() < 400) {
                        errBuf.append("第").append(chapter.getChapterNo()).append("章:")
                                .append(ex.getClass().getSimpleName()).append(" ")
                                .append(ex.getMessage()).append("; ");
                    }
                }
            }

            task.setLastRunTime(new Date());
            task.setLastResult(truncateSpiderLastResult("目录" + dirCount + "章，跳过已有正文" + dirSkip + "章，本轮处理" + pending.size()
                    + "章，成功" + ok + "失败" + fail + "。" + (errBuf.length() > 0 ? errBuf.toString() : "")));
            task.setTaskStatus(ok > 0 ? 0 : 2);
            spiderTaskMapper.updateById(task);
        } catch (Exception e) {
            task.setTaskStatus(2);
            task.setLastRunTime(new Date());
            task.setLastResult(truncateSpiderLastResult("抓取失败: " + formatEx(e)));
            spiderTaskMapper.updateById(task);
        }
    }

    private String normalizeDingdianMirrorHost(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("(?i)http(s?)://www\\.dingdiann\\.cc", "http$1://www.diandingnnn.cc");
    }

    private String truncateSpiderLastResult(String s) {
        if (s == null) {
            return null;
        }
        if (s.length() <= SPIDER_LAST_RESULT_MAX) {
            return s;
        }
        return s.substring(0, SPIDER_LAST_RESULT_MAX) + "...";
    }

    private static String formatEx(Throwable e) {
        if (e == null) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder();
        Throwable t = e;
        for (int i = 0; i < 5 && t != null; i++) {
            sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
            t = t.getCause();
            if (t != null) {
                sb.append(" | ");
            }
        }
        return sb.toString();
    }

    private Novel parseNovel(String startUrl, String html, String sourceSite) {
        Document doc = Jsoup.parse(html);
        Novel novel = new Novel();
        novel.setSourceSite(sourceSite == null ? "dingdiann" : sourceSite);
        novel.setSourceUrl(startUrl);
        novel.setSourceBookId(parseBookId(startUrl));
        String name = doc.selectFirst("#info h1") != null ? doc.selectFirst("#info h1").text() : "未知小说";
        String intro = doc.selectFirst("#intro") != null ? doc.selectFirst("#intro").text() : "";
        String author = "未知作者";
        Elements ps = doc.select("#info p");
        for (Element p : ps) {
            String text = p.text();
            if (text.contains("作") && text.contains("者")) {
                int idx = text.indexOf("：");
                if (idx < 0) {
                    idx = text.indexOf(":");
                }
                if (idx >= 0 && idx + 1 < text.length()) {
                    author = text.substring(idx + 1).trim();
                } else {
                    author = text.replace("作者", "").trim();
                }
                break;
            }
        }
        novel.setName(name);
        novel.setAuthor(author);
        novel.setIntro(intro);
        novel.setIsEnable(1);
        novel.setIsDeleted(0);
        novel.setLatestChapterNo(0);
        return novel;
    }

    private List<NovelChapter> parseChapterList(Long novelId, String startUrl, String html, SpiderTask task) {
        Document doc = Jsoup.parse(html, startUrl);
        String bookId = parseBookId(startUrl);
        if (bookId == null || bookId.isEmpty()) {
            bookId = parseBookIdFromLinks(doc);
        }
        Elements links = new Elements();
        if (task != null && task.getChapterListSelector() != null && !task.getChapterListSelector().trim().isEmpty()) {
            links = doc.select(task.getChapterListSelector().trim());
        }
        if (links.isEmpty()) {
            links = selectMainVolumeChapterAnchors(doc);
        }
        if (links.isEmpty()) {
            links = doc.select("#list dd a");
        }
        if (links.isEmpty()) {
            links = doc.select(".listmain dd a");
        }
        if (links.isEmpty()) {
            links = doc.select("div.listmain dl dd a");
        }
        if (links.isEmpty()) {
            links = doc.select("dd a");
        }
        if (links.isEmpty()) {
            links = doc.select("a[href]");
        }
        Map<String, String> urlTitleMap = new LinkedHashMap<>();
        for (Element link : links) {
            String title = link.text().trim();
            String fullUrl = link.absUrl("href");
            if (title.isEmpty() || fullUrl.isEmpty()) {
                continue;
            }
            if (!isBookChapterUrl(fullUrl, bookId)) {
                continue;
            }
            if (!isLikelyChapterTitle(title)) {
                continue;
            }
            urlTitleMap.putIfAbsent(fullUrl, title);
        }
        List<NovelChapter> list = new ArrayList<>();
        int no = 1;
        for (Map.Entry<String, String> e : urlTitleMap.entrySet()) {
            NovelChapter chapter = new NovelChapter();
            chapter.setNovelId(novelId);
            chapter.setChapterNo(no++);
            chapter.setChapterTitle(e.getValue());
            chapter.setChapterUrl(e.getKey());
            chapter.setIsEnable(1);
            chapter.setIsVip(0);
            list.add(chapter);
        }
        if (list.isEmpty()) {
            list = parseChapterByRegex(novelId, startUrl, html, bookId);
        }
        return list;
    }

    private Elements selectMainVolumeChapterAnchors(Document doc) {
        String[] roots = {"div.listmain > dl", ".listmain dl", "#list"};
        for (String rootSel : roots) {
            for (Element dl : doc.select(rootSel)) {
                Elements collected = new Elements();
                boolean afterBody = false;
                for (Element node : dl.children()) {
                    String tn = node.normalName();
                    if ("dt".equals(tn)) {
                        String tx = node.text();
                        if (tx != null && tx.contains("正文卷")) {
                            afterBody = true;
                        } else if (afterBody) {
                            break;
                        }
                    } else if (afterBody && "dd".equals(tn)) {
                        collected.addAll(node.select("a[href]"));
                    }
                }
                if (!collected.isEmpty()) {
                    return collected;
                }
            }
        }
        return new Elements();
    }

    private boolean isLikelyChapterTitle(String title) {
        if (title == null) {
            return false;
        }
        String t = title.trim();
        if (t.isEmpty() || t.length() > 120) {
            return false;
        }
        if (t.contains("://") || t.contains("www.") || t.matches("(?i).+\\.(com|cc|net)/?.*")) {
            return false;
        }
        if (t.contains("朵朵") && t.contains("打开")) {
            return false;
        }
        if (t.contains("请记住") || t.contains("秒记住") || t.contains("顶点小说")) {
            return false;
        }
        if (t.contains("上架感言") || t.contains("请假") && t.length() < 12) {
            return false;
        }
        if (t.matches("(?i).*(作者的话|求月票|求推荐票|封推说明|重要通知).*") && t.length() < 20) {
            return false;
        }
        return true;
    }

    private boolean isBlankChapterText(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String toMobileChapterUrl(String url) {
        if (url == null) {
            return null;
        }
        String u = url.trim();
        if (u.isEmpty() || Pattern.compile("://m\\.", Pattern.CASE_INSENSITIVE).matcher(u).find()) {
            return null;
        }
        Matcher m = Pattern.compile("^(https?)://(?:www\\.)?([^/?#]+)(.*)$", Pattern.CASE_INSENSITIVE).matcher(u);
        if (!m.find()) {
            return null;
        }
        String host = m.group(2);
        if (host.toLowerCase(Locale.ROOT).startsWith("m.")) {
            return null;
        }
        return m.group(1).toLowerCase(Locale.ROOT) + "://m." + host + m.group(3);
    }

    private String parseChapterContent(String html, SpiderTask task) {
        Document doc = Jsoup.parse(html);
        if (task != null && task.getChapterContentSelector() != null && !task.getChapterContentSelector().trim().isEmpty()) {
            Element el = doc.selectFirst(task.getChapterContentSelector().trim());
            String t = extractPlainTextFromContentElement(el);
            if (!isBlankChapterText(t)) {
                return t;
            }
        }
        String[] selectors = {
                "#content.showtxt",
                "div#content.showtxt",
                "div.book.reader #content.showtxt",
                "div.book.reader #content",
                "div.reader #content",
                "#content",
                "div.showtxt",
                "#nr1",
                "#nr",
                "div.nr_page",
                "div.nr",
                "#novelcontent",
                "div.txt"
        };
        for (String sel : selectors) {
            Element contentEl = doc.selectFirst(sel);
            String t = extractPlainTextFromContentElement(contentEl);
            if (!isBlankChapterText(t)) {
                return t;
            }
        }
        Element readerBox = doc.selectFirst("div.book.reader div.content");
        if (readerBox != null) {
            Element bc = readerBox.clone();
            bc.select("h1,.link,.page_chapter,.share,script,ins,iframe").remove();
            String t = extractPlainTextFromContentElement(bc);
            if (!isBlankChapterText(t)) {
                return t;
            }
        }
        String longest = longestExtractBySelector(doc, "#content");
        if (!isBlankChapterText(longest)) {
            return longest;
        }
        longest = longestExtractBySelector(doc, "div.showtxt");
        return isBlankChapterText(longest) ? "" : longest;
    }

    private String longestExtractBySelector(Document doc, String css) {
        String best = "";
        for (Element el : doc.select(css)) {
            String t = extractPlainTextFromContentElement(el);
            if (t.length() > best.length()) {
                best = t;
            }
        }
        return best.length() >= 80 ? best : "";
    }

    private String extractPlainTextFromContentElement(Element contentEl) {
        if (contentEl == null) {
            return "";
        }
        Document frag = Jsoup.parse(contentEl.outerHtml());
        Element box = frag.body().children().isEmpty() ? null : frag.body().child(0);
        if (box == null) {
            return "";
        }
        box.select("script,ins,.adsbygoogle,iframe").remove();
        box.select("div[align=center]").remove();
        for (Element a : box.select("a")) {
            a.unwrap();
        }
        String raw = box.html().replaceAll("(?i)<br\\s*/?>", "\n");
        String text = Jsoup.parse(raw).text();
        text = cleanChapterTail(text);
        return text.trim();
    }

    private String cleanChapterTail(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replace('\u00a0', ' ');
        t = t.replaceAll("\\(https?://[^)]+\\)", "");
        t = t.replaceAll("章节错误[^。]*。?", "");
        t = t.replaceAll("举报后请耐心等待[^。]*。?", "");
        t = t.replaceAll("[\\d一二两三四五六七八九十]+秒记住顶点小说[^\\n]*", "");
        t = t.replaceAll("手机版阅读网址[^\\n]*", "");
        t = t.replaceAll("新书幼苗[^\\n]*", "");
        t = t.replaceAll("求鼓励[^\\n]*", "");
        return t;
    }

    private String getHtml(String url) throws Exception {
        List<String> candidates = buildUrlCandidates(url);
        Exception last = null;
        for (String u : candidates) {
            try {
                return getHtmlOnce(u);
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IOException("已尝试域名: " + candidates + " | " + formatEx(last));
    }

    private List<String> buildChapterHtmlCandidates(String url) {
        LinkedHashSet<String> base = new LinkedHashSet<>();
        String u0 = url == null ? "" : url.trim();
        if (!u0.isEmpty() && Pattern.compile("/ddk\\d+", Pattern.CASE_INSENSITIVE).matcher(u0).find()) {
            String path = extractPathAndQuery(u0);
            String scheme = u0.toLowerCase(Locale.ROOT).startsWith("https") ? "https" : "http";
            for (String host : DINGDIAN_MIRROR_HOSTS) {
                base.add(scheme + "://" + host + path);
            }
            base.add(u0);
        } else if (!u0.isEmpty()) {
            base.add(u0);
        }
        LinkedHashSet<String> withMobile = new LinkedHashSet<>();
        for (String u : base) {
            withMobile.add(u);
            String m = toMobileChapterUrl(u);
            if (m != null) {
                withMobile.add(m);
            }
        }
        return new ArrayList<>(withMobile);
    }

    private String getHtmlForChapter(String url, SpiderTask task) throws Exception {
        List<String> candidates = buildChapterHtmlCandidates(url);
        Exception last = null;
        String bestHtml = null;
        int bestLen = 0;
        for (String u : candidates) {
            try {
                String html = getHtmlOnceForChapter(u, task);
                String text = parseChapterContent(html, task);
                int n = text == null ? 0 : text.length();
                if (n > bestLen) {
                    bestLen = n;
                    bestHtml = html;
                }
            } catch (Exception e) {
                last = e;
            }
        }
        if (bestHtml != null) {
            return bestHtml;
        }
        throw new IOException("章节页均请求失败: " + candidates + " | " + formatEx(last));
    }

    private List<String> buildUrlCandidates(String url) {
        List<String> list = new ArrayList<>();
        if (url != null) {
            String t = url.trim();
            if (!t.isEmpty()) {
                list.add(t);
            }
        }
        if (url != null && Pattern.compile("/ddk\\d+", Pattern.CASE_INSENSITIVE).matcher(url).find()) {
            String path = extractPathAndQuery(url);
            String scheme = url.toLowerCase(Locale.ROOT).startsWith("https") ? "https" : "http";
            for (String host : DINGDIAN_MIRROR_HOSTS) {
                String nu = scheme + "://" + host + path;
                if (!list.contains(nu)) {
                    list.add(nu);
                }
            }
        }
        return list;
    }

    private String extractPathAndQuery(String url) {
        if (url == null) {
            return "/";
        }
        Matcher m = Pattern.compile("^https?://[^/]+([^?#]*)(\\?[^#]*)?", Pattern.CASE_INSENSITIVE).matcher(url.trim());
        if (!m.find()) {
            return "/";
        }
        String path = m.group(1);
        String q = m.group(2) != null ? m.group(2) : "";
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        return path + q;
    }

    private void applySpiderHttpSettings(HttpURLConnection conn) throws IOException {
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(spiderConnectTimeoutMs);
        conn.setReadTimeout(spiderReadTimeoutMs);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
    }

    private String getHtmlOnce(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        applySpiderHttpSettings(conn);
        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        if (in == null) {
            conn.disconnect();
            throw new IOException("HTTP " + code + " 无响应体: " + url);
        }
        byte[] bytes = readAllBytes(in);
        String header = conn.getContentType();
        conn.disconnect();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + ": " + url);
        }
        return pickBestDecodedIndexHtml(bytes, header);
    }

    private String getHtmlOnceForChapter(String url, SpiderTask task) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        applySpiderHttpSettings(conn);
        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        if (in == null) {
            conn.disconnect();
            throw new IOException("HTTP " + code + " 无响应体: " + url);
        }
        byte[] bytes = readAllBytes(in);
        String header = conn.getContentType();
        conn.disconnect();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + ": " + url);
        }
        return pickBestDecodedChapterHtml(bytes, header, task);
    }

    private int cjkDecodeQualityScore(String text) {
        if (text == null) {
            return 0;
        }
        int cjk = 0;
        int bad = 0;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\uFFFD') {
                bad++;
            }
            if (c >= 0x4E00 && c <= 0x9FFF) {
                cjk++;
            } else if (c >= 0x3400 && c <= 0x4DBF) {
                cjk++;
            }
        }
        if (bad > 30) {
            return Integer.MIN_VALUE / 4;
        }
        return cjk * 2000 + Math.min(len, 500000) - bad * 8000;
    }

    private void fillCharsetCandidates(LinkedHashSet<String> out, byte[] bytes, String contentType) {
        String hdrCs = extractCharset(contentType);
        if (hdrCs != null && !hdrCs.trim().isEmpty()) {
            out.add(normalizeCharsetName(hdrCs.trim()));
        }
        addCharsetFromMetaPeek(out, bytes, StandardCharsets.UTF_8);
        addCharsetFromMetaPeek(out, bytes, Charset.forName("GBK"));
        for (String n : CHAPTER_TRY_CHARSETS) {
            out.add(n);
        }
    }

    private String pickBestDecodedIndexHtml(byte[] bytes, String contentType) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        fillCharsetCandidates(names, bytes, contentType);
        String bestHtml = null;
        int bestScore = Integer.MIN_VALUE;
        for (String name : names) {
            Charset cs;
            try {
                cs = Charset.forName(name);
            } catch (Exception e) {
                continue;
            }
            String html = new String(bytes, cs);
            int score = cjkDecodeQualityScore(Jsoup.parse(html).text());
            if (score > bestScore) {
                bestScore = score;
                bestHtml = html;
            }
        }
        return bestHtml != null ? bestHtml : new String(bytes, Charset.forName("GBK"));
    }

    private String pickBestDecodedChapterHtml(byte[] bytes, String contentType, SpiderTask task) {
        LinkedHashSet<String> charsetTryOrder = new LinkedHashSet<>();
        fillCharsetCandidates(charsetTryOrder, bytes, contentType);
        String bestHtml = null;
        int bestScore = Integer.MIN_VALUE;
        for (String name : charsetTryOrder) {
            Charset cs;
            try {
                cs = Charset.forName(name);
            } catch (Exception e) {
                continue;
            }
            String html;
            try {
                html = new String(bytes, cs);
            } catch (Exception e) {
                continue;
            }
            int score = cjkDecodeQualityScore(parseChapterContent(html, task));
            if (score > bestScore) {
                bestScore = score;
                bestHtml = html;
            }
        }
        if (bestHtml != null) {
            return bestHtml;
        }
        return new String(bytes, Charset.forName("GBK"));
    }

    private void addCharsetFromMetaPeek(Set<String> out, byte[] bytes, Charset peekCharset) {
        try {
            String peek = new String(bytes, peekCharset);
            String m = extractCharsetFromMeta(peek);
            if (m != null && !m.trim().isEmpty()) {
                out.add(normalizeCharsetName(m.trim()));
            }
        } catch (Exception ignored) {
        }
    }

    private String normalizeCharsetName(String raw) {
        String s = raw.trim();
        if (s.length() >= 5 && s.regionMatches(true, 0, "utf-8", 0, 5)) {
            return "UTF-8";
        }
        if (s.equalsIgnoreCase("utf8")) {
            return "UTF-8";
        }
        return s;
    }

    private byte[] readAllBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, n);
        }
        inputStream.close();
        return output.toByteArray();
    }

    private String extractCharset(String contentType) {
        if (contentType == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("charset\\s*=\\s*([\\w-]+)", Pattern.CASE_INSENSITIVE).matcher(contentType);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String extractCharsetFromMeta(String html) {
        Matcher matcher = Pattern.compile("<meta[^>]*charset=['\"]?([\\w-]+)['\"]?", Pattern.CASE_INSENSITIVE).matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        Matcher matcher2 = Pattern.compile("content=['\"][^'\"]*charset=([\\w-]+)[^'\"]*['\"]", Pattern.CASE_INSENSITIVE).matcher(html);
        if (matcher2.find()) {
            return matcher2.group(1);
        }
        return null;
    }

    private String parseBookId(String url) {
        if (url == null) {
            return null;
        }
        String u = url.split("\\?")[0].split("#")[0];
        Matcher matcher = Pattern.compile("/(ddk\\d+)/", Pattern.CASE_INSENSITIVE).matcher(u);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private String parseBookIdFromLinks(Document doc) {
        Elements sample = doc.select(".listmain dd a[href], #list dd a[href], dd a[href]");
        for (Element a : sample) {
            String href = a.absUrl("href");
            String id = parseBookId(href);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private boolean isBookChapterUrl(String url, String bookId) {
        String lower = url.toLowerCase(Locale.ROOT);
        int hashIdx = lower.indexOf('#');
        if (hashIdx >= 0) {
            lower = lower.substring(0, hashIdx);
        }
        if (bookId != null && !bookId.isEmpty()) {
            String bid = Pattern.quote(bookId.toLowerCase(Locale.ROOT));
            return Pattern.compile(".*/" + bid + "/\\d+\\.html(?:\\?.*)?$", Pattern.CASE_INSENSITIVE).matcher(lower).find();
        }
        return Pattern.compile(".*/ddk\\d+/\\d+\\.html(?:\\?.*)?$", Pattern.CASE_INSENSITIVE).matcher(lower).find();
    }

    private List<NovelChapter> parseChapterByRegex(Long novelId, String startUrl, String html, String bookId) {
        int z = html.indexOf("正文卷");
        if (z >= 0) {
            html = html.substring(z);
        }
        Pattern pattern;
        if (bookId != null && !bookId.isEmpty()) {
            pattern = Pattern.compile("href\\s*=\\s*\"([^\"]*/" + Pattern.quote(bookId) + "/\\d+\\.html[^\"]*)\"[^>]*>(.*?)</a>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        } else {
            pattern = Pattern.compile("href\\s*=\\s*\"([^\"]*/ddk\\d+/\\d+\\.html[^\"]*)\"[^>]*>(.*?)</a>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        }
        Matcher matcher = pattern.matcher(html);
        List<NovelChapter> list = new ArrayList<>();
        int no = 1;
        while (matcher.find()) {
            String href = matcher.group(1);
            String title = Jsoup.parse(matcher.group(2)).text().trim();
            if (title.isEmpty() || !isLikelyChapterTitle(title)) {
                continue;
            }
            String fullUrl = href.startsWith("http")
                    ? href
                    : Jsoup.parse("<a href=\"" + href + "\"></a>", startUrl).selectFirst("a").absUrl("href");
            NovelChapter chapter = new NovelChapter();
            chapter.setNovelId(novelId);
            chapter.setChapterNo(no++);
            chapter.setChapterTitle(title);
            chapter.setChapterUrl(fullUrl);
            chapter.setIsEnable(1);
            chapter.setIsVip(0);
            list.add(chapter);
        }
        return list;
    }
}
