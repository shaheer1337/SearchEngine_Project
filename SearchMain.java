import java.util.List;

/**
 * CLI entry point for retrieval testing.
 * Usage:
 *   java SearchMain "hong kong" universities -"science park"
 */
public class SearchMain
{
    public static void main(String[] args) throws Exception
    {
        if (args.length == 0)
        {
            System.out.println("Usage: java SearchMain [--no-title-boost] <query>");
            return;
        }

        boolean enableTitleBoost = true;
        int queryStart = 0;
        if ("--no-title-boost".equals(args[0]))
        {
            enableTitleBoost = false;
            queryStart = 1;
        }
        if (queryStart >= args.length)
        {
            System.out.println("Usage: java SearchMain [--no-title-boost] <query>");
            return;
        }

        StringBuilder queryBuilder = new StringBuilder();
        for (int i = queryStart; i < args.length; i++)
        {
            if (i > queryStart) queryBuilder.append(' ');
            queryBuilder.append(args[i]);
        }
        String query = queryBuilder.toString();

        DBManager dbm = new DBManager();
        SearchEngine engine = new SearchEngine(dbm, enableTitleBoost);
        List<SearchEngine.SearchResult> results = engine.search(query);

        System.out.println("Title boost: " + (enableTitleBoost ? "ON" : "OFF"));

        int rank = 1;
        for (SearchEngine.SearchResult r : results)
        {
            System.out.printf("%02d. score=%.6f  %s%n", rank++, r.score, r.metadata.title);
            System.out.println("    " + r.metadata.url);
        }
        if (results.isEmpty())
            System.out.println("No results.");
        dbm.close();
    }
}
