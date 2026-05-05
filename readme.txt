================================================================
COMP4321 Project — Final Submission Readme
================================================================

----------------------------------------------------------------
1) REQUIREMENTS
----------------------------------------------------------------
- Java JDK 8+
- Apache Tomcat 9+ (for JSP UI)
- htmlparser.jar (project root)
- lib/jdbm-1.0.jar

----------------------------------------------------------------
2) SOURCE FILES (FINAL)
----------------------------------------------------------------
Core crawling/indexing:
- Spider.java
- Crawler.java
- StopStem.java
- IRUtilities/Porter.java

Database:
- DBManager.java
- Posting.java
- PageMetadata.java

Retrieval/ranking:
- QueryParser.java
- SearchEngine.java
- SearchMain.java

Output:
- TestProgram.java

Web UI:
- web/search.jsp
- web/results.jsp
- web/WEB-INF/web.xml

----------------------------------------------------------------
3) BUILD
----------------------------------------------------------------
From project root:

  javac -cp .:htmlparser.jar:lib/jdbm-1.0.jar \
    IRUtilities/Porter.java \
    StopStem.java \
    Posting.java \
    PageMetadata.java \
    DBManager.java \
    Crawler.java \
    Spider.java \
    QueryParser.java \
    SearchEngine.java \
    SearchMain.java \
    TestProgram.java

Windows: replace ':' with ';' in classpath.

----------------------------------------------------------------
4) INDEXING (FINAL: 300 PAGES)
----------------------------------------------------------------
Clear old DB:
  rm -f searchengine.db searchengine.lg

Run crawler (2026 seed URL):
  java -cp .:htmlparser.jar:lib/jdbm-1.0.jar Spider \
    "https://hitcslj.github.io/TestPages/testpage.htm" 300

Output files:
- searchengine.db
- searchengine.lg

----------------------------------------------------------------
5) RETRIEVAL (CLI)
----------------------------------------------------------------
Normal query:
  java -cp .:htmlparser.jar:lib/jdbm-1.0.jar SearchMain "computer science"

Phrase query:
  java -cp .:htmlparser.jar:lib/jdbm-1.0.jar SearchMain "\"hong kong\""

Bonus excluded-term query:
  java -cp .:htmlparser.jar:lib/jdbm-1.0.jar SearchMain "university -science"

Bonus excluded-phrase query:
  java -cp .:htmlparser.jar:lib/jdbm-1.0.jar SearchMain "\"hong kong\" -\"science park\""

Title-boost comparison:
  java -cp .:htmlparser.jar:lib/jdbm-1.0.jar SearchMain "cse department"
  java -cp .:htmlparser.jar:lib/jdbm-1.0.jar SearchMain --no-title-boost "cse department"

----------------------------------------------------------------
6) SPIDER RESULT OUTPUT
----------------------------------------------------------------
Generate spider output:
  java -cp .:htmlparser.jar:lib/jdbm-1.0.jar TestProgram

Output file:
- spider_result.txt

----------------------------------------------------------------
7) TOMCAT JSP WEB INTERFACE
----------------------------------------------------------------
App URL after deployment:
  http://localhost:8080/comp4321search/search.jsp

If CATALINA_BASE is available, deploy under:
  $CATALINA_BASE/webapps/comp4321search/

Fedora service-path example:
  /var/lib/tomcat/webapps/comp4321search/

Deploy commands (Fedora):
  sudo mkdir -p /var/lib/tomcat/webapps/comp4321search/WEB-INF/classes/IRUtilities
  sudo mkdir -p /var/lib/tomcat/webapps/comp4321search/WEB-INF/lib
  sudo cp web/search.jsp /var/lib/tomcat/webapps/comp4321search/
  sudo cp web/results.jsp /var/lib/tomcat/webapps/comp4321search/
  sudo cp web/WEB-INF/web.xml /var/lib/tomcat/webapps/comp4321search/WEB-INF/
  sudo cp *.class /var/lib/tomcat/webapps/comp4321search/WEB-INF/classes/
  sudo cp IRUtilities/*.class /var/lib/tomcat/webapps/comp4321search/WEB-INF/classes/IRUtilities/
  sudo cp htmlparser.jar /var/lib/tomcat/webapps/comp4321search/WEB-INF/lib/
  sudo cp lib/jdbm-1.0.jar /var/lib/tomcat/webapps/comp4321search/WEB-INF/lib/

----------------------------------------------------------------
8) IMPLEMENTED FINAL FEATURES
----------------------------------------------------------------
- Vector-space retrieval with cosine similarity.
- Weighting uses (tf/max_tf) * idf.
- Phrase search with positional postings (body and title).
- Title boosting in ranking.
- Top 50 result cap.
- Required UI fields: score, title, URL, last-modified, size, top keywords,
  parent links, child links.
- Bonus: excluded terms/phrases (-term, -"phrase").
- Extra demo feature: title-boost ON/OFF toggle (CLI + JSP checkbox).

----------------------------------------------------------------
9) DATABASE DESIGN DOCUMENT
----------------------------------------------------------------
See:
- database_design.txt

================================================================
