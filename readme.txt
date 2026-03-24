================================================================
COMP4321 Phase 1 — Spider & Test Program
================================================================

----------------------------------------------------------------
REQUIREMENTS
----------------------------------------------------------------
- Java JDK 8 or above
- htmlparser.jar       (included in project root)
- jdbm-1.0.jar         (place in lib/ folder inside project root)

----------------------------------------------------------------
FILE STRUCTURE
----------------------------------------------------------------
COMP4321 Project/
├── IRUtilities/
│   └── Porter.java          Porter stemmer (provided)
├── lib/
│   └── jdbm-1.0.jar         JDBM 1.0 library
├── htmlparser.jar            HTML parser library
├── stopwords.txt             English stop words list
├── Crawler.java              HTML word/link extractor
├── StopStem.java             Stop word filter + Porter stemmer wrapper
├── Posting.java              Serializable posting (pageID, tf, positions)
├── PageMetadata.java         Serializable page metadata
├── DBManager.java            JDBM database layer (all HTrees + API)
├── Spider.java               BFS spider integrated with indexer
└── TestProgram.java          Reads DB and writes spider_result.txt

----------------------------------------------------------------
BUILD INSTRUCTIONS
----------------------------------------------------------------
From the project root directory, run:

  javac -cp .:htmlparser.jar:lib/jdbm-1.0.jar \
      IRUtilities/Porter.java \
      StopStem.java \
      Posting.java \
      PageMetadata.java \
      DBManager.java \
      Crawler.java \
      Spider.java \
      TestProgram.java

On Windows, replace ':' with ';' in the classpath:

  javac -cp .;htmlparser.jar;lib/jdbm-1.0.jar ...

----------------------------------------------------------------
EXECUTION
----------------------------------------------------------------
Step 1 — Run the Spider (crawls and indexes 30 pages):

  java -cp .:htmlparser.jar:lib/jdbm-1.0.jar Spider

  Output: prints crawling progress to console.
  Creates: searchengine.db and searchengine.lg (the JDBM database files).

Step 2 — Run the Test Program (generates spider_result.txt):

  java -cp .:htmlparser.jar:lib/jdbm-1.0.jar TestProgram

  Output: spider_result.txt in the project root.

NOTE: To re-crawl from scratch, delete the database files first:
  rm -f searchengine.db searchengine.lg   (Linux/Mac)
  del searchengine.db searchengine.lg     (Windows)

----------------------------------------------------------------
JDBM DATABASE SCHEMA DESIGN
----------------------------------------------------------------
All data is stored in a single JDBM RecordManager file named
"searchengine" (files: searchengine.db, searchengine.lg).

The RecordManager contains 11 named HTree hash tables:

1. url_to_pageID
   Key:   String (URL)
   Value: Integer (pageID)
   Purpose: Converts a URL to its internal integer page ID.

2. pageID_to_url
   Key:   Integer (pageID)
   Value: String (URL)
   Purpose: Reverse lookup — converts a page ID back to its URL.

3. pageMetadata
   Key:   Integer (pageID)
   Value: PageMetadata object (title, url, lastModified, size, maxTF)
   Purpose: Stores all metadata for a crawled page.
            maxTF (max term frequency) is stored for tf/max_tf
            normalization in the vector space model (Final phase).

4. childLinks
   Key:   Integer (pageID)
   Value: Vector<Integer> (list of child page IDs)
   Purpose: Stores outgoing links from each page (parent→child graph).

5. parentLinks
   Key:   Integer (pageID)
   Value: Vector<Integer> (list of parent page IDs)
   Purpose: Stores incoming links to each page (child→parent graph).

6. word_to_wordID
   Key:   String (stemmed word)
   Value: Integer (wordID)
   Purpose: Converts a stemmed word to its internal integer word ID.

7. wordID_to_word
   Key:   Integer (wordID)
   Value: String (stemmed word)
   Purpose: Reverse lookup — converts a word ID back to its stem.

8. bodyIndex  [inverted index]
   Key:   Integer (wordID)
   Value: Vector<Posting> where each Posting holds:
            - pageID    (which document)
            - tf        (term frequency in that document)
            - positions (list of word positions for phrase search)
   Purpose: Body inverted index. Supports ranked retrieval and
            phrase search in page bodies.

9. titleIndex  [inverted index]
   Key:   Integer (wordID)
   Value: Vector<Posting> (same structure as bodyIndex)
   Purpose: Title inverted index. Kept separate to allow title
            boosting in scoring (Final phase).

10. forwardIndex
    Key:   Integer (pageID)
    Value: HashMap<Integer, Integer> (wordID → term frequency)
    Purpose: Forward index used to efficiently retrieve the top-N
             most frequent keywords per page for output display.

11. counters
    Key:   String ("pageCounter" or "wordCounter")
    Value: Integer
    Purpose: Auto-incrementing ID counters for page IDs and word IDs.

----------------------------------------------------------------
INDEXING PIPELINE
----------------------------------------------------------------
For each crawled page, the following steps are applied:

1. Extract visible text from page body using HTMLParser StringBean
2. Extract title using HTMLParser TitleTag filter
3. Tokenize: split on whitespace, lowercase, strip non-alpha chars
4. Filter stop words using stopwords.txt via StopStem
5. Apply Porter stemming via StopStem
6. Track word positions (0-based index in token stream)
7. Write postings to bodyIndex or titleIndex in DBManager
8. Write wordID→tf map to forwardIndex for top-keyword display
9. Compute maxTF (highest term frequency) and store in PageMetadata

----------------------------------------------------------------
CRAWLING STRATEGY
----------------------------------------------------------------
- Breadth-first search (BFS) using a Queue
- Cycle detection via a visited HashSet
- Before fetching, checks:
    (a) URL not yet indexed (no metadata stored) → fetch
    (b) URL indexed but server Last-Modified is newer → re-fetch
    (c) Otherwise → skip
- On fetch failure (timeout, parse error), the page is skipped
  and crawling continues with the next URL in the queue
================================================================
