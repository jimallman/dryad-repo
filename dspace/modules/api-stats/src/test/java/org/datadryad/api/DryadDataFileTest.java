/*
 */
package org.datadryad.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import org.datadryad.test.ContextUnitTest;
import org.dspace.content.Collection;
import org.dspace.content.DCValue;
import org.dspace.core.Context;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;

/**
 *
 * @author Dan Leehr <dan.leehr@nescent.org>
 */
public class DryadDataFileTest extends ContextUnitTest {

    private Calendar calendar;
    private Date pastDate;
    private Date nowDate;
    private Date futureDate;

    @Before
    public void setUp() {
        super.setUp();
        calendar = new GregorianCalendar();
        nowDate = calendar.getTime();
        int currentYear = calendar.get(Calendar.YEAR);
        calendar.set(Calendar.YEAR, currentYear - 1);
        pastDate = calendar.getTime();
        calendar.set(Calendar.YEAR, currentYear + 1);
        futureDate = calendar.getTime();
    }

    /**
     * Test of getCollection method, of class DryadDataFile.
     */
    @Test
    public void testGetCollection() throws Exception {
        Context context = this.context;
        Collection result = DryadDataFile.getCollection(context);
        assertEquals(result.getName(), "Dryad Data Files");
    }

    /**
     * Test of create method, of class DryadDataFile.
     */
    @Test
    public void testCreate() throws Exception {
        DryadDataFile result = DryadDataFile.create(context);
        assertNotNull(result);
    }

    /**
     * Test of parseDate method, of class DryadDataFile.
     */
    @Test
    public void testParseDate() throws Exception {
        String dateString = "2014-06-12";
        Date expResult = null;
        Date result = DryadDataFile.parseDate(dateString);
        calendar.setTime(result);
        assertEquals(calendar.get(Calendar.YEAR), 2014);
        assertEquals(calendar.get(Calendar.MONTH), Calendar.JUNE);
        assertEquals(calendar.get(Calendar.DAY_OF_MONTH), 12);
    }

    /**
     * Test of formatDate method, of class DryadDataFile.
     */
    @Test
    public void testFormatDate() {
        calendar.set(Calendar.YEAR, 2015);
        calendar.set(Calendar.MONTH, Calendar.JULY);
        calendar.set(Calendar.DAY_OF_MONTH, 25);
        String expResult = "2015-07-25";
        String result = DryadDataFile.formatDate(calendar.getTime());
        assertEquals(expResult, result);
    }


    /**
     * Test of getEarliestDate method, of class DryadDataFile.
     */
    @Test
    public void testGetEarliestDate_DateArr() {
        System.out.println("getEarliestDate");
        Date[] dates = {
            futureDate,
            pastDate,
            nowDate
        };
        Date expResult = pastDate;
        Date result = DryadDataFile.getEarliestDate(dates);
        assertEquals(expResult, result);
    }

    /**
     * Test of isEmbargoed method, of class DryadDataFile.
     */
    @Test
    public void testIsEmbargoed() throws SQLException {
        System.out.println("isEmbargoed");
        DryadDataFile instance = DryadDataFile.create(context);
        // not initially embargoed, so set one in the past
        instance.setEmbargo("oneyear", futureDate);
        assertTrue(instance.isEmbargoed());
        instance.setEmbargo("oneyear", pastDate);
        assertFalse(instance.isEmbargoed());
    }

    /**
     * Test of clearEmbargo method, of class DryadDataFile.
     */
    @Test
    public void testClearEmbargo() throws Exception {
        System.out.println("clearEmbargo");
        DryadDataFile instance = DryadDataFile.create(context);
        instance.clearEmbargo();
        instance.setEmbargo("oneyear", futureDate);
        instance.clearEmbargo();
        assertFalse(instance.isEmbargoed());
    }

    /**
     * Test of setEmbargo method, of class DryadDataFile.
     */
    @Test
    public void testSetEmbargo() throws Exception {
        System.out.println("setEmbargo");
        DryadDataFile instance = DryadDataFile.create(context);
        instance.setEmbargo("untilArticleAppears", futureDate);
        assertTrue(instance.isEmbargoed());
    }
    /**
     * Test of addBitstream method, of class DryadDataFile.
     */
    @Test
    public void testAddBitstream() throws Exception {
        System.out.println("addBitstream");
        InputStream stream = DryadDataFileTest.class.getClassLoader().getResourceAsStream("Logo-one-tone-dark.jpg");
        DryadDataFile instance = DryadDataFile.create(context);
        instance.addBitstream(stream);
    }

    /**
     * Test of getTotalStorageSize method, of class DryadDataFile.
     */
    @Test
    public void testGetTotalStorageSize() throws Exception {
        System.out.println("getTotalStorageSize");

        File file1 = new File(DryadDataFileTest.class.getClassLoader().getResource("Logo-one-tone-dark.jpg").toURI());
        File file2 = new File(DryadDataFileTest.class.getClassLoader().getResource("world.js").toURI());

        Long expResult = file1.length() + file2.length();
        assertTrue(expResult > 0);

        DryadDataFile instance = DryadDataFile.create(context);
        instance.addBitstream(new FileInputStream(file1));
        instance.addBitstream(new FileInputStream(file2));

        Long result = instance.getTotalStorageSize();
        assertEquals(expResult, result);
    }
}
