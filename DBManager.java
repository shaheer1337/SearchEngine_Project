import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import jdbm.htree.HTree;
import jdbm.helper.FastIterator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

/**
 * Central database layer for the COMP4321 search engine.
 *
 * All persistent state is stored in a single JDBM RecordManager ("searchengine")
 * containing 11 named HTrees:
 *
 *  TABLE               KEY              VALUE
 *  ─────────────────────────────────────────────────────────────────────
 *  url_to_pageID       String url       Integer pageID
 *  pageID_to_url       Integer pageID   String url
 *  pageMetadata        Integer pageID   PageMetadata
 *  childLinks          Integer pageID   Vector<Integer> childPageIDs
 *  parentLinks         Integer pageID   Vector<Integer> parentPageIDs
 *  word_to_wordID      String stem      Integer wordID
 *  wordID_to_word      Integer wordID   String stem
 *  bodyIndex           Integer wordID   Vector<Posting>
 *  titleIndex          Integer wordID   Vector<Posting>
 *  forwardIndex        Integer pageID   HashMap<Integer,Integer> wordID→tf
 *  counters            String name      Integer value  (pageCounter, wordCounter)
 *  ─────────────────────────────────────────────────────────────────────
 */
public class DBManager
{
    private static final String DB_NAME = "searchengine";

    // Counter keys
    private static final String PAGE_COUNTER = "pageCounter";
    private static final String WORD_COUNTER  = "wordCounter";

    private RecordManager recman;

    private HTree urlToPageID;
    private HTree pageIDToUrl;
    private HTree pageMetadata;
    private HTree childLinks;
    private HTree parentLinks;
    private HTree wordToWordID;
    private HTree wordIDToWord;
    private HTree bodyIndex;
    private HTree titleIndex;
    private HTree forwardIndex;
    private HTree counters;

    // ─── Construction ────────────────────────────────────────────────────────

    public DBManager() throws IOException
    {
        recman = RecordManagerFactory.createRecordManager(DB_NAME);

        urlToPageID = loadOrCreate("url_to_pageID");
        pageIDToUrl = loadOrCreate("pageID_to_url");
        pageMetadata = loadOrCreate("pageMetadata");
        childLinks   = loadOrCreate("childLinks");
        parentLinks  = loadOrCreate("parentLinks");
        wordToWordID = loadOrCreate("word_to_wordID");
        wordIDToWord = loadOrCreate("wordID_to_word");
        bodyIndex    = loadOrCreate("bodyIndex");
        titleIndex   = loadOrCreate("titleIndex");
        forwardIndex = loadOrCreate("forwardIndex");
        counters     = loadOrCreate("counters");
    }

    private HTree loadOrCreate(String name) throws IOException
    {
        long recid = recman.getNamedObject(name);
        if (recid != 0)
            return HTree.load(recman, recid);

        HTree tree = HTree.createInstance(recman);
        recman.setNamedObject(name, tree.getRecid());
        return tree;
    }

    // ─── Page ID mapping ─────────────────────────────────────────────────────

    /**
     * Returns the existing pageID for the URL, or creates and stores a new one.
     */
    public int getOrCreatePageID(String url) throws IOException
    {
        Integer existing = (Integer) urlToPageID.get(url);
        if (existing != null)
            return existing;

        int newID = nextCounter(PAGE_COUNTER);
        urlToPageID.put(url, newID);
        pageIDToUrl.put(newID, url);
        return newID;
    }

    public boolean isIndexed(String url) throws IOException
    {
        Integer pageID = (Integer) urlToPageID.get(url);
        if (pageID == null) return false;
        return pageMetadata.get(pageID) != null;
    }

    public Integer getPageID(String url) throws IOException
    {
        return (Integer) urlToPageID.get(url);
    }

    public String getURL(int pageID) throws IOException
    {
        return (String) pageIDToUrl.get(pageID);
    }

    // ─── Page metadata ────────────────────────────────────────────────────────

    public void storePageMetadata(int pageID, PageMetadata meta) throws IOException
    {
        pageMetadata.put(pageID, meta);
    }

    public PageMetadata getPageMetadata(int pageID) throws IOException
    {
        return (PageMetadata) pageMetadata.get(pageID);
    }

    /** Returns all stored pageIDs. */
    @SuppressWarnings("unchecked")
    public List<Integer> getAllPageIDs() throws IOException
    {
        List<Integer> ids = new ArrayList<>();
        FastIterator iter = pageIDToUrl.keys();
        Integer key;
        while ((key = (Integer) iter.next()) != null)
            ids.add(key);
        return ids;
    }

    // ─── Link graph ───────────────────────────────────────────────────────────

    public void addChildLink(int parentID, int childID) throws IOException
    {
        Vector<Integer> children = getOrCreateVector(childLinks, parentID);
        if (!children.contains(childID))
        {
            children.add(childID);
            childLinks.put(parentID, children);
        }
    }

    public void addParentLink(int childID, int parentID) throws IOException
    {
        Vector<Integer> parents = getOrCreateVector(parentLinks, childID);
        if (!parents.contains(parentID))
        {
            parents.add(parentID);
            parentLinks.put(childID, parents);
        }
    }

    @SuppressWarnings("unchecked")
    public Vector<Integer> getChildIDs(int pageID) throws IOException
    {
        Vector<Integer> v = (Vector<Integer>) childLinks.get(pageID);
        return v != null ? v : new Vector<>();
    }

    @SuppressWarnings("unchecked")
    public Vector<Integer> getParentIDs(int pageID) throws IOException
    {
        Vector<Integer> v = (Vector<Integer>) parentLinks.get(pageID);
        return v != null ? v : new Vector<>();
    }

    // ─── Word ID mapping ──────────────────────────────────────────────────────

    /**
     * Returns the existing wordID for the stem, or creates and stores a new one.
     */
    public int getOrCreateWordID(String stem) throws IOException
    {
        Integer existing = (Integer) wordToWordID.get(stem);
        if (existing != null)
            return existing;

        int newID = nextCounter(WORD_COUNTER);
        wordToWordID.put(stem, newID);
        wordIDToWord.put(newID, stem);
        return newID;
    }

    public Integer getWordID(String stem) throws IOException
    {
        return (Integer) wordToWordID.get(stem);
    }

    public String getWord(int wordID) throws IOException
    {
        return (String) wordIDToWord.get(wordID);
    }

    /** Returns all stored wordIDs. */
    @SuppressWarnings("unchecked")
    public List<Integer> getAllWordIDs() throws IOException
    {
        List<Integer> ids = new ArrayList<>();
        FastIterator iter = wordIDToWord.keys();
        Integer key;
        while ((key = (Integer) iter.next()) != null)
            ids.add(key);
        return ids;
    }

    // ─── Inverted indexes ─────────────────────────────────────────────────────

    /**
     * Adds or updates a posting in the body inverted index.
     * If a posting for this (wordID, pageID) already exists it is replaced.
     */
    public void addBodyPosting(int wordID, int pageID, int tf, List<Integer> positions)
            throws IOException
    {
        addPosting(bodyIndex, wordID, pageID, tf, positions);
    }

    /**
     * Adds or updates a posting in the title inverted index.
     */
    public void addTitlePosting(int wordID, int pageID, int tf, List<Integer> positions)
            throws IOException
    {
        addPosting(titleIndex, wordID, pageID, tf, positions);
    }

    @SuppressWarnings("unchecked")
    public Vector<Posting> getBodyPostings(int wordID) throws IOException
    {
        Vector<Posting> v = (Vector<Posting>) bodyIndex.get(wordID);
        return v != null ? v : new Vector<>();
    }

    @SuppressWarnings("unchecked")
    public Vector<Posting> getTitlePostings(int wordID) throws IOException
    {
        Vector<Posting> v = (Vector<Posting>) titleIndex.get(wordID);
        return v != null ? v : new Vector<>();
    }

    /** Document frequency: number of pages that contain this word in the body. */
    public int getBodyDF(int wordID) throws IOException
    {
        return getBodyPostings(wordID).size();
    }

    /** Document frequency: number of pages that contain this word in the title. */
    public int getTitleDF(int wordID) throws IOException
    {
        return getTitlePostings(wordID).size();
    }

    // ─── Forward index ────────────────────────────────────────────────────────

    /**
     * Stores the (wordID → tf) map for a page.
     * Overwrites any existing entry for this pageID.
     */
    public void storeForwardIndex(int pageID, HashMap<Integer, Integer> wordTF)
            throws IOException
    {
        forwardIndex.put(pageID, wordTF);
    }

    @SuppressWarnings("unchecked")
    public HashMap<Integer, Integer> getForwardIndex(int pageID) throws IOException
    {
        HashMap<Integer, Integer> map = (HashMap<Integer, Integer>) forwardIndex.get(pageID);
        return map != null ? map : new HashMap<>();
    }

    // ─── Commit / close ───────────────────────────────────────────────────────

    public void commit() throws IOException
    {
        recman.commit();
    }

    public void close() throws IOException
    {
        recman.commit();
        recman.close();
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void addPosting(HTree index, int wordID, int pageID, int tf,
                            List<Integer> positions) throws IOException
    {
        Vector<Posting> postings = (Vector<Posting>) index.get(wordID);
        if (postings == null)
            postings = new Vector<>();

        // Replace existing posting for this page, if any
        for (int i = 0; i < postings.size(); i++)
        {
            if (postings.get(i).pageID == pageID)
            {
                postings.set(i, new Posting(pageID, tf, positions));
                index.put(wordID, postings);
                return;
            }
        }

        postings.add(new Posting(pageID, tf, positions));
        index.put(wordID, postings);
    }

    @SuppressWarnings("unchecked")
    private Vector<Integer> getOrCreateVector(HTree tree, int key) throws IOException
    {
        Vector<Integer> v = (Vector<Integer>) tree.get(key);
        return v != null ? v : new Vector<>();
    }

    private int nextCounter(String name) throws IOException
    {
        Integer current = (Integer) counters.get(name);
        int next = (current == null) ? 0 : current + 1;
        counters.put(name, next);
        return next;
    }
}
