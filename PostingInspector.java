import java.io.IOException;
import java.util.List;

/**
 * Posting list inspector for TA2 demo.
 *
 * Usage:
 *   java -cp .:htmlparser.jar:lib/jdbm-1.0.jar PostingInspector run
 *   java -cp .:htmlparser.jar:lib/jdbm-1.0.jar PostingInspector running
 *   java -cp .:htmlparser.jar:lib/jdbm-1.0.jar PostingInspector the
 */
public class PostingInspector
{
    public static void main(String[] args) throws Exception
    {
        if (args.length < 1)
        {
            System.out.println("Usage: java PostingInspector <term>");
            return;
        }

        String rawTerm = args[0].toLowerCase();
        StopStem stopStem = new StopStem("stopwords.txt");
        String stem = stopStem.stem(rawTerm);

        System.out.println("=== Posting Inspector ===");
        System.out.println("raw term : " + rawTerm);
        System.out.println("stem     : " + stem);
        if (stem == null || stem.isEmpty())
        {
            System.out.println("Result: term removed by stop-word filter or invalid token.");
            return;
        }

        DBManager dbm = new DBManager();
        try
        {
            Integer wordID = dbm.getWordID(stem);
            if (wordID == null)
            {
                System.out.println("Result: stem not found in index.");
                return;
            }

            List<Posting> bodyPostings = dbm.getBodyPostings(wordID);
            List<Posting> titlePostings = dbm.getTitlePostings(wordID);

            System.out.println("wordID   : " + wordID);
            System.out.println("body df  : " + bodyPostings.size());
            System.out.println("title df : " + titlePostings.size());
            System.out.println();

            System.out.println("[Body postings: pageID | tf | url]");
            for (Posting p : bodyPostings)
            {
                System.out.println(
                    p.pageID + " | " + p.tf + " | " + safeURL(dbm, p.pageID)
                );
            }

            System.out.println();
            System.out.println("[Title postings: pageID | tf | url]");
            for (Posting p : titlePostings)
            {
                System.out.println(
                    p.pageID + " | " + p.tf + " | " + safeURL(dbm, p.pageID)
                );
            }
        }
        finally
        {
            dbm.close();
        }
    }

    private static String safeURL(DBManager dbm, int pageID) throws IOException
    {
        String url = dbm.getURL(pageID);
        return url == null ? "(unknown-url)" : url;
    }
}
