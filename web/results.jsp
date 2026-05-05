<%@ page import="java.util.*" %>
<%@ page import="java.text.DecimalFormat" %>
<%@ page import="java.lang.reflect.*" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Search Results</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 24px; }
        .result { border: 1px solid #ddd; padding: 12px; margin-bottom: 12px; max-width: 1100px; }
        .title { font-size: 18px; margin-bottom: 4px; }
        .meta { color: #444; margin-bottom: 4px; }
        .kw { color: #333; margin: 8px 0; }
        .section { margin-top: 8px; }
        .section-title { font-weight: bold; }
        a { color: #1a0dab; text-decoration: none; }
        a:hover { text-decoration: underline; }
    </style>
</head>
<body>
<%
    String query = request.getParameter("q");
    if (query == null) query = "";
    boolean enableTitleBoost = "on".equalsIgnoreCase(request.getParameter("titleBoost"));
%>
<h2>Search Results</h2>
<form method="get" action="results.jsp">
    <input type="text" name="q" value="<%= query.replace("\"", "&quot;") %>" style="width:700px;padding:8px;" />
    <label style="margin-left:10px;">
        <input type="checkbox" name="titleBoost" value="on" <%= enableTitleBoost ? "checked" : "" %> />
        Title boost
    </label>
    <button type="submit" style="padding:8px 12px;">Search</button>
</form>
<p><a href="search.jsp">Back to search page</a></p>

<%
    if (query.trim().isEmpty())
    {
%>
    <p>Please enter a query.</p>
<%
    }
    else
    {
        Object dbm = null;
        try
        {
            Class<?> dbManagerCls = Class.forName("DBManager");
            dbm = dbManagerCls.getConstructor().newInstance();

            Class<?> searchEngineCls = Class.forName("SearchEngine");
            Object engine;
            try
            {
                engine = searchEngineCls.getConstructor(dbManagerCls, boolean.class)
                        .newInstance(dbm, enableTitleBoost);
            }
            catch (NoSuchMethodException nsme)
            {
                // Backward compatibility if only single-arg constructor is present.
                engine = searchEngineCls.getConstructor(dbManagerCls).newInstance(dbm);
            }

            Method searchMethod = searchEngineCls.getMethod("search", String.class);
            List<?> results = (List<?>) searchMethod.invoke(engine, query);
            DecimalFormat fmt = new DecimalFormat("0.000000");
%>
            <p>Query: <b><%= query %></b></p>
            <p>Title boost: <b><%= enableTitleBoost ? "ON" : "OFF" %></b></p>
            <p>Returned <b><%= results.size() %></b> result(s).</p>
<%
            for (Object r : results)
            {
                Class<?> resultCls = r.getClass();
                double score = resultCls.getField("score").getDouble(r);
                Object m = resultCls.getField("metadata").get(r);

                Class<?> metaCls = m.getClass();
                String title = (String) metaCls.getField("title").get(m);
                String url = (String) metaCls.getField("url").get(m);
                String lastModified = (String) metaCls.getField("lastModified").get(m);
                int size = metaCls.getField("size").getInt(m);

                @SuppressWarnings("unchecked")
                List<String> topKeywords = (List<String>) resultCls.getField("topKeywords").get(r);
                @SuppressWarnings("unchecked")
                List<String> parentUrls = (List<String>) resultCls.getField("parentUrls").get(r);
                @SuppressWarnings("unchecked")
                List<String> childUrls = (List<String>) resultCls.getField("childUrls").get(r);
%>
                <div class="result">
                    <div class="title">
                        <%= fmt.format(score) %>
                        <a href="<%= url %>" target="_blank"><%= title %></a>
                    </div>
                    <div><a href="<%= url %>" target="_blank"><%= url %></a></div>
                    <div class="meta"><%= lastModified %>, <%= size %></div>
                    <div class="kw">
                        <%
                            for (int i = 0; i < topKeywords.size(); i++)
                            {
                                if (i > 0) out.print("; ");
                                out.print(topKeywords.get(i));
                            }
                        %>
                    </div>

                    <div class="section">
                        <span class="section-title">Parent links</span><br/>
                        <%
                            for (String p : parentUrls)
                            {
                        %>
                            <a href="<%= p %>" target="_blank"><%= p %></a><br/>
                        <%
                            }
                        %>
                    </div>

                    <div class="section">
                        <span class="section-title">Child links</span><br/>
                        <%
                            for (String c : childUrls)
                            {
                        %>
                            <a href="<%= c %>" target="_blank"><%= c %></a><br/>
                        <%
                            }
                        %>
                    </div>
                </div>
<%
            }
        }
        catch (Exception ex)
        {
%>
            <p>Error while searching: <%= ex.getMessage() %></p>
<%
        }
        finally
        {
            if (dbm != null)
            {
                Class<?> dbManagerCls = Class.forName("DBManager");
                Method closeMethod = dbManagerCls.getMethod("close");
                closeMethod.invoke(dbm);
            }
        }
    }
%>

</body>
</html>
