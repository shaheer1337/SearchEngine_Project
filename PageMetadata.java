import java.io.Serializable;

/**
 * Stores all metadata for a crawled page.
 * Serialized and stored in JDBM keyed by pageID.
 */
public class PageMetadata implements Serializable
{
    private static final long serialVersionUID = 1L;

    public String title;
    public String url;
    public String lastModified;  // as a String, e.g. "2024-01-15" or HTTP date header value
    public int    size;          // content length in characters (or bytes from header)
    public int    maxTF;         // max term frequency among all body terms (for tf/max_tf normalization)

    public PageMetadata(String title, String url, String lastModified, int size, int maxTF)
    {
        this.title        = title;
        this.url          = url;
        this.lastModified = lastModified;
        this.size         = size;
        this.maxTF        = maxTF;
    }

    @Override
    public String toString()
    {
        return "PageMetadata{title='" + title + "', url='" + url +
               "', lastModified='" + lastModified + "', size=" + size +
               ", maxTF=" + maxTF + "}";
    }
}
