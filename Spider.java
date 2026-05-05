import org.htmlparser.Parser;
import org.htmlparser.filters.NodeClassFilter;
import org.htmlparser.tags.TitleTag;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * BFS spider integrated with indexer.
 *
 * Crawls pages starting from a given URL using breadth-first search,
 * indexes each page (body + title), and stores all data in DBManager.
 *
 * Indexing pipeline per page:
 *   raw tokens → lowercase + strip non-alpha → stop-word filter
 *              → Porter stem → track positions → write postings to DBManager
 *
 * Usage:
 *   DBManager dbm = new DBManager();
 *   new Spider("https://comp4321-hkust.github.io/testpages/testpage.htm", 30, dbm).crawl();
 *   dbm.close();
 */
public class Spider
{
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);

    private final String    startURL;
    private final int       maxPages;
    private final DBManager dbm;
    private final StopStem  stopStem;

    public Spider(String startURL, int maxPages, DBManager dbm)
    {
        this.startURL = startURL;
        this.maxPages = maxPages;
        this.dbm      = dbm;
        this.stopStem = new StopStem("stopwords.txt");
    }

    // ─── Main BFS loop ────────────────────────────────────────────────────────

    public void crawl() throws IOException
    {
        Queue<String> queue   = new LinkedList<>();
        Set<String>   visited = new HashSet<>();
        int           count   = 0;

        queue.add(startURL);

        while (!queue.isEmpty() && count < maxPages)
        {
            String url = queue.poll();

            if (visited.contains(url)) continue;
            visited.add(url);

            if (!shouldFetch(url)) continue;

            try
            {
                System.out.println("Crawling (" + (count + 1) + "/" + maxPages + "): " + url);

                URLConnection conn = openConnection(url);
                if (conn == null) continue;

                String lastModified = getLastModified(conn);
                int    size         = getPageSize(conn, url);
                String title        = extractTitle(url);
                int    pageID       = dbm.getOrCreatePageID(url);

                // Index body and title words
                Crawler        crawler   = new Crawler(url);
                Vector<String> bodyWords = crawler.extractWords();
                Vector<String> titleWords = tokenize(title);

                indexWords(bodyWords,  pageID, false);
                indexWords(titleWords, pageID, true);
                int maxTF = computeMaxTF(bodyWords);

                dbm.storePageMetadata(pageID,
                        new PageMetadata(title, url, lastModified, size, maxTF));

                // Extract links, store parent/child graph, enqueue unvisited
                Vector<String> links = crawler.extractLinks();
                for (String link : links)
                {
                    int childID = dbm.getOrCreatePageID(link);
                    dbm.addChildLink(pageID, childID);
                    dbm.addParentLink(childID, pageID);
                    if (!visited.contains(link))
                        queue.add(link);
                }

                dbm.commit();
                count++;
            }
            catch (ParserException e)
            {
                System.err.println("Parse error on " + url + ": " + e.getMessage());
            }
            catch (Exception e)
            {
                System.err.println("Error crawling " + url + ": " + e.getMessage());
            }
        }

        System.out.println("Done. Indexed " + count + " pages.");
    }

    // ─── Fetch decision ───────────────────────────────────────────────────────

    /**
     * Returns true if the page should be fetched:
     *   - URL has never been indexed, OR
     *   - Server's last-modified date is newer than what we stored
     */
    private boolean shouldFetch(String url) throws IOException
    {
        if (!dbm.isIndexed(url)) return true;

        try
        {
            URLConnection conn = openConnection(url);
            if (conn == null) return false;

            long serverMs = conn.getLastModified();
            if (serverMs == 0) return false;

            Integer pageID = dbm.getPageID(url);
            if (pageID == null) return true;

            PageMetadata meta = dbm.getPageMetadata(pageID);
            if (meta == null) return true;

            String serverDate = DATE_FMT.format(new Date(serverMs));
            return !serverDate.equals(meta.lastModified);
        }
        catch (Exception e)
        {
            return false;
        }
    }

    // ─── Page info helpers ────────────────────────────────────────────────────

    private URLConnection openConnection(String url)
    {
        try
        {
            URLConnection conn = new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.connect();
            return conn;
        }
        catch (Exception e)
        {
            System.err.println("Cannot connect to " + url + ": " + e.getMessage());
            return null;
        }
    }

    private String getLastModified(URLConnection conn)
    {
        long lm = conn.getLastModified();
        if (lm != 0) return DATE_FMT.format(new Date(lm));
        String date = conn.getHeaderField("Date");
        return date != null ? date : "Unknown";
    }

    private int getPageSize(URLConnection conn, String url)
    {
        int size = conn.getContentLength();
        if (size > 0) return size;

        try
        {
            org.htmlparser.beans.StringBean sb = new org.htmlparser.beans.StringBean();
            sb.setURL(url);
            String content = sb.getStrings();
            return content != null ? content.length() : 0;
        }
        catch (Exception e)
        {
            return 0;
        }
    }

    private String extractTitle(String url)
    {
        try
        {
            Parser   parser = new Parser(url);
            NodeList nodes  = parser.extractAllNodesThatMatch(
                    new NodeClassFilter(TitleTag.class));
            if (nodes.size() > 0)
                return ((TitleTag) nodes.elementAt(0)).getTitle().trim();
        }
        catch (ParserException e)
        {
            // fall through
        }
        return url;
    }

    // ─── Indexing ─────────────────────────────────────────────────────────────

    /**
     * Tokenizes a plain-text string into lowercase alphabetic tokens.
     */
    private Vector<String> tokenize(String text)
    {
        Vector<String> tokens = new Vector<>();
        StringTokenizer st = new StringTokenizer(text);
        while (st.hasMoreTokens())
        {
            String token = st.nextToken().toLowerCase().replaceAll("[^a-z]", "");
            if (!token.isEmpty()) tokens.add(token);
        }
        return tokens;
    }

    /**
     * Filters stop words, stems, tracks positions, and writes postings to DBManager.
     * Body words also update the forward index (used for top-keyword display).
     */
    private void indexWords(Vector<String> words, int pageID, boolean isTitle)
            throws IOException
    {
        Map<Integer, List<Integer>> wordPositions = new HashMap<>();

        for (int pos = 0; pos < words.size(); pos++)
        {
            String word = words.get(pos).toLowerCase().replaceAll("[^a-z]", "");
            if (word.isEmpty() || stopStem.isStopWord(word)) continue;

            String stem = stopStem.stem(word);
            if (stem.isEmpty()) continue;

            int wordID = dbm.getOrCreateWordID(stem);
            wordPositions.computeIfAbsent(wordID, k -> new ArrayList<>()).add(pos);
        }

        for (Map.Entry<Integer, List<Integer>> entry : wordPositions.entrySet())
        {
            int           wordID    = entry.getKey();
            List<Integer> positions = entry.getValue();
            int           tf        = positions.size();

            if (isTitle)
                dbm.addTitlePosting(wordID, pageID, tf, positions);
            else
                dbm.addBodyPosting(wordID, pageID, tf, positions);
        }

        if (!isTitle)
        {
            HashMap<Integer, Integer> wordTF = new HashMap<>();
            for (Map.Entry<Integer, List<Integer>> e : wordPositions.entrySet())
                wordTF.put(e.getKey(), e.getValue().size());
            dbm.storeForwardIndex(pageID, wordTF);
        }
    }

    /**
     * Returns the highest term frequency among all body stems.
     * Stored in PageMetadata for tf/max_tf normalization in the vector space model.
     */
    private int computeMaxTF(Vector<String> words)
    {
        Map<String, Integer> freq = new HashMap<>();
        for (String word : words)
        {
            String w = word.toLowerCase().replaceAll("[^a-z]", "");
            if (w.isEmpty() || stopStem.isStopWord(w)) continue;
            String stem = stopStem.stem(w);
            if (!stem.isEmpty())
                freq.merge(stem, 1, Integer::sum);
        }
        return freq.values().stream().mapToInt(Integer::intValue).max().orElse(1);
    }

    // ─── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception
    {
        String seedUrl = "https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm";
        int maxPages = 300;

        if (args.length >= 1 && !args[0].trim().isEmpty())
            seedUrl = args[0].trim();
        if (args.length >= 2)
            maxPages = Integer.parseInt(args[1]);

        DBManager dbm = new DBManager();
        Spider spider = new Spider(
            seedUrl,
            maxPages,
            dbm
        );
        spider.crawl();
        dbm.close();
        System.out.println("Spider finished.");
    }
}
