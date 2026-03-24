import java.io.IOException;
import java.util.*;

/**
 * Dumps all JDBM database entries for pages 0-9 (or a custom range).
 *
 * Usage:
 *   java DBViewer              — dump pages 0 to 9
 *   java DBViewer 0            — dump only page 0
 *   java DBViewer 0 5          — dump pages 0 to 5
 */
public class DBViewer
{
    public static void main(String[] args) throws IOException
    {
        DBManager dbm   = new DBManager();
        int       start = 0;
        int       end   = 9;

        if (args.length == 1) { start = end = Integer.parseInt(args[0]); }
        if (args.length == 2) { start = Integer.parseInt(args[0]); end = Integer.parseInt(args[1]); }

        // ── Global stats ──────────────────────────────────────────────
        List<Integer> allPages = dbm.getAllPageIDs();
        Collections.sort(allPages);
        List<Integer> allWords = dbm.getAllWordIDs();
        Collections.sort(allWords);

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("  GLOBAL STATS");
        System.out.println("  Total pages indexed : " + allPages.size());
        System.out.println("  Total unique stems  : " + allWords.size());
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // ── TABLE: counters ───────────────────────────────────────────
        System.out.println("  ┌─ counters ──────────────────────────────────────────────────┐");
        System.out.println("  │  pageCounter : " + allPages.size());
        System.out.println("  │  wordCounter : " + allWords.size());
        System.out.println("  └────────────────────────────────────────────────────────────┘");
        System.out.println();

        // ── TABLE: pageID_to_url / url_to_pageID ──────────────────────
        System.out.println("  ┌─ pageID_to_url  /  url_to_pageID ──────────────────────────┐");
        System.out.printf("  │  %-6s  %s%n", "pageID", "URL");
        System.out.println("  │  " + "-".repeat(70));
        for (int pid : allPages)
            System.out.printf("  │  %-6d  %s%n", pid, dbm.getURL(pid));
        System.out.println("  └────────────────────────────────────────────────────────────┘");
        System.out.println();

        // ── TABLE: wordID_to_word / word_to_wordID ────────────────────
        System.out.println("  ┌─ wordID_to_word  /  word_to_wordID (first 50 shown) ────────┐");
        System.out.printf("  │  %-6s  %s%n", "wordID", "stem");
        System.out.println("  │  " + "-".repeat(35));
        int wordShown = 0;
        for (int wid : allWords)
        {
            System.out.printf("  │  %-6d  %s%n", wid, dbm.getWord(wid));
            if (++wordShown >= 50) { System.out.println("  │  ... (" + (allWords.size() - 50) + " more)"); break; }
        }
        System.out.println("  └────────────────────────────────────────────────────────────┘");
        System.out.println();

        // Pre-build a wordID -> titlePostings map for fast lookup
        Map<Integer, Map<Integer, Posting>> titleByPage = new HashMap<>();
        for (int wid : dbm.getAllWordIDs())
        {
            for (Posting p : dbm.getTitlePostings(wid))
            {
                titleByPage
                    .computeIfAbsent(p.pageID, k -> new HashMap<>())
                    .put(wid, p);
            }
        }

        // ── Per-page dump ─────────────────────────────────────────────
        for (int pageID = start; pageID <= end; pageID++)
        {
            PageMetadata meta = dbm.getPageMetadata(pageID);
            if (meta == null)
            {
                System.out.println("[ pageID " + pageID + " — no metadata (not indexed) ]");
                System.out.println();
                continue;
            }

            System.out.println("################################################################");
            System.out.println("  PAGE ID : " + pageID);
            System.out.println("################################################################");

            // ── TABLE: pageMetadata ───────────────────────────────────
            System.out.println();
            System.out.println("  ┌─ pageMetadata ─────────────────────────────────────────┐");
            System.out.println("  │ title        : " + meta.title);
            System.out.println("  │ url          : " + meta.url);
            System.out.println("  │ lastModified : " + meta.lastModified);
            System.out.println("  │ size         : " + meta.size + " bytes");
            System.out.println("  │ maxTF        : " + meta.maxTF);
            System.out.println("  └────────────────────────────────────────────────────────┘");

            // ── TABLE: forwardIndex ───────────────────────────────────
            System.out.println();
            System.out.println("  ┌─ forwardIndex (body wordID → tf) ──────────────────────┐");
            HashMap<Integer, Integer> wordTF = dbm.getForwardIndex(pageID);
            List<Map.Entry<Integer, Integer>> fwdEntries = new ArrayList<>(wordTF.entrySet());
            fwdEntries.sort((a, b) -> b.getValue() - a.getValue());
            System.out.printf("  │  %-6s  %-22s  %-6s  %s%n", "wordID", "stem", "tf", "positions (from bodyIndex)");
            System.out.println("  │  " + "-".repeat(65));
            for (Map.Entry<Integer, Integer> e : fwdEntries)
            {
                int    wid      = e.getKey();
                int    tf       = e.getValue();
                String stem     = dbm.getWord(wid);
                List<Integer> positions = new ArrayList<>();
                for (Posting p : dbm.getBodyPostings(wid))
                    if (p.pageID == pageID) { positions = p.positions; break; }
                String posStr = positions.size() > 8
                        ? positions.subList(0, 8).toString().replace("]", ", ...]") : positions.toString();
                System.out.printf("  │  %-6d  %-22s  %-6d  %s%n", wid, stem, tf, posStr);
            }
            System.out.println("  └────────────────────────────────────────────────────────┘");

            // ── TABLE: titleIndex ─────────────────────────────────────
            System.out.println();
            System.out.println("  ┌─ titleIndex (wordID → Posting) ────────────────────────┐");
            Map<Integer, Posting> titlePostings = titleByPage.getOrDefault(pageID, new HashMap<>());
            if (titlePostings.isEmpty())
            {
                System.out.println("  │  (no title stems)");
            }
            else
            {
                System.out.printf("  │  %-6s  %-22s  %-6s  %s%n", "wordID", "stem", "tf", "positions");
                System.out.println("  │  " + "-".repeat(55));
                for (Map.Entry<Integer, Posting> e : titlePostings.entrySet())
                {
                    String stem = dbm.getWord(e.getKey());
                    Posting p   = e.getValue();
                    System.out.printf("  │  %-6d  %-22s  %-6d  %s%n",
                            e.getKey(), stem, p.tf, p.positions);
                }
            }
            System.out.println("  └────────────────────────────────────────────────────────┘");

            // ── TABLE: childLinks ─────────────────────────────────────
            System.out.println();
            System.out.println("  ┌─ childLinks ────────────────────────────────────────────┐");
            Vector<Integer> children = dbm.getChildIDs(pageID);
            if (children.isEmpty()) System.out.println("  │  (none)");
            for (int cid : children)
                System.out.printf("  │  [%3d]  %s%n", cid, dbm.getURL(cid));
            System.out.println("  └────────────────────────────────────────────────────────┘");

            // ── TABLE: parentLinks ────────────────────────────────────
            System.out.println();
            System.out.println("  ┌─ parentLinks ───────────────────────────────────────────┐");
            Vector<Integer> parents = dbm.getParentIDs(pageID);
            if (parents.isEmpty()) System.out.println("  │  (none)");
            for (int pid : parents)
                System.out.printf("  │  [%3d]  %s%n", pid, dbm.getURL(pid));
            System.out.println("  └────────────────────────────────────────────────────────┘");

            System.out.println();
        }

        dbm.close();
    }
}
