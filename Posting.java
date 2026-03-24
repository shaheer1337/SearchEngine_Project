import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents one entry in an inverted index posting list.
 * Stores the page ID, term frequency, and word positions (for phrase search).
 */
public class Posting implements Serializable
{
    private static final long serialVersionUID = 1L;

    public int pageID;
    public int tf;                   // term frequency in this document
    public List<Integer> positions;  // 0-based word positions for phrase search

    public Posting(int pageID, int tf, List<Integer> positions)
    {
        this.pageID    = pageID;
        this.tf        = tf;
        this.positions = new ArrayList<>(positions);
    }

    @Override
    public String toString()
    {
        return "Posting{pageID=" + pageID + ", tf=" + tf + ", positions=" + positions + "}";
    }
}
