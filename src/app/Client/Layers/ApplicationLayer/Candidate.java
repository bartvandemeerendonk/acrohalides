package app.Client.Layers.ApplicationLayer;

public class Candidate {
    private boolean electable;
    private String tag;
    private String description;
    private Candidate parent;

    public Candidate(boolean electable, String tag, String description) {
        this.electable = electable;
        this.tag = tag;
        this.description = description;
        this.parent = null;
    }

    public boolean isElectable() {
        return electable;
    }

    public String getTag() {
        return tag;
    }

    public String getDescription() {
        return description;
    }

    public Candidate getParent() {
        return parent;
    }

    public void setParent(Candidate parent) {
        this.parent = parent;
    }
}
