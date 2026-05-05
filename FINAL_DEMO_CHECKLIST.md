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
javac -cp .:htmlparser.jar:lib/jdbm-1.0.jar IRUtilities/Porter.java StopStem.java Posting.java PageMetadata.java DBManager.java Crawler.java Spider.java QueryParser.java SearchEngine.java SearchMain.java TestProgram.java

# Crawl (hidden seed / normal seed)
java -cp .:htmlparser.jar:lib/jdbm-1.0.jar Spider "https://hitcslj.github.io/TestPages/testpage.htm" 300

# TA1/TA2 hidden test rerun (N=30, empty DB)
rm -f searchengine.db searchengine.lg
java -cp .:htmlparser.jar:lib/jdbm-1.0.jar Spider "<TA_PROVIDED_SEED>" 30

# Generate spider result
java -cp .:htmlparser.jar:lib/jdbm-1.0.jar TestProgram

# CLI retrieval spot checks
java -cp .:htmlparser.jar:lib/jdbm-1.0.jar SearchMain "computer science"
java -cp .:htmlparser.jar:lib/jdbm-1.0.jar SearchMain "\"hong kong\" universities"
java -cp .:htmlparser.jar:lib/jdbm-1.0.jar SearchMain "\"hong kong\" -\"science park\""
```

## Station 1 (TA1): Crawling Checklist

- [ ] Show `spider_result.txt`, especially page ID 0 section
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
- [ ] Explain speed choices (BFS design, skip logic, lightweight parsing, etc.)

## Station 2 (TA2): Indexing & DB Checklist

- [ ] Show posting-list inspector capability (stem -> docs + tf)
- [ ] Show stopword removal:
  - `the`, `of`, `a` -> not found
- [ ] Show stemming parity:
  - `running` and `run` map consistently
- [ ] Show body and title are separate indexes
- [ ] Show phrase-search support evidence (positional postings)
- [ ] Do a fresh hidden seed re-index with N=30 (<1 min target)
- [ ] Walk through DB schema tables/HTrees

## Station 3 (TA3): Retrieval / Ranking / UI Checklist

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
- We demonstrate with quoted queries like `"hong kong"` in retrieval.

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

## Final Submission Artifacts Checklist

- [ ] Source code (no DB files in final submission package unless course asks separately)
- [ ] `readme.txt` with exact build/run/deploy steps
- [ ] 8-10 page report PDF
- [ ] Screenshots/evidence for core features + bonus
- [ ] Demo query sheet (5+ valid-set queries)

## Last 10 Minutes Before Your Slot

- [ ] Open browser at search page
- [ ] Open `readme.txt` in terminal
- [ ] Open `spider_result.txt` in text editor
- [ ] Open report PDF
- [ ] Verify one query end-to-end
- [ ] Verify one phrase query
- [ ] Verify one excluded-term query
