package de.fu_berlin.inf.dpp.activities.business;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.text.source.ILineRange;
import org.eclipse.jface.text.source.LineRange;

import de.fu_berlin.inf.dpp.activities.serializable.IActivityDataObject;
import de.fu_berlin.inf.dpp.activities.serializable.ViewportActivityDataObject;
import de.fu_berlin.inf.dpp.net.JID;

public class ViewportActivity extends AbstractActivity {

    protected final int topIndex;
    protected final int bottomIndex;
    protected final IPath path;

    public ViewportActivity(JID source, int topIndex, int bottomIndex,
        IPath editor) {
        super(source);

        if (editor == null) {
            throw new IllegalArgumentException("editor must not be null");
        }

        assert topIndex <= bottomIndex : "Top == " + topIndex + ", Bottom == "
            + bottomIndex;

        this.topIndex = topIndex;
        this.bottomIndex = bottomIndex;
        this.path = editor;
    }

    public ViewportActivity(JID source, ILineRange viewport, IPath editor) {
        this(source, Math.max(0, viewport.getStartLine()), Math.max(0, viewport
            .getStartLine())
            + Math.max(0, viewport.getNumberOfLines()), editor);
    }

    public ILineRange getLineRange() {
        return new LineRange(topIndex, bottomIndex - topIndex);
    }

    public int getBottomIndex() {
        return this.bottomIndex;
    }

    public int getTopIndex() {
        return this.topIndex;
    }

    public IPath getEditor() {
        return this.path;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + bottomIndex;
        result = prime * result + ((path == null) ? 0 : path.hashCode());
        result = prime * result + topIndex;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (!(obj instanceof ViewportActivity))
            return false;
        ViewportActivity other = (ViewportActivity) obj;
        if (bottomIndex != other.bottomIndex)
            return false;
        if (path == null) {
            if (other.path != null)
                return false;
        } else if (!path.equals(other.path))
            return false;
        if (topIndex != other.topIndex)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "ViewportActivity(path:" + this.path + ",range:("
            + this.topIndex + "," + this.bottomIndex + "))";
    }

    public boolean dispatch(IActivityConsumer consumer) {
        return consumer.consume(this);
    }

    public void dispatch(IActivityReceiver receiver) {
        receiver.receive(this);
    }

    public IActivityDataObject getActivityDataObject() {
        return new ViewportActivityDataObject(source, topIndex, bottomIndex,
            path);
    }
}
