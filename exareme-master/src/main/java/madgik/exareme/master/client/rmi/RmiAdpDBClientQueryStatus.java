package madgik.exareme.master.client.rmi;

import madgik.exareme.common.app.engine.*;
import madgik.exareme.common.art.ConcreteOperatorStatistics;
import madgik.exareme.common.art.ContainerSessionStatistics;
import madgik.exareme.common.schema.Index;
import madgik.exareme.common.schema.Partition;
import madgik.exareme.common.schema.PhysicalTable;
import madgik.exareme.common.schema.Table;
import madgik.exareme.master.client.AdpDBClientProperties;
import madgik.exareme.master.client.AdpDBClientQueryStatus;
import madgik.exareme.master.engine.AdpDBQueryExecutionPlan;
import madgik.exareme.master.engine.intermediateCache.Cache;
import madgik.exareme.master.engine.parser.SemanticException;
import madgik.exareme.master.registry.Registry;
import madgik.exareme.utils.chart.TimeFormat;
import madgik.exareme.utils.chart.TimeUnit;
import madgik.exareme.utils.embedded.db.TableInfo;
import madgik.exareme.utils.properties.AdpDBProperties;
import org.apache.log4j.Logger;

import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author alex
 */
public class RmiAdpDBClientQueryStatus implements AdpDBClientQueryStatus {
    private final static Logger log = Logger.getLogger(AdpDBClientQueryStatus.class);

    private AdpDBClientProperties properties;
    private AdpDBQueryExecutionPlan plan;
    private AdpDBStatus status;
    private String lastStatus;
    private String resultTableName;
    private TimeFormat timeF;
    private boolean finished;

    public RmiAdpDBClientQueryStatus(AdpDBQueryID queryId, AdpDBClientProperties properties,
                                     AdpDBQueryExecutionPlan plan, AdpDBStatus status) {
        this.properties = properties;
        this.plan = plan;
        this.status = status;
        this.resultTableName = null;
        this.lastStatus = null;
        this.timeF = new TimeFormat(TimeUnit.min);
        this.finished = false;
    }

    @Override
    public boolean hasFinished() throws RemoteException {
        if (finished) {
            return true;
        }
        if (status.hasFinished() == false && status.hasError() == false) {
            return false;
        }
        updateRegistry();
        finished = true;
        return true;
    }

    @Override
    public AdpDBQueryID getQueryID() {
        return plan.getQueryID();
    }

    @Override
    public String getStatus() throws RemoteException {
        lastStatus = status.getStatistics().toString();
        return lastStatus;
    }

    @Override
    public String getStatusIfChanged() throws RemoteException {
        String currentStatus = status.getStatistics().toString();
        if (!currentStatus.equals(lastStatus)) {
            lastStatus = currentStatus;
            return lastStatus;
        }
        return null;
    }

    @Override
    public boolean hasError() throws RemoteException {
        if (!finished)
            return false;
        return status.hasError();
    }

    @Override
    public String getError() throws RemoteException {
        return status.getLastException().toString();
    }

    @Override
    public String getExecutionTime() throws RemoteException {
        long startTime = status.getStatistics().getAdpEngineStatistics().startTime;
        long endTime = status.getStatistics().getAdpEngineStatistics().endTime;
        return String.format("%s m", timeF.format(endTime - startTime));
    }

    @Override
    public void close() throws RemoteException {
        status.stopExecution();
    }

    @Override
    public void registerListener(AdpDBQueryListener listener) throws RemoteException {
        status.registerListener(listener);
    }

    private void updateRegistry() throws RemoteException {
        if (status.hasError() == false) {

            // update registry
            HashMap<String, List<TableInfo>> resultTablesSQLDef = new HashMap<>();
            HashMap<String, List<ExecuteQueryExitMessage>> exitMessageMap = new HashMap<>();
            List<ExecuteQueryExitMessage> queryExitMessageList;

            List<TableInfo> tables;
            Set<String> tableSet = new HashSet<>();

            // containers
            for (ContainerSessionStatistics containerStat : status.getStatistics()
                    .getAdpEngineStatistics().containerStats) {
                // operators
                for (ConcreteOperatorStatistics operatorStatistics : containerStat.operators) {
                    ExecuteQueryExitMessage exitMessage =
                            (ExecuteQueryExitMessage) operatorStatistics.getExitMessage();
                    // get
                    if (exitMessage != null) {

                        tables = resultTablesSQLDef.get(exitMessage.outTableInfo.getTableName());
                        queryExitMessageList = exitMessageMap.get(exitMessage.outTableInfo.getTableName());
                        if (exitMessage.type == AdpDBOperatorType.tableUnionReplicator) {
                            tableSet.add(exitMessage.outTableInfo.getTableName());
                        }
                        if (tables == null) {
                            tables = new LinkedList<TableInfo>();
                            resultTablesSQLDef.put(exitMessage.outTableInfo.getTableName(), tables);
                            queryExitMessageList = new LinkedList<ExecuteQueryExitMessage>();
                            exitMessageMap.put(exitMessage.outTableInfo.getTableName(), queryExitMessageList);
                        }

                        tables.add(exitMessage.outTableInfo);
                        queryExitMessageList.add(exitMessage);

//                        resultTablesSQLDef.put(exitMessage.outTableInfo.getTableName(),
//                            exitMessage.outTableInfo.getSQLDefinition());
                    }
                }
            }

            //      log.debug(
            //          ConsoleFactory.getDefaultStatisticsFormat().format(
            //              status.getStatistics().getAdpEngineStatistics(),
            //              plan.getGraph(),
            //              RmiAdpDBSelectScheduler.runTimeParams
            //          )
            //      );

            // Adding result tables, indexes to schema.

            int totalSize;
            Set<String> pernamentTables = new HashSet<>();

            Registry registry = Registry.getInstance(properties.getDatabase());


            for (PhysicalTable resultTable : plan.getResultTables()) {

                //                for (Select selectQuery : plan.getScript().getSelectQueries()) {
//                    if (selectQuery.getParsedSqlQuery().getResultTable().equals(resultTable.getName())) {
//                        for (String column : selectQuery.getParsedSqlQuery().getPartitionColumns()) {
//                            resultTable.addPartitionColumn(column);
//                        }
//                        break;
//                    }
//                }
                pernamentTables.add(resultTable.getTable().getName());
                TableInfo tableInfo = resultTablesSQLDef.get(resultTable.getName()).get(0);
                totalSize = 0;
                queryExitMessageList = exitMessageMap.get(resultTable.getName());
                for (int part = 0; part < resultTable.getPartitions().size(); ++part) {
//          System.out.println("~pnum " + resultTable.getPartitions().get(part).getpNum());
                    for (ExecuteQueryExitMessage message : queryExitMessageList) {
                        if (message.serialNumber == resultTable.getPartitions().get(part).getpNum()) {

                            resultTable.getPartition(part).setSize(message.outTableInfo.getSizeInBytes());
                            totalSize += message.outTableInfo.getSizeInBytes();
                            break;
                        }
                    }
                }
                resultTable.getTable().setSqlQuery(queryExitMessageList.get(0).outTableInfo.getSqlQuery());
                resultTable.getTable().setSize(totalSize);

                if (resultTable.getTable().hasSQLDefinition() == false) {
                    if (resultTablesSQLDef.containsKey(resultTable.getName()) == false) {
                        throw new SemanticException(
                                "Table definition not found: " + resultTable.getName());
                    }
                    resultTableName = resultTable.getName();
                    String sqlDef = resultTablesSQLDef.get(resultTable.getName()).get(0).getSQLDefinition();
                    resultTable.getTable().setSqlDefinition(sqlDef);
                }
                resultTable.getTable().setTemp(false);
                registry.addPhysicalTable(resultTable);
                Cache cache = new Cache(properties);
                cache.unpinTable(resultTable.getName());
            }


            DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            java.util.Date dateobj = new Date();
            if (AdpDBProperties.getAdpDBProps().getString("db.cache").equals("true")) {

                Table table;
                Partition partition;
                PhysicalTable resultTable;
                HashMap<String, Integer> map;
                // Adding temporary tables to schema
                for (String tableName : tableSet) {

                    if (!pernamentTables.contains(tableName)) {  //Temporary table

                        table = new Table(tableName);
                        table.setTemp(true);
                        table.setHashID(plan.getScript().getTable(tableName).getTable().getHashID());
                        table.setNumOfAccess(0);
                        table.setLastAccess(df.format(dateobj));

                        resultTable = new PhysicalTable(table);
                        for (String column : plan.getScript().getTable(tableName).getPartitionColumns()) {
                            resultTable.addPartitionColumn(column);
                        }

                        //edwww
//                        for (Select selectQuery : plan.getScript().getSelectQueries()) {
//                            if (selectQuery.getParsedSqlQuery().getResultTable().equals(tableName)) {
//                                for (String column : selectQuery.getParsedSqlQuery().getPartitionColumns()) {
//                                    resultTable.addPartitionColumn(column);
//                                }
//                                break;
//                            }
//                        }

                        TableInfo tableInfo = resultTablesSQLDef.get(tableName).get(0);
                        totalSize = 0;
                        int size;
                        queryExitMessageList = exitMessageMap.get(tableName);
                        map = new HashMap<>();
                        for (int part = 0; part < queryExitMessageList.size(); ++part) { //resultTable.getPartitions().size()
                            for (ExecuteQueryExitMessage message : queryExitMessageList) {
                                if (message.serialNumber == part) {  //resultTable.getPartitions().get(part).getpNum()

                                    partition = new Partition(tableName, part);
                                    partition.setSize(message.outTableInfo.getSizeInBytes());
                                    partition.addLocation(message.outTableInfo.getLocation());
                                    resultTable.addPartition(partition);
                                    size = message.outTableInfo.getSizeInBytes();
                                    totalSize += size;
                                    if (!map.containsKey(message.outTableInfo.getLocation())) {
                                        map.put(message.outTableInfo.getLocation(), size);
                                    } else {
                                        size += map.get(message.outTableInfo.getLocation());
                                        map.put(message.outTableInfo.getLocation(), size);
                                    }
                                    break;
                                }
                            }
                        }
                        resultTable.getTable().setSqlQuery(queryExitMessageList.get(0).outTableInfo.getSqlQuery());
                        resultTable.getTable().setSize(totalSize);
                        table.setSize(totalSize);
                        Cache cache = new Cache(properties, Integer.parseInt(AdpDBProperties.getAdpDBProps().getString("db.cacheSize")));
                        try {
//                            System.out.println("map " + map);
                            cache.updateCache(table, map);
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }

                        if (resultTable.getTable().hasSQLDefinition() == false) {
                            if (resultTablesSQLDef.containsKey(resultTable.getName()) == false) {
                                throw new SemanticException(
                                        "Table definition not found: " + resultTable.getName());
                            }
                            resultTable.getTable().setSqlDefinition(tableInfo.getSQLDefinition());
                        }
                        registry.addPhysicalTable(resultTable);
                    }
                }


                //arxi
//                if (resultTable.getTable().hasSQLDefinition() == false) {
//                    if (resultTablesSQLDef.containsKey(resultTable.getName()) == false) {
//                        throw new SemanticException(
//                            "Table definition not found: " + resultTable.getName());
//                    }
//                    resultTableName = resultTable.getName();
//                    String sqlDef = resultTablesSQLDef.get(resultTable.getName());
//                    resultTable.getTable().setSqlDefinition(sqlDef);
//                }
//                registry.addPhysicalTable(resultTable);
            }

            for (Index index : plan.getBuildIndexes()) {
                registry.addIndex(index);
            }

            //Drop tables
            for (AdpDBDMOperator dmOP : plan.getDataManipulationOperators()) {
                if (dmOP.getType().equals(AdpDBOperatorType.dropTable)) {
                    registry.removePhysicalTable(dmOP.getDMQuery().getTable());
                }
            }
            log.debug("Registry updated.");
        }
    }
}
