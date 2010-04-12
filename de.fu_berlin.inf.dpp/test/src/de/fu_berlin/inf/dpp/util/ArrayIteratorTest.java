package de.fu_berlin.inf.dpp.util;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * checks both contructors per test
 * 
 * @author Lindner, Andreas und Marcus
 * 
 */

public class ArrayIteratorTest {
    protected Integer[] intarr3, intarr1, intarr0;
    
    @Before
    public void prepare() {
        intarr3 = new Integer[] { -20, 0, 100};
        intarr1 = new Integer[] { -20 };
        intarr0 = new Integer[] { };
    }
    
    @Test
    public void removeShouldBeUnsupported() {
        ArrayIterator<Integer> iterator0 = new ArrayIterator<Integer>(intarr0);
        ArrayIterator<Integer> iterator3 = new ArrayIterator<Integer>(intarr3, 0, 3);

        try {
            iterator0.remove();
            iterator3.remove();
            assertTrue(false);
        } catch (RuntimeException e) { /**/ }
    }

    @Test
    public void hasNextShouldBeValid() {
        ArrayIterator<Integer> iterator3 = new ArrayIterator<Integer>(intarr3);
        ArrayIterator<Integer> iterator1 = new ArrayIterator<Integer>(intarr1, 0, 1);

        assertTrue(iterator3.hasNext());
        iterator3.next();
        assertTrue(iterator3.hasNext());
        iterator3.next();
        assertTrue(iterator3.hasNext());
        iterator3.next();
        assertTrue(!iterator3.hasNext());

        assertTrue(iterator1.hasNext());
        iterator1.next();
        assertTrue(!iterator1.hasNext());
    }

    @Test
    public void nextShouldBeValidIfNotLast() {
        ArrayIterator<Integer> iterator1 = new ArrayIterator<Integer>(intarr1, 0, 1);
        ArrayIterator<Integer> iterator3 = new ArrayIterator<Integer>(intarr3);

        assertTrue(iterator1.next().equals(-20));
        
        assertTrue(iterator3.next().equals(-20));
        assertTrue(iterator3.next().equals(0));
        assertTrue(iterator3.next().equals(100));
    }

    @Test
    public void nextShouldExceptIfIsLast() {
        ArrayIterator<Integer> iterator3 = new ArrayIterator<Integer>(intarr3);
        ArrayIterator<Integer> iterator1 = new ArrayIterator<Integer>(intarr1, 0, 1);

        iterator3.next();
        iterator3.next();
        iterator3.next();
        try {
            iterator3.next();
            assertTrue(false);
        } catch (RuntimeException e) { /**/ }

        iterator1.next();
        try {
            iterator1.next();
            assertTrue(false);
        } catch (RuntimeException e) { /**/ }
    }
    
    @Test
    public void constructorShouldExceptIfNullArray() {
        // ArrayIterator<Integer> iterator_null = new ArrayIterator<Integer>(null);
        // TODO: check whether or not
    }
}
