# COMP4321 Final Demo Checklist (2026)

Use this as the runbook on demo day. It is aligned with the TA pipeline:
**TA1 (Crawling) -> TA2 (Indexing/DB) -> TA3 (Retrieval/Ranking/UI)**.

## Demo Logistics

- Dates: **Fri May 8 (10:00-17:00)** and **Sat May 9 (09:00-13:00)**
- Format: in-person, 45 minutes per group
- TA flow: 3 stations, about 15 minutes each
- Seed URL for 2026: `https://hitcslj.github.io/TestPages/testpage.htm`

## Pre-Demo Setup (Before Leaving)

- [ ] Laptop charged + charger packed
- [ ] Project freshly cloned (same as submission)
- [ ] Code compiled successfully
- [ ] DB for **300 pages** already prepared and available
- [ ] `readme.txt` opened in terminal
- [ ] report PDF opened in browser/tab
- [ ] `spider_result.txt` opened in text editor
- [ ] Browser ready on your local search page
- [ ] One member designated as driver, one as explainer

## Cold-Start Rule

- [ ] As soon as previous group leaves TA1, start your services immediately
- [ ] No install/startup waiting during grading

## Quick Commands (Keep Ready)

```bash
# Compile
javac -cp .:htmlparser.jar:lib/jdbm-1.0.jar IRUtilities/Porter.java StopStem.java Posting.java PageMetadata.java DBManager.java Crawler.java Spider.java QueryParser.java SearchEngine.java SearchMain.java PostingInspector.java DBViewer.java TestProgram.java

# Build required 300-page DB before demo
java -cp .:htmlparser.jar:lib/jdbm-1.0.jar Spider "https://hitcslj.github.io/TestPages/testpage.htm" 300
java -cp .:htmlparser.jar:lib/jdbm-1.0.jar TestProgram

# Backup 300-page DB before TA1/TA2 hidden-seed tests (VERY IMPORTANT)
cp searchengine.db searchengine_300.db
cp searchengine.lg searchengine_300.lg

# TA1/TA2 hidden test rerun (N=30, empty DB) - TA gives seed:
rm -f searchengine.db searchengine.lg
java -cp .:htmlparser.jar:lib/jdbm-1.0.jar Spider "<TA_PROVIDED_SEED>" 30

# Immediate re-run to prove skip behavior
java -cp .:htmlparser.jar:lib/jdbm-1.0.jar Spider "<TA_PROVIDED_SEED>" 30

# After TA1/TA2, restore the 300-page DB for TA3 retrieval demo
mv -f searchengine_300.db searchengine.db
mv -f searchengine_300.lg searchengine.lg

# Posting inspector (TA2)
java -cp .:htmlparser.jar:lib/jdbm-1.0.jar PostingInspector run
java -cp .:htmlparser.jar:lib/jdbm-1.0.jar PostingInspector the
java -cp .:htmlparser.jar:lib/jdbm-1.0.jar PostingInspector running
java -cp .:htmlparser.jar:lib/jdbm-1.0.jar PostingInspector run
java -cp .:htmlparser.jar:lib/jdbm-1.0.jar DBViewer 0 2

# CLI retrieval spot checks
java -cp .:htmlparser.jar:lib/jdbm-1.0.jar SearchMain "computer science"
java -cp .:htmlparser.jar:lib/jdbm-1.0.jar SearchMain "\"hong kong\" universities"
java -cp .:htmlparser.jar:lib/jdbm-1.0.jar SearchMain "\"hong kong\" -\"science park\""
```

## Station 1 (TA1): Crawling Checklist

- [ ] Show `spider_result.txt`, especially page ID 0 section
- [ ] Keep 300-page DB safe by backing up before hidden-seed run
- [ ] Confirm required format per page:
  - title
  - URL
  - last-modified date
  - size
  - up to 10 keyword-frequency pairs
  - up to 10 child links
  - separator line
- [ ] Run hidden seed crawl with **N=30** from empty DB
- [ ] Finish hidden crawl under target time (preferably <30s, avoid >5 min)
- [ ] Re-run same crawl immediately; show unchanged pages are skipped
- [ ] Explain speed choices (BFS queue + visited set + skip unchanged pages by Last-Modified + single-pass parsing)

## Station 2 (TA2): Indexing & DB Checklist

- [ ] Show posting-list inspector capability (stem -> docs + tf):
  - `java -cp .:htmlparser.jar:lib/jdbm-1.0.jar PostingInspector run`
- [ ] Show stopword removal:
  - `java -cp .:htmlparser.jar:lib/jdbm-1.0.jar PostingInspector the`
  - `java -cp .:htmlparser.jar:lib/jdbm-1.0.jar PostingInspector of`
  - `java -cp .:htmlparser.jar:lib/jdbm-1.0.jar PostingInspector a`
- [ ] Show stemming parity:
  - `java -cp .:htmlparser.jar:lib/jdbm-1.0.jar PostingInspector running`
  - `java -cp .:htmlparser.jar:lib/jdbm-1.0.jar PostingInspector run`
- [ ] Show body and title are separate indexes
- [ ] Show phrase-search support evidence (positional postings)
- [ ] Do a fresh hidden seed re-index with N=30 (<1 min target)
- [ ] Walk through DB schema tables/HTrees
- [ ] DB tour command:
  - `java -cp .:htmlparser.jar:lib/jdbm-1.0.jar DBViewer 0 2`

## Station 3 (TA3): Retrieval / Ranking / UI Checklist

- [ ] Confirm `searchengine.db/.lg` are restored from `searchengine_300.*` backup
- [ ] Bring written **valid set**: at least 5 queries with expected top-5
- [ ] Include at least:
  - one single-keyword query
  - one multi-keyword query
  - one quoted phrase query
  - one zero-result query
  - one query showing title-boost impact
- [ ] Show top-K <= 50 and descending score
- [ ] Show stopword-only query and no-match query are handled safely
- [ ] Show query-side stemming (`books` vs `book`)
- [ ] Confirm every UI result shows:
  - score
  - clickable title
  - clickable URL
  - last modification date
  - page size
  - up to 5 stemmed keywords with frequencies
  - parent links
  - child links
- [ ] Explain ranking math clearly:
  - `(tf / max_tf) * idf`
  - cosine similarity
  - title-boost factor

## TA Q&A Answer Script (Use Verbatim if Needed)

### TA1 likely question: "What did you do to make crawler fast?"
- We use BFS with a queue and a visited set to avoid duplicate crawling work.
- We persist discovered URLs as IDs and skip recrawling unchanged pages by checking Last-Modified.
- We commit incrementally to DB and keep parsing lightweight (single-pass tokenization).
- For hidden-seed N=30, we run from empty DB and then immediately rerun to show skip behavior.
- We did not implement multithreading/connection pooling/HEAD-before-GET in this version;
  performance mainly comes from deduplication + skip logic, which is visible in rerun timing.

### TA2 likely question: "How is your DB schema organized?"
- We use one JDBM RecordManager with HTrees for:
  - URL<->pageID mapping
  - word<->wordID mapping
  - body inverted index
  - title inverted index
  - forward index (page -> word tf)
  - page metadata
  - parent/child link graph
  - counters
- Stopwords are removed before indexing.
- Porter stemming is applied to both documents and query terms.
- Phrase support is enabled by storing positional postings.

### TA2 likely question: "How do you prove phrase support?"
- Each posting stores term positions in body/title.
- Phrase match checks adjacency by verifying consecutive positions.
- We show positions directly using `DBViewer` and demonstrate quoted queries like `"hong kong"` in retrieval.

### TA3 likely question: "Walk me through your ranking math."
- For each query term:
  - document weight `wd = (tf / max_tf) * idf`
  - query weight `wq = qtf * idf`
  - `idf = log(N / df)`
- Base score is cosine similarity:
  - `score = dot(doc, query) / (||doc|| * ||query||)`
- Then we add boosts:
  - title term boost
  - title phrase boost
  - body phrase bonus
- Results are sorted descending by score and truncated to top 50.
- In our implementation, title boost is configurable at runtime (ON/OFF for demo).

### TA3 ranking math (expanded explanation script)
- **What each symbol means**
  - `tf`: how many times a term appears in one document.
  - `max_tf`: highest term frequency of any term in that document.
  - `df`: number of documents containing that term.
  - `N`: total number of indexed documents.
  - `idf = log(N/df)`: rare terms get higher weight; common terms get lower weight.
- **Why normalize by `max_tf`**
  - Raw `tf` unfairly favors long pages.
  - `tf/max_tf` scales term importance to `[0,1]` inside each page.
  - So a short relevant page is not automatically dominated by a very long page.
- **Document/query vectors**
  - For each query term `t`, document weight: `wd,t = (tf_d,t / max_tf_d) * idf_t`.
  - Query weight: `wq,t = qtf_t * idf_t` (if query word appears once, `qtf=1`).
  - We only compute over query terms (efficient candidate set from postings).
- **Cosine similarity intuition**
  - Dot product rewards matching weighted terms.
  - Divide by vector lengths to normalize magnitude:
    - `cos(d,q) = sum_t(wd,t * wq,t) / (||d|| * ||q||)`.
  - This measures alignment of term distribution, not just document length.
- **Mini numeric example (easy to say live)**
  - Suppose query: `"cse department"` with 2 terms.
  - Assume `idf(cse)=2.0`, `idf(department)=1.5`.
  - Document A has `tf(cse)=2`, `tf(department)=2`, `max_tf=2`.
    - `wd(cse)=1*2.0=2.0`, `wd(department)=1*1.5=1.5`.
  - Query weights (both appear once):
    - `wq(cse)=1*2.0=2.0`, `wq(department)=1*1.5=1.5`.
  - Dot product is high (`2.0*2.0 + 1.5*1.5 = 6.25`), so cosine is high.
  - If Document B has much smaller normalized tf for these terms, its dot product is lower.
- **Where title boost enters**
  - Cosine score is the base relevance from body text.
  - Then we add bonuses if query terms/phrases appear in title:
    - title term match bonus
    - title phrase match bonus
    - body phrase bonus
  - Final score (conceptually): `base_cosine + boosts`.
- **How to defend title boost**
  - Title usually summarizes page intent better than body.
  - Boost improves top-rank precision for navigational/entity queries.
  - You can prove effect live by comparing:
    - `SearchMain "cse department"`
    - `SearchMain --no-title-boost "cse department"`

### TA3 likely question: "How do you show title boost effect?"
- We support runtime toggle:
  - `java ... SearchMain "cse department"` (boost ON)
  - `java ... SearchMain --no-title-boost "cse department"` (boost OFF)
- We show score changes for the same query (title-relevant page gets much higher score with boost ON).
- In JSP UI, there is a checkbox toggle for title boost ON/OFF.

## Bonus Demo Checklist (Implemented: Excluded Terms)

- [ ] Show excluded term query: `university -science`
- [ ] Show excluded phrase query: `"hong kong" -"science park"`
- [ ] Demonstrate that excluded matches are removed from results
- [ ] Mention bonus in report and show it live (bonus is not counted if not demonstrated)

### Bonus feature explanation (what to say)
- We support negative filters in query parsing:
  - `-term` means exclude documents containing that stemmed term.
  - `-"phrase"` means exclude documents containing that exact phrase.
- Query parser splits input into 4 sets:
  - include terms, include phrases, exclude terms, exclude phrases.
- Retrieval flow:
  1) build candidate docs from include terms/phrases,
  2) remove any candidate that matches exclude terms/phrases,
  3) compute ranking only on remaining docs.
- Important point for TA:
  - exclusion is a hard filter (removes docs), not a soft score penalty.
- Example to explain live:
  - `university -science` -> normal university matches, but pages containing "science" are removed.
  - `"hong kong" -"science park"` -> keeps exact "hong kong" matches, drops those containing phrase "science park".

## Final Submission Artifacts Checklist

- [ ] Source code (no DB files in final submission package unless course asks separately)
- [ ] `readme.txt` with exact build/run/deploy steps
- [ ] 8-10 page report PDF
- [ ] Screenshots/evidence for core features + bonus
- [ ] Demo query sheet (5+ valid-set queries)
- [ ] DB tour sheet ready (`DB_TA2_TOUR.txt`)

## Last 10 Minutes Before Your Slot

- [ ] Open browser at search page
- [ ] Open `readme.txt` in terminal
- [ ] Open `spider_result.txt` in text editor
- [ ] Open report PDF
- [ ] Verify one query end-to-end
- [ ] Verify one phrase query
- [ ] Verify one excluded-term query
