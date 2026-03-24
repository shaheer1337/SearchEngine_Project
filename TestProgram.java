import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Reads all indexed pages from DBManager and writes spider_result.txt.
 *
 * Output format per page:
 *   Page title
 *   URL
 *   Last modification date, size of page
 *   Keyword1 freq1; Keyword2 freq2; ... (up to 10, sorted by frequency descending)
 *   Child Link 1
 *   Child Link 2
 *   ...
 *   --------------------------------------------------
 */
public class TestProgram
{
    private static final String OUTPUT_FILE  = "spider_result.txt";
    private static final int    MAX_KEYWORDS = 10;
    private static final int    MAX_CHILDREN = 10;
    private static final String SEPARATOR    =
            "-------------------------------------------------------------------";

    public static void main(String[] args) throws IOException
    {
        DBManager dbm = new DBManager();

        List<Integer> pageIDs = dbm.getAllPageIDs();

        if (pageIDs.isEmpty())
        {
            System.out.println("No pages indexed. Run Spider first.");
            dbm.close();
            return;
        }

        // Sort by pageID for consistent output order
        Collections.sort(pageIDs);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_FILE)))
        {
            for (int pageID : pageIDs)
            {
                PageMetadata meta = dbm.getPageMetadata(pageID);
                if (meta == null) continue;

                // Title
                writer.write(meta.title);
                writer.newLine();

                // URL
                writer.write(meta.url);
                writer.newLine();

                // Last modification date, size
                writer.write(meta.lastModified + ", " + meta.size);
                writer.newLine();

                // Top keywords by frequency
                String keywords = getTopKeywords(dbm, pageID, MAX_KEYWORDS);
                writer.write(keywords);
                writer.newLine();

                // Child links (up to 10)
                Vector<Integer> childIDs = dbm.getChildIDs(pageID);
                int childCount = Math.min(childIDs.size(), MAX_CHILDREN);
                for (int i = 0; i < childCount; i++)
                {
                    String childURL = dbm.getURL(childIDs.get(i));
                    if (childURL != null)
                        writer.write(childURL);
                    writer.newLine();
                }

                writer.write(SEPARATOR);
                writer.newLine();
            }
        }

        dbm.close();
        System.out.println("spider_result.txt written with " + pageIDs.size() + " pages.");
    }

    /**
     * Builds the keyword line for a page.
     * Reads the forward index, sorts by tf descending, returns top N as:
     *   "word1 freq1; word2 freq2; ..."
     */
    private static String getTopKeywords(DBManager dbm, int pageID, int topN)
            throws IOException
    {
        HashMap<Integer, Integer> wordTF = dbm.getForwardIndex(pageID);

        if (wordTF == null || wordTF.isEmpty())
            return "";

        // Sort entries by tf descending
        List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(wordTF.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<Integer, Integer> entry : entries)
        {
            if (count >= topN) break;

            String word = dbm.getWord(entry.getKey());
            if (word == null) continue;

            if (count > 0) sb.append("; ");
            sb.append(word).append(" ").append(entry.getValue());
            count++;
        }

        return sb.toString();
    }
}
