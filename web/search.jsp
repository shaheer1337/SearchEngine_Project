<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>COMP4321 Search Engine</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 24px; }
        .hint { color: #555; font-size: 14px; margin-top: 8px; }
        .box { margin-top: 20px; padding: 12px; border: 1px solid #ddd; max-width: 900px; }
        input[type=text] { width: 700px; padding: 8px; }
        button { padding: 8px 12px; }
    </style>
</head>
<body>
<h2>COMP4321 Search Engine</h2>

<div class="box">
    <form method="get" action="results.jsp">
        <input type="text" name="q" placeholder="Enter keywords or &quot;quoted phrase&quot;" />
        <label style="margin-left:10px;">
            <input type="checkbox" name="titleBoost" value="on" checked />
            Title boost
        </label>
        <button type="submit">Search</button>
    </form>
    <div class="hint">
        Supports phrases with double quotes, e.g. <code>"hong kong"</code>.<br/>
        Bonus feature: exclude terms/phrases with minus, e.g. <code>university -science -"computer science"</code>.
    </div>
</div>

</body>
</html>
