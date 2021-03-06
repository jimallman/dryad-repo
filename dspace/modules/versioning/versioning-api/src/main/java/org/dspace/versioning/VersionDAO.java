package org.dspace.versioning;

import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: fabio.bolognesi
 * Date: Mar 30, 2011
 * Time: 9:15:14 AM
 * To change this template use File | Settings | File Templates.
 */
public class VersionDAO {

    protected final static String TABLE_NAME = "versionitem";
    protected final static String VERSION_ID = "versionitem_id";
    protected final static String ITEM_ID = "item_id";
    protected final static String VERSION_NUMBER = "version_number";
    protected final static String EPERSON_ID = "eperson_id";
    protected final static String VERSION_DATE = "version_date";
    protected final static String VERSION_SUMMARY = "version_summary";
    protected final static String HISTORY_ID = "versionhistory_id";


    public VersionImpl find(Context context, int id) {
        try {
            TableRow row = DatabaseManager.findByUnique(context, TABLE_NAME, VERSION_ID, id);

            if (row == null) return null;

            return new VersionImpl(context, row);
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

    }

    public VersionImpl findByItem(Context c, Item item) {
        return findByItemId(c, item.getID());
    }

    public VersionImpl findByItemId(Context context, int itemId) {
        try {
            if (itemId == 0 || itemId == -1) return null;

            VersionImpl fromCache = (VersionImpl) context.fromCache(VersionImpl.class, itemId);
            if (fromCache != null) return fromCache;

            TableRow row = DatabaseManager.findByUnique(context, TABLE_NAME, ITEM_ID, itemId);
            if (row == null) return null;

            return new VersionImpl(context, row);
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }


    public List<Version> findByVersionHistory(Context context, int versionHistoryId) {
        TableRowIterator rows = null;
        try {
            //TODO better to use Database.queryPrepared() ???
            rows = DatabaseManager.query(context, "SELECT * FROM " + TABLE_NAME + " where " + HISTORY_ID + "=" + versionHistoryId + " order by " + VERSION_NUMBER + " desc");
            List versionRows = rows.toList();

            List<Version> versions = new ArrayList<Version>();
            for (Object o : versionRows) {
                TableRow tr = (TableRow) o;

                VersionImpl fromCache = (VersionImpl) context.fromCache(VersionImpl.class, tr.getIntColumn(VERSION_ID));

                if (fromCache != null) versions.add(fromCache);
                else versions.add(new VersionImpl(context, tr));
            }
            return versions;
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            if (rows != null) rows.close();
        }

    }


    public VersionImpl create(Context context) {
        try {
            TableRow row = DatabaseManager.create(context, TABLE_NAME);
            VersionImpl v = new VersionImpl(context, row);

            //TODO Do I have to manage the event?
            //context.addEvent(new Event(Event.CREATE, Constants.EPERSON, e.getID(), null));

            return v;
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }


    public void delete(Context c, int versionID) {
        try {
            //TODO Do I have to manage the event?
            //context.addEvent(new Event(Event.DELETE, Constants.EPERSON, getID(), getEmail()));

            // Remove ourself
            VersionImpl version = find(c, versionID);
            if(version!=null)
                DatabaseManager.delete(c, version.getMyRow());
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }


    public void update(VersionImpl version) {
        try {
            //TODO Do I have to manage the event?
            DatabaseManager.update(version.getMyContext(), version.getMyRow());


//        if (modified)
//        {
//            myContext.addEvent(new Event(Event.MODIFY, Constants.EPERSON, getID(), null));
//            modified = false;
//        }
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

    }
}
