
/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package javax.swing.text;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Toolkit;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Hashtable;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Vector;
import java.util.WeakHashMap;

import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;

import sun.font.FontUtilities;

/**
 * A pool of styles and their associated resources.  This class determines
 * the lifetime of a group of resources by being a container that holds
 * caches for various resources such as font and color that get reused
 * by the various style definitions.  This can be shared by multiple
 * documents if desired to maximize the sharing of related resources.
 * <p>
 * This class also provides efficient support for small sets of attributes
 * and compresses them by sharing across uses and taking advantage of
 * their immutable nature.  Since many styles are replicated, the potential
 * for sharing is significant, and copies can be extremely cheap.
 * Larger sets reduce the possibility of sharing, and therefore revert
 * automatically to a less space-efficient implementation.
 * <p>
 * <strong>Warning:</strong>
 * Serialized objects of this class will not be compatible with
 * future Swing releases. The current serialization support is
 * appropriate for short term storage or RMI between applications running
 * the same version of Swing.  As of 1.4, support for long term storage
 * of all JavaBeans
 * has been added to the <code>java.beans</code> package.
 * Please see {@link java.beans.XMLEncoder}.
 *
 * @author  Timothy Prinzing
 */
@SuppressWarnings("serial") // Same-version serialization only
public class StyleContext implements Serializable, AbstractDocument.AttributeContext {

    /**
     * Returns default AttributeContext shared by all documents that
     * don't bother to define/supply their own context.
     *
     * @return the context
     */
    public static final StyleContext getDefaultStyleContext() {
        if (defaultContext == null) {
            defaultContext = new StyleContext();
        }
        return defaultContext;
    }

    private static StyleContext defaultContext;

    /**
     * Creates a new StyleContext object.
     */
    public StyleContext() {
        styles = new NamedStyle(null);
        addStyle(DEFAULT_STYLE, null);
    }

    /**
     * Adds a new style into the style hierarchy.  Style attributes
     * resolve from bottom up so an attribute specified in a child
     * will override an attribute specified in the parent.
     *
     * @param nm   the name of the style (must be unique within the
     *   collection of named styles in the document).  The name may
     *   be null if the style is unnamed, but the caller is responsible
     *   for managing the reference returned as an unnamed style can't
     *   be fetched by name.  An unnamed style may be useful for things
     *   like character attribute overrides such as found in a style
     *   run.
     * @param parent the parent style.  This may be null if unspecified
     *   attributes need not be resolved in some other style.
     * @return the created style
     */
    public Style addStyle(String nm, Style parent) {
        Style style = new NamedStyle(nm, parent);
        if (nm != null) {
            // add a named style, a class of attributes
            styles.addAttribute(nm, style);
        }
        return style;
    }

    /**
     * Removes a named style previously added to the document.
     *
     * @param nm  the name of the style to remove
     */
    public void removeStyle(String nm) {
        styles.removeAttribute(nm);
    }

    /**
     * Fetches a named style previously added to the document
     *
     * @param nm  the name of the style
     * @return the style
     */
    public Style getStyle(String nm) {
        return (Style) styles.getAttribute(nm);
    }

    /**
     * Fetches the names of the styles defined.
     *
     * @return the list of names as an enumeration
     */
    public Enumeration<?> getStyleNames() {
        return styles.getAttributeNames();
    }

    /**
     * Adds a listener to track when styles are added
     * or removed.
     *
     * @param l the change listener
     */
    public void addChangeListener(ChangeListener l) {
        styles.addChangeListener(l);
    }

    /**
     * Removes a listener that was tracking styles being
     * added or removed.
     *
     * @param l the change listener
     */
    public void removeChangeListener(ChangeListener l) {
        styles.removeChangeListener(l);
    }

    /**
     * Returns an array of all the <code>ChangeListener</code>s added
     * to this StyleContext with addChangeListener().
     *
     * @return all of the <code>ChangeListener</code>s added or an empty
     *         array if no listeners have been added
     * @since 1.4
     */
    public ChangeListener[] getChangeListeners() {
        return ((NamedStyle)styles).getChangeListeners();
    }

    /**
     * Gets the font from an attribute set.  This is
     * implemented to try and fetch a cached font
     * for the given AttributeSet, and if that fails
     * the font features are resolved and the
     * font is fetched from the low-level font cache.
     *
     * @param attr the attribute set
     * @return the font
     */
    public Font getFont(AttributeSet attr) {
        // PENDING(prinz) add cache behavior
        int style = Font.PLAIN;
        if (StyleConstants.isBold(attr)) {
            style |= Font.BOLD;
        }
        if (StyleConstants.isItalic(attr)) {
            style |= Font.ITALIC;
        }
        String family = StyleConstants.getFontFamily(attr);
        int size = StyleConstants.getFontSize(attr);

        /**
         * if either superscript or subscript is
         * is set, we need to reduce the font size
         * by 2.
         */
        if (StyleConstants.isSuperscript(attr) ||
            StyleConstants.isSubscript(attr)) {
            size -= 2;
        }

        return getFont(family, style, size);
    }

    /**
     * Takes a set of attributes and turn it into a foreground color
     * specification.  This might be used to specify things
     * like brighter, more hue, etc.  By default it simply returns
     * the value specified by the StyleConstants.Foreground attribute.
     *
     * @param attr the set of attributes
     * @return the color
     */
    public Color getForeground(AttributeSet attr) {
        return StyleConstants.getForeground(attr);
    }

    /**
     * Takes a set of attributes and turn it into a background color
     * specification.  This might be used to specify things
     * like brighter, more hue, etc.  By default it simply returns
     * the value specified by the StyleConstants.Background attribute.
     *
     * @param attr the set of attributes
     * @return the color
     */
    public Color getBackground(AttributeSet attr) {
        return StyleConstants.getBackground(attr);
    }

    /**
     * Gets a new font.  This returns a Font from a cache
     * if a cached font exists.  If not, a Font is added to
     * the cache.  This is basically a low-level cache for
     * 1.1 font features.
     *
     * @param family the font family (such as "Monospaced")
     * @param style the style of the font (such as Font.PLAIN)
     * @param size the point size &gt;= 1
     * @return the new font
     */
    public Font getFont(String family, int style, int size) {
        fontSearch.setValue(family, style, size);
        Font f = fontTable.get(fontSearch);
        if (f == null) {
            // haven't seen this one yet.
            Style defaultStyle =
                getStyle(StyleContext.DEFAULT_STYLE);
            if (defaultStyle != null) {
                final String FONT_ATTRIBUTE_KEY = "FONT_ATTRIBUTE_KEY";
                Font defaultFont =
                    (Font) defaultStyle.getAttribute(FONT_ATTRIBUTE_KEY);
                if (defaultFont != null
                      && defaultFont.getFamily().equalsIgnoreCase(family)) {
                    f = defaultFont.deriveFont(style, size);
                }
            }
            if (f == null) {
                f = new Font(family, style, size);
            }
            if (! FontUtilities.fontSupportsDefaultEncoding(f)) {
                f = FontUtilities.getCompositeFontUIResource(f);
            }
            FontKey key = new FontKey(family, style, size);
            fontTable.put(key, f);
        }
        return f;
    }

    /**
     * Returns font metrics for a font.
     *
     * @param f the font
     * @return the metrics
     */
    @SuppressWarnings("deprecation")
    public FontMetrics getFontMetrics(Font f) {
        // The Toolkit implementations cache, so we just forward
        // to the default toolkit.
        return Toolkit.getDefaultToolkit().getFontMetrics(f);
    }

    // --- AttributeContext methods --------------------

    /**
     * Adds an attribute to the given set, and returns
     * the new representative set.
     * <p>
     * This method is thread safe, although most Swing methods
     * are not. Please see
     * <A HREF="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/index.html">Concurrency
     * in Swing</A> for more information.
     *
     * @param old the old attribute set
     * @param name the non-null attribute name
     * @param value the attribute value
     * @return the updated attribute set
     * @see MutableAttributeSet#addAttribute
     */
    public synchronized AttributeSet addAttribute(AttributeSet old, Object name, Object value) {
        if ((old.getAttributeCount() + 1) <= getCompressionThreshold()) {
            // build a search key and find/create an immutable and unique
            // set.
            search.removeAttributes(search);
            search.addAttributes(old);
            search.addAttribute(name, value);
            reclaim(old);
            return getImmutableUniqueSet();
        }
        MutableAttributeSet ma = getMutableAttributeSet(old);
        ma.addAttribute(name, value);
        return ma;
    }

    /**
     * Adds a set of attributes to the element.
     * <p>
     * This method is thread safe, although most Swing methods
     * are not. Please see
     * <A HREF="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/index.html">Concurrency
     * in Swing</A> for more information.
     *
     * @param old the old attribute set
     * @param attr the attributes to add
     * @return the updated attribute set
     * @see MutableAttributeSet#addAttribute
     */
    public synchronized AttributeSet addAttributes(AttributeSet old, AttributeSet attr) {
        if ((old.getAttributeCount() + attr.getAttributeCount()) <= getCompressionThreshold()) {
            // build a search key and find/create an immutable and unique
            // set.
            search.removeAttributes(search);
            search.addAttributes(old);
            search.addAttributes(attr);
            reclaim(old);
            return getImmutableUniqueSet();
        }
        MutableAttributeSet ma = getMutableAttributeSet(old);
        ma.addAttributes(attr);
        return ma;
    }

    /**
     * Removes an attribute from the set.
     * <p>
     * This method is thread safe, although most Swing methods
     * are not. Please see
     * <A HREF="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/index.html">Concurrency
     * in Swing</A> for more information.
     *
     * @param old the old set of attributes
     * @param name the non-null attribute name
     * @return the updated attribute set
     * @see MutableAttributeSet#removeAttribute
     */
    public synchronized AttributeSet removeAttribute(AttributeSet old, Object name) {
        if ((old.getAttributeCount() - 1) <= getCompressionThreshold()) {
            // build a search key and find/create an immutable and unique
            // set.
            search.removeAttributes(search);
            search.addAttributes(old);
            search.removeAttribute(name);
            reclaim(old);
            return getImmutableUniqueSet();
        }
        MutableAttributeSet ma = getMutableAttributeSet(old);
        ma.removeAttribute(name);
        return ma;
    }

    /**
     * Removes a set of attributes for the element.
     * <p>
     * This method is thread safe, although most Swing methods
     * are not. Please see
     * <A HREF="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/index.html">Concurrency
     * in Swing</A> for more information.
     *
     * @param old the old attribute set
     * @param names the attribute names
     * @return the updated attribute set
     * @see MutableAttributeSet#removeAttributes
     */
    public synchronized AttributeSet removeAttributes(AttributeSet old, Enumeration<?> names) {
        if (old.getAttributeCount() <= getCompressionThreshold()) {
            // build a search key and find/create an immutable and unique
            // set.
            search.removeAttributes(search);
            search.addAttributes(old);
            search.removeAttributes(names);
            reclaim(old);
            return getImmutableUniqueSet();
        }
        MutableAttributeSet ma = getMutableAttributeSet(old);
        ma.removeAttributes(names);
        return ma;
    }

    /**
     * Removes a set of attributes for the element.
     * <p>
     * This method is thread safe, although most Swing methods
     * are not. Please see
     * <A HREF="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/index.html">Concurrency
     * in Swing</A> for more information.
     *
     * @param old the old attribute set
     * @param attrs the attributes
     * @return the updated attribute set
     * @see MutableAttributeSet#removeAttributes
     */
    public synchronized AttributeSet removeAttributes(AttributeSet old, AttributeSet attrs) {
        if (old.getAttributeCount() <= getCompressionThreshold()) {
            // build a search key and find/create an immutable and unique
            // set.
            search.removeAttributes(search);
            search.addAttributes(old);
            search.removeAttributes(attrs);
            reclaim(old);
            return getImmutableUniqueSet();
        }
        MutableAttributeSet ma = getMutableAttributeSet(old);
        ma.removeAttributes(attrs);
        return ma;
    }

    /**
     * Fetches an empty AttributeSet.
     *
     * @return the set
     */
    public AttributeSet getEmptySet() {
        return SimpleAttributeSet.EMPTY;
    }

    /**
     * Returns a set no longer needed by the MutableAttributeSet implementation.
     * This is useful for operation under 1.1 where there are no weak
     * references.  This would typically be called by the finalize method
     * of the MutableAttributeSet implementation.
     * <p>
     * This method is thread safe, although most Swing methods
     * are not. Please see
     * <A HREF="https://docs.oracle.com/javase/tutorial/uiswing/concurrency/index.html">Concurrency
     * in Swing</A> for more information.
     *
     * @param a the set to reclaim
     */
    public void reclaim(AttributeSet a) {
        if (SwingUtilities.isEventDispatchThread()) {
            attributesPool.size(); // force WeakHashMap to expunge stale entries
        }
        // if current thread is not event dispatching thread
        // do not bother with expunging stale entries.
    }

    // --- local methods -----------------------------------------------

    /**
     * Returns the maximum number of key/value pairs to try and
     * compress into unique/immutable sets.  Any sets above this
     * limit will use hashtables and be a MutableAttributeSet.
     *
     * @return the threshold
     */
    protected int getCompressionThreshold() {
        return THRESHOLD;
    }

    /**
     * Create a compact set of attributes that might be shared.
     * This is a hook for subclasses that want to alter the
     * behavior of SmallAttributeSet.  This can be reimplemented
     * to return an AttributeSet that provides some sort of
     * attribute conversion.
     * @param a The set of attributes to be represented in the
     *  the compact form.
     * @return a compact set of attributes that might be shared
     */
    protected SmallAttributeSet createSmallAttributeSet(AttributeSet a) {
        return new SmallAttributeSet(a);
    }

    /**
     * Create a large set of attributes that should trade off
     * space for time.  This set will not be shared.  This is
     * a hook for subclasses that want to alter the behavior
     * of the larger attribute storage format (which is
     * SimpleAttributeSet by default).   This can be reimplemented
     * to return a MutableAttributeSet that provides some sort of
     * attribute conversion.
     *
     * @param a The set of attributes to be represented in the
     *  the larger form.
     * @return a large set of attributes that should trade off
     * space for time
     */
    protected MutableAttributeSet createLargeAttributeSet(AttributeSet a) {
        return new SimpleAttributeSet(a);
    }

    /**
     * Clean the unused immutable sets out of the hashtable.
     */
    synchronized void removeUnusedSets() {
        attributesPool.size(); // force WeakHashMap to expunge stale entries
    }

    /**
     * Search for an existing attribute set using the current search
     * parameters.  If a matching set is found, return it.  If a match
     * is not found, we create a new set and add it to the pool.
     */
    AttributeSet getImmutableUniqueSet() {
        // PENDING(prinz) should consider finding a alternative to
        // generating extra garbage on search key.
        SmallAttributeSet key = createSmallAttributeSet(search);
        WeakReference<SmallAttributeSet> reference = attributesPool.get(key);
        SmallAttributeSet a;
        if (reference == null || (a = reference.get()) == null) {
            a = key;
            attributesPool.put(a, new WeakReference<SmallAttributeSet>(a));
        }
        return a;
    }

    /**
     * Creates a mutable attribute set to hand out because the current
     * needs are too big to try and use a shared version.
     */
    MutableAttributeSet getMutableAttributeSet(AttributeSet a) {
        if (a instanceof MutableAttributeSet &&
            a != SimpleAttributeSet.EMPTY) {
            return (MutableAttributeSet) a;
        }
        return createLargeAttributeSet(a);
    }

    /**
     * Converts a StyleContext to a String.
     *
     * @return the string
     */
    public String toString() {
        removeUnusedSets();
        String s = "";
        for (SmallAttributeSet set : attributesPool.keySet()) {
            s = s + set + "\n";
        }
        return s;
    }

    // --- serialization ---------------------------------------------

    /**
     * Context-specific handling of writing out attributes
     * @param out the output stream
     * @param a the attribute set
     * @throws IOException on any I/O error
     */
    public void writeAttributes(ObjectOutputStream out,
                                  AttributeSet a) throws IOException {
        writeAttributeSet(out, a);
    }

    /**
     * Context-specific handling of reading in attributes
     * @param in the object stream to read the attribute data from.
     * @param a  the attribute set to place the attribute
     *   definitions in.
     * @throws ClassNotFoundException passed upward if encountered
     *  when reading the object stream.
     * @throws IOException passed upward if encountered when
     *  reading the object stream.
     */
    public void readAttributes(ObjectInputStream in,
                               MutableAttributeSet a) throws ClassNotFoundException, IOException {
        readAttributeSet(in, a);
    }

    /**
     * Writes a set of attributes to the given object stream
     * for the purpose of serialization.  This will take
     * special care to deal with static attribute keys that
     * have been registered with the
     * <code>registerStaticAttributeKey</code> method.
     * Any attribute key not registered as a static key
     * will be serialized directly.  All values are expected
     * to be serializable.
     *
     * @param out the output stream
     * @param a the attribute set
     * @throws IOException on any I/O error
     */
    public static void writeAttributeSet(ObjectOutputStream out,
                                         AttributeSet a) throws IOException {
        int n = a.getAttributeCount();
        out.writeInt(n);
        Enumeration<?> keys = a.getAttributeNames();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (key instanceof Serializable) {
                out.writeObject(key);
            } else {
                Object ioFmt = freezeKeyMap.get(key);
                if (ioFmt == null) {
                    throw new NotSerializableException(key.getClass().
                                 getName() + " is not serializable as a key in an AttributeSet");
                }
                out.writeObject(ioFmt);
            }
            Object value = a.getAttribute(key);
            Object ioFmt = freezeKeyMap.get(value);
            if (value instanceof Serializable) {
                out.writeObject((ioFmt != null) ? ioFmt : value);
            } else {
                if (ioFmt == null) {
                    throw new NotSerializableException(value.getClass().
                                 getName() + " is not serializable as a value in an AttributeSet");
                }
                out.writeObject(ioFmt);
            }
        }
    }

    /**
     * Reads a set of attributes from the given object input
     * stream that have been previously written out with
     * <code>writeAttributeSet</code>.  This will try to restore
     * keys that were static objects to the static objects in
     * the current virtual machine considering only those keys
     * that have been registered with the
     * <code>registerStaticAttributeKey</code> method.
     * The attributes retrieved from the stream will be placed
     * into the given mutable set.
     *
     * @param in the object stream to read the attribute data from.
     * @param a  the attribute set to place the attribute
     *   definitions in.
     * @throws ClassNotFoundException passed upward if encountered
     *  when reading the object stream.
     * @throws IOException passed upward if encountered when
     *  reading the object stream.
     */
    public static void readAttributeSet(ObjectInputStream in,
        MutableAttributeSet a) throws ClassNotFoundException, IOException {

        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            Object key = in.readObject();
            Object value = in.readObject();
            if (thawKeyMap != null) {
                Object staticKey = thawKeyMap.get(key);
                if (staticKey != null) {
                    key = staticKey;
                }
                Object staticValue = thawKeyMap.get(value);
                if (staticValue != null) {
                    value = staticValue;
                }
            }
            a.addAttribute(key, value);
        }
    }

    /**
     * Registers an object as a static object that is being
     * used as a key in attribute sets.  This allows the key
     * to be treated specially for serialization.
     * <p>
     * For operation under a 1.1 virtual machine, this
     * uses the value returned by <code>toString</code>
     * concatenated to the classname.  The value returned
     * by toString should not have the class reference
     * in it (ie it should be reimplemented from the
     * definition in Object) in order to be the same when
     * recomputed later.
     *
     * @param key the non-null object key
     */
    public static void registerStaticAttributeKey(Object key) {
        String ioFmt = key.getClass().getName() + "." + key.toString();
        if (freezeKeyMap == null) {
            freezeKeyMap = new Hashtable<Object, String>();
            thawKeyMap = new Hashtable<String, Object>();
        }
        freezeKeyMap.put(key, ioFmt);
        thawKeyMap.put(ioFmt, key);
    }

    /**
     * Returns the object previously registered with
     * <code>registerStaticAttributeKey</code>.
     * @param key the object key
     * @return Returns the object previously registered with
     * {@code registerStaticAttributeKey}
     */
    public static Object getStaticAttribute(Object key) {
        if (thawKeyMap == null || key == null) {
            return null;
        }
        return thawKeyMap.get(key);
    }

    /**
     * Returns the String that <code>key</code> will be registered with.
     * @see #getStaticAttribute
     * @see #registerStaticAttributeKey
     * @param key the object key
     * @return the String that {@code key} will be registered with
     */
    public static Object getStaticAttributeKey(Object key) {
        return key.getClass().getName() + "." + key.toString();
    }

    @Serial
    private void writeObject(java.io.ObjectOutputStream s)
        throws IOException
    {
        // clean out unused sets before saving
        removeUnusedSets();

        s.defaultWriteObject();
    }

    @Serial
    private void readObject(ObjectInputStream s)
      throws ClassNotFoundException, IOException
    {
        fontSearch = new FontKey(null, 0, 0);
        fontTable = new Hashtable<>();
        search = new SimpleAttributeSet();
        attributesPool = Collections.
                synchronizedMap(new WeakHashMap<SmallAttributeSet,
                        WeakReference<SmallAttributeSet>>());

        ObjectInputStream.GetField f = s.readFields();
        Style newStyles = (Style) f.get("styles", null);
        if (newStyles == null) {
            throw new InvalidObjectException("Null styles");
        }
        styles = newStyles;
        unusedSets = f.get("unusedSets", 0);
    }

    // --- variables ---------------------------------------------------

    /**
     * The name given to the default logical style attached
     * to paragraphs.
     */
    public static final String DEFAULT_STYLE = "default";

    private static Hashtable<Object, String> freezeKeyMap;
    private static Hashtable<String, Object> thawKeyMap;

    private Style styles;
    private transient FontKey fontSearch = new FontKey(null, 0, 0);
    private transient Hashtable<FontKey, Font> fontTable = new Hashtable<>();

    private transient Map<SmallAttributeSet, WeakReference<SmallAttributeSet>> attributesPool = Collections.
            synchronizedMap(new WeakHashMap<SmallAttributeSet, WeakReference<SmallAttributeSet>>());
    private transient MutableAttributeSet search = new SimpleAttributeSet();

    /**
     * Number of immutable sets that are not currently
     * being used.  This helps indicate when the sets need
     * to be cleaned out of the hashtable they are stored
     * in.
     */
    private int unusedSets;

    /**
     * The threshold for no longer sharing the set of attributes
     * in an immutable table.
     */
    static final int THRESHOLD = 9;

    /**
     * This class holds a small number of attributes in an array.
     * The storage format is key, value, key, value, etc.  The size
     * of the set is the length of the array divided by two.  By
     * default, this is the class that will be used to store attributes
     * when held in the compact shareable form.
     */
    public class SmallAttributeSet implements AttributeSet {

        /**
         * Constructs a SmallAttributeSet.
         * @param attributes the attributes
         */
        public SmallAttributeSet(Object[] attributes) {
            this.attributes = Arrays.copyOf(attributes, attributes.length);
            updateResolveParent();
        }

        /**
         * Constructs a SmallAttributeSet.
         * @param attrs the attributes
         */
        public SmallAttributeSet(AttributeSet attrs) {
            int n = attrs.getAttributeCount();
            Object[] tbl = new Object[2 * n];
            Enumeration<?> names = attrs.getAttributeNames();
            int i = 0;
            while (names.hasMoreElements()) {
                tbl[i] = names.nextElement();
                tbl[i+1] = attrs.getAttribute(tbl[i]);
                i += 2;
            }
            attributes = tbl;
            updateResolveParent();
        }

        private void updateResolveParent() {
            resolveParent = null;
            Object[] tbl = attributes;
            for (int i = 0; i < tbl.length; i += 2) {
                if (tbl[i] == StyleConstants.ResolveAttribute) {
                    resolveParent = (AttributeSet)tbl[i + 1];
                    break;
                }
            }
        }

        Object getLocalAttribute(Object nm) {
            if (nm == StyleConstants.ResolveAttribute) {
                return resolveParent;
            }
            Object[] tbl = attributes;
            for (int i = 0; i < tbl.length; i += 2) {
                if (nm.equals(tbl[i])) {
                    return tbl[i+1];
                }
            }
            return null;
        }

        // --- Object methods -------------------------

        /**
         * Returns a string showing the key/value pairs.
         * @return a string showing the key/value pairs
         */
        public String toString() {
            String s = "{";
            Object[] tbl = attributes;
            for (int i = 0; i < tbl.length; i += 2) {
                if (tbl[i+1] instanceof AttributeSet) {
                    // don't recurse
                    s = s + tbl[i] + "=" + "AttributeSet" + ",";
                } else {
                    s = s + tbl[i] + "=" + tbl[i+1] + ",";
                }
            }
            s = s + "}";
            return s;
        }

        /**
         * Returns a hashcode for this set of attributes.
         * @return     a hashcode value for this set of attributes.
         */
        public int hashCode() {
            int code = 0;
            Object[] tbl = attributes;
            for (int i = 1; i < tbl.length; i += 2) {
                code ^= tbl[i].hashCode();
            }
            return code;
        }

        /**
         * Compares this object to the specified object.
         * The result is <code>true</code> if the object is an equivalent
         * set of attributes.
         * @param     obj   the object to compare with.
         * @return    <code>true</code> if the objects are equal;
         *            <code>false</code> otherwise.
         */
        public boolean equals(Object obj) {
            if (obj instanceof AttributeSet) {
                AttributeSet attrs = (AttributeSet) obj;
                return ((getAttributeCount() == attrs.getAttributeCount()) &&
                        containsAttributes(attrs));
            }
            return false;
        }

        /**
         * Clones a set of attributes.  Since the set is immutable, a
         * clone is basically the same set.
         *
         * @return the set of attributes
         */
        public Object clone() {
            return this;
        }

        //  --- AttributeSet methods ----------------------------

        /**
         * Gets the number of attributes that are defined.
         *
         * @return the number of attributes
         * @see AttributeSet#getAttributeCount
         */
        public int getAttributeCount() {
            return attributes.length / 2;
        }

        /**
         * Checks whether a given attribute is defined.
         *
         * @param key the attribute key
         * @return true if the attribute is defined
         * @see AttributeSet#isDefined
         */
        public boolean isDefined(Object key) {
            Object[] a = attributes;
            int n = a.length;
            for (int i = 0; i < n; i += 2) {
                if (key.equals(a[i])) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Checks whether two attribute sets are equal.
         *
         * @param attr the attribute set to check against
         * @return true if the same
         * @see AttributeSet#isEqual
         */
        public boolean isEqual(AttributeSet attr) {
            if (attr instanceof SmallAttributeSet) {
                return attr == this;
            }
            return ((getAttributeCount() == attr.getAttributeCount()) &&
                    containsAttributes(attr));
        }

        /**
         * Copies a set of attributes.
         *
         * @return the copy