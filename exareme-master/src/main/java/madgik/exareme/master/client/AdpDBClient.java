/**
 * Copyright MaDgIK Group 2010 - 2015.
 */
package madgik.exareme.master.client;

import madgik.exareme.common.app.engine.AdpDBQueryListener;
import madgik.exareme.utils.association.Pair;

import java.io.InputStream;
import java.rmi.RemoteException;
import java.util.HashMap;


/**
 * @author alex
 * @since 0.1
 */
public interface AdpDBClient {

    /**
     * @param queryScript
     * @return
     * @throws RemoteException
     */
    String explain(String queryScript, String exportMode) throws RemoteException;

    /**
     * @param queryID
     * @param queryScript
     * @return
     * @throws RemoteException
     */
    AdpDBClientQueryStatus query(String queryID, String queryScript) throws RemoteException;

    /**
     * @param queryID
     * @param queryScript
     * @param  hashQueryID
     * @return
     * @throws RemoteException
     */
    AdpDBClientQueryStatus query(String queryID, String queryScript, HashMap<String, Pair<Integer, String>> hashQueryID) throws RemoteException;


    /**
     * @param queryID
     * @param queryScript
     * @param listener
     * @return
     * @throws RemoteException
     */
    AdpDBClientQueryStatus aquery(String queryID, String queryScript, AdpDBQueryListener listener)
        throws RemoteException;

    /**
     * @param tableName
     * @return
     * @throws RemoteException
     */
    InputStream readTable(String tableName) throws RemoteException;

}
