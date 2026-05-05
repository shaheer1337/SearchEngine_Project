import java.util.ArrayList;
import java.util.List;

/**
 * Parses a query string into included/excluded terms and phrases.
 *
 * Supported syntax:
 * - simple term: hong
 * - quoted phrase: "hong kong"
 * - excluded term: -hong
 * - excluded phrase: -"hong kong"
 */
public class QueryParser
{
    public static class ParsedQuery
    {
        public final List<String> includeTerms = new ArrayList<>();
        public final List<String> includePhrases = new ArrayList<>();
        public final List<String> excludeTerms = new ArrayList<>();
        public final List<String> excludePhrases = new ArrayList<>();
    }

    public ParsedQuery parse(String rawQuery)
    {
        ParsedQuery parsed = new ParsedQuery();
        if (rawQuery == null) return parsed;

        int i = 0;
        int n = rawQuery.length();
        while (i < n)
        {
            while (i < n && Character.isWhitespace(rawQuery.charAt(i))) i++;
            if (i >= n) break;

            boolean excluded = false;
            if (rawQuery.charAt(i) == '-')
            {
                excluded = true;
                i++;
                while (i < n && Character.isWhitespace(rawQuery.charAt(i))) i++;
                if (i >= n) break;
            }

            if (rawQuery.charAt(i) == '"')
            {
                int start = ++i;
                while (i < n && rawQuery.charAt(i) != '"') i++;
                String phrase = rawQuery.substring(start, Math.min(i, n)).trim();
                if (!phrase.isEmpty())
                {
                    if (excluded) parsed.excludePhrases.add(phrase);
                    else parsed.includePhrases.add(phrase);
                }
                if (i < n && rawQuery.charAt(i) == '"') i++;
            }
            else
            {
                int start = i;
                while (i < n && !Character.isWhitespace(rawQuery.charAt(i))) i++;
                String term = rawQuery.substring(start, i).trim();
                if (!term.isEmpty())
                {
                    if (excluded) parsed.excludeTerms.add(term);
                    else parsed.includeTerms.add(term);
                }
            }
        }

        return parsed;
    }
}
