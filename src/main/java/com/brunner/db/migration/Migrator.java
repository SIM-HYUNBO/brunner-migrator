
package com.brunner.db.migration;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JTable;
import javax.swing.SwingUtilities;

import com.fasterxml.jackson.databind.JsonNode;

public class Migrator {
    private Connection sourceConnection;
    private Connection targetConnection;
    private JsonNode config;
    frmDBMigrator frm = null;
    boolean migInProcess = false;

    public Migrator(frmDBMigrator frm, JsonNode config) throws SQLException {
        this.frm = frm;
        this.config = config;
    }

    public void connect() throws SQLException {
        this.sourceConnection = DBConnector.getDBConnection(config.get("source_db"));
        this.targetConnection = DBConnector.getDBConnection(config.get("target_db"));
    }

    public void migrateAllTables(String taskName) throws SQLException {
        connect();

        migInProcess = true;
        for (JsonNode table : config.get("tables")) {
            if (!migInProcess) {
                frm.AddLog(String.format("[%s] Migration stopped by user.", taskName), Color.BLUE);
                break;
            }

            Rectangle cellRect = frm.tblTableList.getCellRect(frm.tblTableList.getSelectedRow(), 0, true);
            frm.tblTableList.scrollRectToVisible(cellRect);

            // source, target 테이블명
            String sourceTable = table.get("source_tb").asText();
            String targetTable = table.get("target_tb").asText();
            // 에러 발생 시 처리 방법 (resume_next, stop_migration)
            String onError = table.has("on_error") ? table.get("on_error").asText() : "stop_migration";

            // 해당 테이블 스킵 여부 (true, false)
            boolean skip_flag = table.has("skip_flag") && table.get("skip_flag").asBoolean();

            try {
                if (skip_flag) {
                    frm.AddLog(String.format("[%s] Skip table : %s -> %s", taskName, sourceTable, targetTable), Color.BLUE);
                    continue;
                }

                // 현재 작업 중인 테이블을 선택하여 반전으로 표시
                int rowIndex = findTableRowIndex(frm.tblTableList, sourceTable);
                if (rowIndex >= 0) {
                    SwingUtilities.invokeLater(() -> {
                        Rectangle cellRectRow = frm.tblTableList.getCellRect(rowIndex, 0, true);
                        frm.tblTableList.scrollRectToVisible(cellRectRow);
                        frm.tblTableList.setRowSelectionInterval(rowIndex, rowIndex);
                    });
                }

                String rangeColumn = table.has("range_column") ? table.get("range_column").asText() : "";
                String rangeType = table.has("range_type") ? table.get("range_type").asText() : "";
                String rangeFrom = table.has("range_from") ? table.get("range_from").asText() : "";
                String rangeTo = table.has("range_to") ? table.get("range_to").asText() : "";

                // int batchSize = table.has("batch_size") ? table.get("batch_size").asInt() :
                // 1000;

                String trunc_target_tb = table.has("trunc_target_tb")
                        ? table.get("trunc_target_tb").asText()
                        : "false";

                frm.AddLog(String.format("\n-[%s] Start Table: %s -> %s", taskName, sourceTable, targetTable), Color.BLUE);
                migrateTable(taskName,
                        config.get("source_db").get("type").asText(),
                        config.get("target_db").get("type").asText(),
                        sourceTable,
                        targetTable,
                        rangeColumn,
                        rangeType,
                        rangeFrom,
                        rangeTo,
                        // batchSize,
                        trunc_target_tb,
                        onError);
                frm.AddLog(String.format("[%s] Completed Table: %s -> %s\n", taskName, sourceTable, targetTable), Color.BLUE);
            } catch (Exception e) {
                if (onError.equals("resume_next")) {
                    frm.AddLog(e);
                    frm.AddLog(String.format("[%s] Skip Table %s and Moved to next table.", taskName, sourceTable), Color.RED);
                } else {
                    throw e;
                }
            }
        }

        closeConnections();
        this.frm.AddLog(String.format("[%s] Connections closed.", taskName), Color.BLACK);
    }

    private int findTableRowIndex(JTable table, String tableName) {
        for (int i = 0; i < table.getRowCount(); i++) {
            if (table.getValueAt(i, 0).equals(tableName)) {
                return i;
            }
        }
        return -1;
    }

    private void migrateTable(String taskName,
            String sourceDbType,
            String targetDbType,
            String sourceTable,
            String targetTable,
            String rangeColumn,
            String rangeType,
            String range_from,
            String range_to,
            String trunc_target_tb,
            String onError) throws SQLException {

        int totalRows = 0;

        if (!migInProcess) {
            frm.AddLog(String.format("[%s] Migration stopped by user.", taskName), Color.BLUE);
            return;
        }

        String sql = "";
        switch (sourceDbType.toLowerCase()) {
            case "mysql":
            case "postgresql":
            case "oracle":
                sql = "SELECT COUNT(1) FROM " + sourceTable;
                break;
            case "mssql":
                sql = "SELECT COUNT(1) FROM " + sourceTable + " WITH(NOLOCK)";
                break;
            default:
                throw new UnsupportedOperationException("Unsupported database type: " + sourceDbType);
        }

        // 테이블의 전체 행수 확인
        try (Statement stmt = sourceConnection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next() && rs.getInt(1) > 0) {
                totalRows = rs.getInt(1);

                frm.AddLog(String.format("source table total rows : %d", totalRows), Color.BLACK);
            }
        } catch (SQLException e) {
            frm.AddLog(
                    String.format("Error truncating target table : %s\n%s", targetTable, e.getMessage()), Color.RED);
        }

        // 타겟 테이블 전체 자르기
        if (totalRows > 0 && (trunc_target_tb.toLowerCase().equals("truncate_or_delete")
                || trunc_target_tb.toLowerCase().equals("truncate"))) {

            frm.AddLog("truncating target : " + targetTable, Color.BLACK);
            try (Statement stmt = targetConnection.createStatement()) {
                stmt.executeUpdate("TRUNCATE TABLE " + targetTable);
            } catch (SQLException e) {
                if (onError.equals("resume_next")) {
                    frm.AddLog(e);
                    frm.AddLog(String.format("Skip truncating table %s.", sourceTable), Color.RED);
                } else {
                    throw e;
                }
            }
        }
        // 타겟 테이블 삭제
        if (totalRows > 0 && (trunc_target_tb.toLowerCase().equals("truncate_or_delete")
                || trunc_target_tb.toLowerCase().equals("delete"))) {
            deleteFromTargetTable(targetDbType, targetTable, rangeColumn, rangeType, range_from, range_to);
        }

        if (!migInProcess) {
            frm.AddLog(String.format("[%s] Migration stopped by user.", taskName), Color.BLUE);
            return;
        }

        // 이관 대상 조회
        List<Object> keyList = getKeyList(sourceDbType, sourceTable, rangeColumn, rangeType, range_from, range_to);
        int nTotal = 0;
        for (int keyIndex = 0; keyIndex < keyList.size(); keyIndex++) {
            if (!migInProcess) {
                frm.AddLog(String.format("%s Migration stopped by user.", taskName), Color.BLUE);
                break;
            }

            Object key = keyList.get(keyIndex);
            int nEffected = 0;
            nEffected = migrateTableByKey(sourceDbType, sourceTable, targetTable, rangeColumn, rangeType, key,
                    onError);
            nTotal += nEffected;
            frm.AddLog(String.format("%s , key:%s, inserted rows: %d, total: %d", targetTable, key.toString(),
                    nEffected, nTotal), Color.BLACK);
        }
    }

    public void deleteFromTargetTable(String dbType, String targetTable, String rangeColumn, String rangeType,
            String rangeFrom, String rangeTo) throws SQLException {
        String deleteSql = "DELETE FROM " + targetTable;

        if (rangeFrom != null && !rangeFrom.isEmpty()) {
            deleteSql += " WHERE " + rangeColumn + " >= ?";
        }
        if (rangeTo != null && !rangeTo.isEmpty()) {
            deleteSql += (rangeFrom != null && !rangeFrom.isEmpty() ? " AND " : " WHERE ") + rangeColumn + " <= ?";
        }

        try (PreparedStatement pstmt = targetConnection.prepareStatement(deleteSql)) {
            int paramIndex = 1;
            if (rangeFrom != null && !rangeFrom.isEmpty()) {
                setPreparedStatementParameter(pstmt, paramIndex++, rangeFrom, rangeType);
            }
            if (rangeTo != null && !rangeTo.isEmpty()) {
                setPreparedStatementParameter(pstmt, paramIndex++, rangeTo, rangeType);
            }

            int rowsDeleted = pstmt.executeUpdate();
            frm.AddLog(String.format("Deleted %d rows from target table %s", rowsDeleted, targetTable), Color.BLACK);
        } catch (SQLException e) {
            frm.AddLog(String.format("Error deleting from target table: %s", e.getMessage()), Color.RED);
            throw e;
        }
    }

    public int migrateTableByKey(String dbType, String sourceTable, String targetTable, String rangeColumn,
            String rangeType, Object key, String onError) throws SQLException {
        String selectQuery;
        StringBuilder whereClause = new StringBuilder();
        int nEffected = 0;

        if (rangeType.startsWith("DATE_TIME")) {
            String keyStr = key.toString();
            switch (rangeType) {
                case "DATE_TIME_Y":
                    whereClause.append(getYearFunction(dbType, rangeColumn)).append(" = ").append(keyStr);
                    break;
                case "DATE_TIME_M":
                    String[] ym = keyStr.split("-");
                    whereClause.append(getYearFunction(dbType, rangeColumn)).append(" = ").append(ym[0])
                            .append(" AND ").append(getMonthFunction(dbType, rangeColumn)).append(" = ").append(ym[1]);
                    break;
                case "DATE_TIME_D":
                    String[] ymd = keyStr.split("-");
                    whereClause.append(getYearFunction(dbType, rangeColumn)).append(" = ").append(ymd[0])
                            .append(" AND ").append(getMonthFunction(dbType, rangeColumn)).append(" = ").append(ymd[1])
                            .append(" AND ").append(getDayFunction(dbType, rangeColumn)).append(" = ").append(ymd[2]);
                    break;
                case "DATE_TIME_H":
                    String[] ymdh = keyStr.split(" ");
                    String[] ymdParts = ymdh[0].split("-");
                    whereClause.append(getYearFunction(dbType, rangeColumn)).append(" = ").append(ymdParts[0])
                            .append(" AND ").append(getMonthFunction(dbType, rangeColumn)).append(" = ")
                            .append(ymdParts[1])
                            .append(" AND ").append(getDayFunction(dbType, rangeColumn)).append(" = ")
                            .append(ymdParts[2])
                            .append(" AND ").append(getHourFunction(dbType, rangeColumn)).append(" = ")
                            .append(ymdh[1].split(":")[0]);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported range type: " + rangeType);
            }
        } else {
            whereClause.append(rangeColumn).append(" = ?");
        }

        switch (dbType.toLowerCase()) {
            case "mysql":
            case "postgresql":
            case "oracle":
                selectQuery = "SELECT * FROM " + sourceTable + " WHERE " + whereClause.toString();
                break;
            case "mssql":
                selectQuery = "SELECT * FROM " + sourceTable + " WITH(NOLOCK) WHERE " + whereClause.toString();
                break;
            default:
                throw new UnsupportedOperationException("Unsupported database type: " + dbType);
        }

        try (PreparedStatement pstmt = sourceConnection.prepareStatement(selectQuery)) {
            if (!rangeType.startsWith("DATE_TIME")) {
                pstmt.setObject(1, key);
            }

            // 자동 생성되는 컬럼을 식별하기 위해 데이터베이스 메타데이터를 사용
            Set<String> autoIncrementColumns = getAutoIncrementColumns(sourceTable);

            ResultSet rs = pstmt.executeQuery();
            ResultSetMetaData rsMetaData = rs.getMetaData();
            int columnCount = rsMetaData.getColumnCount();

            StringBuilder placeholders = new StringBuilder();
            StringBuilder columns = new StringBuilder();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = rsMetaData.getColumnName(i);
                if (autoIncrementColumns.contains(columnName)) {
                    continue; // 자동 생성되는 컬럼은 제외
                }
                columns.append(columnName).append(", ");
                placeholders.append("?, ");
            }

            // 마지막 쉼표와 공백 제거
            if (columns.length() > 0) {
                columns.setLength(columns.length() - 2);
            }
            if (placeholders.length() > 0) {
                placeholders.setLength(placeholders.length() - 2);
            }

            String insertQuery = "INSERT INTO " + targetTable + " (" + columns.toString() + ") VALUES ("
                    + placeholders.toString() + ")";
            try (PreparedStatement insertPstmt = targetConnection.prepareStatement(insertQuery)) {
                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        int columnType = rsMetaData.getColumnType(i);
                        switch (columnType) {
                            case java.sql.Types.INTEGER:
                                insertPstmt.setInt(i, rs.getInt(i));
                                break;
                            case java.sql.Types.VARCHAR:
                                insertPstmt.setString(i, rs.getString(i));
                                break;
                            case java.sql.Types.DATE:
                                insertPstmt.setDate(i, rs.getDate(i));
                                break;
                            case java.sql.Types.TIMESTAMP:
                                insertPstmt.setTimestamp(i, rs.getTimestamp(i));
                                break;
                            case java.sql.Types.DOUBLE:
                                insertPstmt.setDouble(i, rs.getDouble(i));
                                break;
                            case java.sql.Types.FLOAT:
                                insertPstmt.setFloat(i, rs.getFloat(i));
                                break;
                            case java.sql.Types.BOOLEAN:
                                insertPstmt.setBoolean(i, rs.getBoolean(i));
                                break;
                            // 필요한 다른 타입들 추가
                            default:
                                insertPstmt.setObject(i, rs.getObject(i));
                                break;
                        }
                    }
                    try {
                        insertPstmt.addBatch();
                        nEffected += insertPstmt.executeBatch().length;
                    } catch (SQLException e) {
                        if (isDuplicateKeyError(e)) {
                            frm.AddLog("Duplicate key error occurred for data: " + getRowData(rs, columnCount),
                                    Color.RED);
                        } else {
                            String errMsg = String.format("Error migrating table=%s, key=%s, message=%s", sourceTable,
                                    key.toString(),
                                    e.getMessage());
                            frm.AddLog(errMsg, Color.RED);
                            if (onError.equals("resume_next")) {
                                frm.AddLog(String.format("Moved to next key.", sourceTable), Color.BLUE);
                            } else {
                                throw e;
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            String errMsg = String.format("Error migrating table=%s, key=%s, message=%s", sourceTable, key.toString(),
                    e.getMessage());
            frm.AddLog(errMsg, Color.RED);
            if (onError.equals("resume_next")) {
                frm.AddLog(String.format("Moved to next key."), Color.BLUE);
            } else {
                throw e;
            }
        }
        return nEffected;
    }

    private Set<String> getAutoIncrementColumns(String tableName) throws SQLException {
        Set<String> autoIncrementColumns = new HashSet<>();
        DatabaseMetaData metaData = sourceConnection.getMetaData();
        try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                if ("YES".equals(rs.getString("IS_AUTOINCREMENT"))) {
                    autoIncrementColumns.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        return autoIncrementColumns;
    }

    private boolean isDuplicateKeyError(SQLException e) {
        // SQL 상태 코드와 SQL 오류 코드를 확인하여 중복 키 오류 여부를 판단
        String sqlState = e.getSQLState();
        int errorCode = e.getErrorCode();

        // MySQL
        if ("23000".equals(sqlState) && errorCode == 1062) {
            return true;
        }

        // PostgreSQL
        if ("23505".equals(sqlState)) {
            return true;
        }

        // SQL Server
        if ("23000".equals(sqlState) && errorCode == 2627) {
            return true;
        }

        // Oracle
        if ("23000".equals(sqlState) && errorCode == 1) {
            return true;
        }

        return false;
    }

    private String getRowData(ResultSet rs, int columnCount) throws SQLException {
        StringBuilder rowData = new StringBuilder();
        for (int i = 1; i <= columnCount; i++) {
            rowData.append(rs.getObject(i)).append(", ");
        }
        return rowData.toString();
    }

    private String getYearFunction(String dbType, String rangeColumn) {
        switch (dbType.toLowerCase()) {
            case "mysql":
            case "postgresql":
                return "EXTRACT(YEAR FROM " + rangeColumn + ")";
            case "mssql":
                return "YEAR(" + rangeColumn + ")";
            case "oracle":
                return "TO_CHAR(" + rangeColumn + ", 'YYYY')";
            default:
                throw new UnsupportedOperationException("Unsupported database type: " + dbType);
        }
    }

    private String getMonthFunction(String dbType, String rangeColumn) {
        switch (dbType.toLowerCase()) {
            case "mysql":
            case "postgresql":
                return "EXTRACT(MONTH FROM " + rangeColumn + ")";
            case "mssql":
                return "MONTH(" + rangeColumn + ")";
            case "oracle":
                return "TO_CHAR(" + rangeColumn + ", 'MM')";
            default:
                throw new UnsupportedOperationException("Unsupported database type: " + dbType);
        }
    }

    private String getDayFunction(String dbType, String rangeColumn) {
        switch (dbType.toLowerCase()) {
            case "mysql":
            case "postgresql":
                return "EXTRACT(DAY FROM " + rangeColumn + ")";
            case "mssql":
                return "DAY(" + rangeColumn + ")";
            case "oracle":
                return "TO_CHAR(" + rangeColumn + ", 'DD')";
            default:
                throw new UnsupportedOperationException("Unsupported database type: " + dbType);
        }
    }

    private String getHourFunction(String dbType, String rangeColumn) {
        switch (dbType.toLowerCase()) {
            case "mysql":
            case "postgresql":
                return "EXTRACT(HOUR FROM " + rangeColumn + ")";
            case "mssql":
                return "DATEPART(HOUR, " + rangeColumn + ")";
            case "oracle":
                return "TO_CHAR(" + rangeColumn + ", 'HH24')";
            default:
                throw new UnsupportedOperationException("Unsupported database type: " + dbType);
        }
    }

    public List<Object> getKeyList(String dbType, String sourceTable, String rangeColumn, String rangeType,
            String range_from, String range_to) throws SQLException {
        List<Object> ret = new ArrayList<>();
        String sql = "";

        switch (rangeType) {
            case "DATE_TIME_Y":
                sql = getYearQuery(dbType, rangeColumn, sourceTable);
                break;
            case "DATE_TIME_M":
                sql = getYearMonthQuery(dbType, rangeColumn, sourceTable);
                break;
            case "DATE_TIME_D":
                sql = getYearMonthDayQuery(dbType, rangeColumn, sourceTable);
                break;
            case "DATE_TIME_H":
                sql = getYearMonthDayHourQuery(dbType, rangeColumn, sourceTable);
                break;
            case "TEXT":
            case "NUMBER":
                sql = "SELECT DISTINCT " + rangeColumn + " FROM " + sourceTable;
                break;
            default:
                throw new IllegalArgumentException("Unsupported range type: " + rangeType);
        }

        if (range_from != null && !range_from.isEmpty()) {
            sql += " WHERE " + rangeColumn + " >= ?";
        }
        if (range_to != null && !range_to.isEmpty()) {
            sql += (range_from != null && !range_from.isEmpty() ? " AND " : " WHERE ") + rangeColumn + " < ?";
        }

        try (PreparedStatement pstmt = sourceConnection.prepareStatement(sql)) {
            int paramIndex = 1;
            if (range_from != null && !range_from.isEmpty()) {
                setPreparedStatementParameter(pstmt, paramIndex++, range_from, rangeType);
            }
            if (range_to != null && !range_to.isEmpty()) {
                setPreparedStatementParameter(pstmt, paramIndex++, range_to, rangeType);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    if (rangeType.equals("DATE_TIME_Y")) {
                        ret.add(rs.getString(1).toString());
                    } else if (rangeType.equals("DATE_TIME_M")) {
                        ret.add(rs.getString(1).toString() + "-" + rs.getString(2).toString());
                    } else if (rangeType.equals("DATE_TIME_D")) {
                        ret.add(rs.getString(1).toString() + "-" + rs.getString(2).toString() + "-"
                                + rs.getString(3).toString());
                    } else if (rangeType.equals("DATE_TIME_H")) {
                        ret.add(rs.getString(1).toString() + "-" + rs.getString(2).toString() + "-"
                                + rs.getString(3).toString() + " " + rs.getString(4).toString());
                    } else if (rangeType.equals("TEXT")) {
                        ret.add(rs.getString(1));
                    } else if (rangeType.equals("NUMBER")) {
                        ret.add(rs.getLong(1));
                    }
                }
            }
        } catch (SQLException e) {
            frm.AddLog(String.format("Error getting key list from source table: %s", e.getMessage()), Color.RED);
        }

        ret.sort((o1, o2) -> {
            if (o1 instanceof Comparable && o2 instanceof Comparable) {
                if (o1 instanceof Comparable && o2 instanceof Comparable) {
                    return compareObjects(o1, o2);
                } else {
                    throw new IllegalArgumentException("Objects are not comparable");
                }
            } else {
                throw new IllegalArgumentException("Objects are not comparable");
            }
        });
        Collections.reverse(ret);
        return ret;
    }

    private void setPreparedStatementParameter(PreparedStatement pstmt, int paramIndex, String value, String rangeType)
            throws SQLException {

        DateTimeFormatter parseFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        switch (rangeType) {
            case "DATE_TIME_Y":
                LocalDateTime yearDateTime = LocalDateTime.parse(value + "-01-01 00:00:00", parseFormatter);
                pstmt.setTimestamp(paramIndex, Timestamp.valueOf(yearDateTime));
                break;
            case "DATE_TIME_M":
                LocalDateTime monthDateTime = LocalDateTime.parse(value + "-01 00:00:00", parseFormatter);
                pstmt.setTimestamp(paramIndex, Timestamp.valueOf(monthDateTime));
                break;
            case "DATE_TIME_D":
                LocalDateTime dayDateTime = LocalDateTime.parse(value + " 00:00:00", parseFormatter);
                pstmt.setTimestamp(paramIndex, Timestamp.valueOf(dayDateTime));
                break;
            case "DATE_TIME_H":
                LocalDateTime hourDateTime = LocalDateTime.parse(value + ":00:00", parseFormatter);
                pstmt.setTimestamp(paramIndex, Timestamp.valueOf(hourDateTime));
                break;
            case "TEXT":
                pstmt.setString(paramIndex, value);
                break;
            case "NUMBER":
                pstmt.setLong(paramIndex, Long.parseLong(value));
                break;
            default:
                throw new IllegalArgumentException("Unsupported range type: " + rangeType);
        }
    }

    private String getYearQuery(String dbType, String rangeColumn, String sourceTable) {
        switch (dbType.toLowerCase()) {
            case "mysql":
            case "postgresql":
                return "SELECT DISTINCT EXTRACT(YEAR FROM " + rangeColumn + ") FROM " + sourceTable;
            case "mssql":
                return "SELECT DISTINCT YEAR(" + rangeColumn + ") FROM " + sourceTable + " WITH(NOLOCK)";
            case "oracle":
                return "SELECT DISTINCT TO_CHAR(" + rangeColumn + ", 'YYYY') FROM " + sourceTable;
            default:
                throw new UnsupportedOperationException("Unsupported database type: " + dbType);
        }
    }

    private String getYearMonthQuery(String dbType, String rangeColumn, String sourceTable) {
        switch (dbType.toLowerCase()) {
            case "mysql":
            case "postgresql":
                return "SELECT DISTINCT EXTRACT(YEAR FROM " + rangeColumn + "), EXTRACT(MONTH FROM " + rangeColumn
                        + ") FROM " + sourceTable;
            case "mssql":
                return "SELECT DISTINCT YEAR(" + rangeColumn + "), MONTH(" + rangeColumn + ") FROM " + sourceTable
                        + " WITH(NOLOCK)";
            case "oracle":
                return "SELECT DISTINCT TO_CHAR(" + rangeColumn + ", 'YYYY'), TO_CHAR(" + rangeColumn + ", 'MM') FROM "
                        + sourceTable;
            default:
                throw new UnsupportedOperationException("Unsupported database type: " + dbType);
        }
    }

    private String getYearMonthDayQuery(String dbType, String rangeColumn, String sourceTable) {
        switch (dbType.toLowerCase()) {
            case "mysql":
            case "postgresql":
                return "SELECT DISTINCT EXTRACT(YEAR FROM " + rangeColumn + "), EXTRACT(MONTH FROM " + rangeColumn
                        + "), EXTRACT(DAY FROM " + rangeColumn + ") FROM " + sourceTable;
            case "mssql":
                return "SELECT DISTINCT YEAR(" + rangeColumn + "), MONTH(" + rangeColumn + "), DAY(" + rangeColumn
                        + ") FROM " + sourceTable + " WITH(NOLOCK)";
            case "oracle":
                return "SELECT DISTINCT TO_CHAR(" + rangeColumn + ", 'YYYY'), TO_CHAR(" + rangeColumn
                        + ", 'MM'), TO_CHAR(" + rangeColumn + ", 'DD') FROM " + sourceTable;
            default:
                throw new UnsupportedOperationException("Unsupported database type: " + dbType);
        }
    }

    private String getYearMonthDayHourQuery(String dbType, String rangeColumn, String sourceTable) {
        switch (dbType.toLowerCase()) {
            case "mysql":
            case "postgresql":
                return "SELECT DISTINCT EXTRACT(YEAR FROM " + rangeColumn + "), EXTRACT(MONTH FROM " + rangeColumn
                        + "), EXTRACT(DAY FROM " + rangeColumn + "), EXTRACT(HOUR FROM " + rangeColumn + ") FROM "
                        + sourceTable;
            case "mssql":
                return "SELECT DISTINCT YEAR(" + rangeColumn + "), MONTH(" + rangeColumn + "), DAY(" + rangeColumn
                        + "), DATEPART(HOUR, " + rangeColumn + ") FROM " + sourceTable + " WITH(NOLOCK)";
            case "oracle":
                return "SELECT DISTINCT TO_CHAR(" + rangeColumn + ", 'YYYY'), TO_CHAR(" + rangeColumn
                        + ", 'MM'), TO_CHAR(" + rangeColumn + ", 'DD'), TO_CHAR(" + rangeColumn + ", 'HH24') FROM "
                        + sourceTable;
            default:
                throw new UnsupportedOperationException("Unsupported database type: " + dbType);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> int compareObjects(Object o1, Object o2) {
        return ((Comparable<T>) o1).compareTo((T) o2);
    }

    public void closeConnections() throws SQLException {
        if (sourceConnection != null)
            sourceConnection.close();
        if (targetConnection != null)
            targetConnection.close();
    }

    public void stopMigration() {
        this.migInProcess = false;
    }

    public static String getFullErrorMessage(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
