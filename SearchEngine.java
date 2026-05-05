import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * Vector-space retrieval engine over the JDBM indexes.
 */
public class SearchEngine
{
    private static final int MAX_RESULTS = 50;
    private static final double TITLE_TERM_BOOST = 0.60;
    private static final double TITLE_PHRASE_BOOST = 1.50;
    private static final double BODY_PHRASE_BOOST = 0.75;

    private final DBManager dbm;
    private final StopStem stopStem;
    private final QueryParser parser;
    private final boolean enableTitleBoost;

    public static class SearchResult
    {
        public int pageID;
        public double score;
        public PageMetadata metadata;
        public List<String> topKeywords = new ArrayList<>();
        public List<String> parentUrls = new ArrayList<>();
        public List<String> childUrls = new ArrayList<>();
    }

    public SearchEngine(DBManager dbm)
    {
        this(dbm, true);
    }

    public SearchEngine(DBManager dbm, boolean enableTitleBoost)
    {
        this.dbm = dbm;
        this.stopStem = new StopStem("stopwords.txt");
        this.parser = new QueryParser();
        this.enableTitleBoost = enableTitleBoost;
    }

    public List<SearchResult> search(String rawQuery) throws IOException
    {
        QueryParser.ParsedQuery parsed = parser.parse(rawQuery);

        List<String> includeTerms = normalizeTerms(parsed.includeTerms);
        List<List<String>> includePhrases = normalizePhrases(parsed.includePhrases);
        List<String> excludeTerms = normalizeTerms(parsed.excludeTerms);
        List<List<String>> excludePhrases = normalizePhrases(parsed.excludePhrases);

        if (includeTerms.isEmpty() && includePhrases.isEmpty())
            return new ArrayList<>();

        int totalDocs = getIndexedDocCount();
        if (totalDocs == 0) return new ArrayList<>();

        Map<Integer, Double> queryWeights = buildQueryWeights(includeTerms, totalDocs);
        Set<Integer> candidates = buildCandidates(includeTerms, includePhrases);
        applyExclusions(candidates, excludeTerms, excludePhrases);

        double queryNorm = 0.0;
        for (double wq : queryWeights.values()) queryNorm += wq * wq;
        queryNorm = Math.sqrt(queryNorm);
        if (queryNorm == 0.0) queryNorm = 1.0;

        List<SearchResult> results = new ArrayList<>();
        for (int pageID : candidates)
        {
            PageMetadata meta = dbm.getPageMetadata(pageID);
            if (meta == null) continue;

            HashMap<Integer, Integer> docTF = dbm.getForwardIndex(pageID);
            if (docTF.isEmpty()) continue;

            double dot = 0.0;
            double docNorm = 0.0;

            // Doc norm over all indexed body terms in this document
            for (Map.Entry<Integer, Integer> entry : docTF.entrySet())
            {
                int wordID = entry.getKey();
                int tf = entry.getValue();
                if (meta.maxTF <= 0) continue;
                int df = Math.max(1, dbm.getBodyDF(wordID));
                double idf = Math.log((double) totalDocs / (double) df);
                double wd = ((double) tf / (double) meta.maxTF) * idf;
                docNorm += wd * wd;
            }
            docNorm = Math.sqrt(docNorm);
            if (docNorm == 0.0) continue;

            // Dot product on query terms
            for (Map.Entry<Integer, Double> qEntry : queryWeights.entrySet())
            {
                int wordID = qEntry.getKey();
                double wq = qEntry.getValue();
                int tf = docTF.getOrDefault(wordID, 0);
                if (tf == 0 || meta.maxTF <= 0) continue;
                int df = Math.max(1, dbm.getBodyDF(wordID));
                double idf = Math.log((double) totalDocs / (double) df);
                double wd = ((double) tf / (double) meta.maxTF) * idf;
                dot += wd * wq;
            }

            double score = dot / (docNorm * queryNorm);

            // Title term boost
            if (enableTitleBoost)
            {
                for (String term : includeTerms)
                {
                    Integer wordID = dbm.getWordID(term);
                    if (wordID == null) continue;
                    Posting titlePosting = findPosting(dbm.getTitlePostings(wordID), pageID);
                    if (titlePosting != null)
                    {
                        int dfTitle = Math.max(1, dbm.getTitleDF(wordID));
                        double idfTitle = Math.log((double) totalDocs / (double) dfTitle);
                        score += TITLE_TERM_BOOST * idfTitle * titlePosting.tf;
                    }
                }
            }

            // Phrase contributions
            for (List<String> phrase : includePhrases)
            {
                if (phraseMatchesPage(phrase, pageID, false))
                    score += BODY_PHRASE_BOOST;
                if (enableTitleBoost && phraseMatchesPage(phrase, pageID, true))
                    score += TITLE_PHRASE_BOOST;
            }

            if (score > 0.0)
            {
                SearchResult r = new SearchResult();
                r.pageID = pageID;
                r.score = score;
                r.metadata = meta;
                r.topKeywords = getTopKeywords(pageID, 5);
                r.parentUrls = resolveUrls(dbm.getParentIDs(pageID));
                r.childUrls = resolveUrls(dbm.getChildIDs(pageID));
                results.add(r);
            }
        }

        results.sort((a, b) -> {
            int cmp = Double.compare(b.score, a.score);
            if (cmp != 0) return cmp;
            return Integer.compare(a.pageID, b.pageID);
        });

        if (results.size() > MAX_RESULTS)
            return new ArrayList<>(results.subList(0, MAX_RESULTS));
        return results;
    }

    private int getIndexedDocCount() throws IOException
    {
        int count = 0;
        for (int pageID : dbm.getAllPageIDs())
            if (dbm.getPageMetadata(pageID) != null) count++;
        return count;
    }

    private Map<Integer, Double> buildQueryWeights(List<String> terms, int totalDocs) throws IOException
    {
        HashMap<Integer, Integer> qtfByWord = new HashMap<>();
        for (String term : terms)
        {
            Integer wordID = dbm.getWordID(term);
            if (wordID != null) qtfByWord.merge(wordID, 1, Integer::sum);
        }

        HashMap<Integer, Double> weights = new HashMap<>();
        for (Map.Entry<Integer, Integer> e : qtfByWord.entrySet())
        {
            int wordID = e.getKey();
            int qtf = e.getValue();
            int df = Math.max(1, dbm.getBodyDF(wordID));
            double idf = Math.log((double) totalDocs / (double) df);
            weights.put(wordID, qtf * idf);
        }
        return weights;
    }

    private Set<Integer> buildCandidates(List<String> terms, List<List<String>> phrases) throws IOException
    {
        Set<Integer> candidates = new HashSet<>();

        for (String term : terms)
        {
            Integer wordID = dbm.getWordID(term);
            if (wordID == null) continue;
            for (Posting p : dbm.getBodyPostings(wordID))
                candidates.add(p.pageID);
            for (Posting p : dbm.getTitlePostings(wordID))
                candidates.add(p.pageID);
        }

        for (List<String> phrase : phrases)
        {
            for (int pageID : dbm.getAllPageIDs())
            {
                if (phraseMatchesPage(phrase, pageID, false) || phraseMatchesPage(phrase, pageID, true))
                    candidates.add(pageID);
            }
        }

        return candidates;
    }

    private void applyExclusions(Set<Integer> candidates, List<String> excludeTerms, List<List<String>> excludePhrases)
            throws IOException
    {
        if (candidates.isEmpty()) return;
        Set<Integer> remove = new HashSet<>();

        for (int pageID : candidates)
        {
            boolean excluded = false;

            for (String term : excludeTerms)
            {
                Integer wordID = dbm.getWordID(term);
                if (wordID == null) continue;
                if (findPosting(dbm.getBodyPostings(wordID), pageID) != null ||
                    findPosting(dbm.getTitlePostings(wordID), pageID) != null)
                {
                    excluded = true;
                    break;
                }
            }

            if (!excluded)
            {
                for (List<String> phrase : excludePhrases)
                {
                    if (phraseMatchesPage(phrase, pageID, false) || phraseMatchesPage(phrase, pageID, true))
                    {
                        excluded = true;
                        break;
                    }
                }
            }

            if (excluded) remove.add(pageID);
        }

        candidates.removeAll(remove);
    }

    private Posting findPosting(Vector<Posting> postings, int pageID)
    {
        for (Posting p : postings)
            if (p.pageID == pageID) return p;
        return null;
    }

    private boolean phraseMatchesPage(List<String> phraseTerms, int pageID, boolean titleIndex) throws IOException
    {
        if (phraseTerms.isEmpty()) return false;

        List<List<Integer>> positionsByTerm = new ArrayList<>();
        for (String term : phraseTerms)
        {
            Integer wordID = dbm.getWordID(term);
            if (wordID == null) return false;
            Vector<Posting> postings = titleIndex ? dbm.getTitlePostings(wordID) : dbm.getBodyPostings(wordID);
            Posting p = findPosting(postings, pageID);
            if (p == null || p.positions == null || p.positions.isEmpty()) return false;
            positionsByTerm.add(new ArrayList<>(p.positions));
        }

        Collections.sort(positionsByTerm.get(0));
        HashSet<Integer> firstSet = new HashSet<>(positionsByTerm.get(0));
        for (int startPos : firstSet)
        {
            boolean ok = true;
            for (int i = 1; i < positionsByTerm.size(); i++)
            {
                if (!positionsByTerm.get(i).contains(startPos + i))
                {
                    ok = false;
                    break;
                }
            }
            if (ok) return true;
        }
        return false;
    }

    private List<String> normalizeTerms(List<String> rawTerms)
    {
        ArrayList<String> normalized = new ArrayList<>();
        for (String t : rawTerms)
        {
            String token = normalizeToken(t);
            if (!token.isEmpty()) normalized.add(token);
        }
        return normalized;
    }

    private List<List<String>> normalizePhrases(List<String> rawPhrases)
    {
        ArrayList<List<String>> out = new ArrayList<>();
        for (String phrase : rawPhrases)
        {
            String[] bits = phrase.split("\\s+");
            ArrayList<String> terms = new ArrayList<>();
            for (String bit : bits)
            {
                String token = normalizeToken(bit);
                if (!token.isEmpty()) terms.add(token);
            }
            if (!terms.isEmpty()) out.add(terms);
        }
        return out;
    }

    private String normalizeToken(String raw)
    {
        String token = raw.toLowerCase().replaceAll("[^a-z]", "");
        if (token.isEmpty()) return "";
        if (stopStem.isStopWord(token)) return "";
        String stem = stopStem.stem(token);
        return stem == null ? "" : stem;
    }

    private List<String> getTopKeywords(int pageID, int topN) throws IOException
    {
        HashMap<Integer, Integer> tf = dbm.getForwardIndex(pageID);
        ArrayList<Map.Entry<Integer, Integer>> entries = new ArrayList<>(tf.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        ArrayList<String> out = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : entries)
        {
            if (out.size() >= topN) break;
            String word = dbm.getWord(entry.getKey());
            if (word != null) out.add(word + " " + entry.getValue());
        }
        return out;
    }

    private List<String> resolveUrls(Vector<Integer> ids) throws IOException
    {
        ArrayList<String> urls = new ArrayList<>();
        for (int id : ids)
        {
            String url = dbm.getURL(id);
            if (url != null) urls.add(url);
        }
        return urls;
    }
}
