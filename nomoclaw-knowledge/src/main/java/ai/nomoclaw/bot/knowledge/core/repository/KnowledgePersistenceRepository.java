package ai.nomoclaw.bot.knowledge.core.repository;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Repository-scoped SQL access for knowledge aggregate workflows.
 */
@Repository
public class KnowledgePersistenceRepository {
    private final DataSource dataSource;

    public KnowledgePersistenceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void execute(String sql) {
        Connection connection = connection();
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException ex) {
            throw dataAccessFailure(ex);
        } finally {
            release(connection);
        }
    }

    public int update(String sql, Object... args) {
        Connection connection = connection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw dataAccessFailure(ex);
        } finally {
            release(connection);
        }
    }

    public <T> List<T> query(String sql, SqlRowMapper<T> mapper, Object... args) {
        Connection connection = connection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> values = new ArrayList<>();
                int row = 0;
                while (resultSet.next()) {
                    values.add(mapper.mapRow(resultSet, row++));
                }
                return values;
            }
        } catch (SQLException ex) {
            throw dataAccessFailure(ex);
        } finally {
            release(connection);
        }
    }

    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return query(sql, (resultSet, row) -> map(resultSet), args);
    }

    public <T> List<T> queryForList(String sql, Class<T> requiredType, Object... args) {
        return query(sql, (resultSet, row) -> convert(resultSet.getObject(1), requiredType), args);
    }

    public Map<String, Object> queryForMap(String sql, Object... args) {
        List<Map<String, Object>> values = queryForList(sql, args);
        if (values.isEmpty()) {
            throw new DataRetrievalFailureException("Expected one row but found none");
        }
        return values.get(0);
    }

    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
        List<T> values = queryForList(sql, requiredType, args);
        return values.isEmpty() ? null : values.get(0);
    }

    private Connection connection() {
        try {
            Object holder = TransactionSynchronizationManager.getResource(dataSource);
            if (holder != null) {
                Method method = holder.getClass().getMethod("getConnection");
                return (Connection) method.invoke(holder);
            }
            return dataSource.getConnection();
        } catch (Exception ex) {
            throw new DataAccessResourceFailureException("Unable to obtain knowledge database connection", ex);
        }
    }

    private void release(Connection connection) {
        try {
            if (TransactionSynchronizationManager.hasResource(dataSource)) return;
            connection.close();
        } catch (SQLException ex) {
            throw dataAccessFailure(ex);
        }
    }

    private void bind(PreparedStatement statement, Object... args) throws SQLException {
        if (args == null) return;
        for (int index = 0; index < args.length; index++) {
            Object value = args[index];
            if (value instanceof LocalDateTime localDateTime) {
                statement.setTimestamp(index + 1, Timestamp.valueOf(localDateTime));
            } else {
                statement.setObject(index + 1, value);
            }
        }
    }

    private Map<String, Object> map(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        Map<String, Object> value = new LinkedHashMap<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            String label = metadata.getColumnLabel(index);
            value.put(label, resultSet.getObject(index));
            value.put(label.toLowerCase(Locale.ROOT), resultSet.getObject(index));
        }
        return value;
    }

    private RuntimeException dataAccessFailure(SQLException exception) {
        if (exception instanceof SQLIntegrityConstraintViolationException) {
            return new DuplicateKeyException("Knowledge database constraint violation", exception);
        }
        return new DataAccessResourceFailureException("Knowledge database operation failed", exception);
    }

    private <T> T convert(Object value, Class<T> requiredType) {
        if (value == null) return null;
        if (requiredType.isInstance(value)) return requiredType.cast(value);
        if (requiredType == String.class) return requiredType.cast(String.valueOf(value));
        if (requiredType == Integer.class && value instanceof Number number) return requiredType.cast(number.intValue());
        if (requiredType == Long.class && value instanceof Number number) return requiredType.cast(number.longValue());
        if (requiredType == Double.class && value instanceof Number number) return requiredType.cast(number.doubleValue());
        if (requiredType == Boolean.class && value instanceof Number number) return requiredType.cast(number.intValue() != 0);
        if (requiredType == LocalDateTime.class && value instanceof Timestamp timestamp) {
            return requiredType.cast(timestamp.toLocalDateTime());
        }
        return requiredType.cast(value);
    }

    /**
     * Maps a SQL row to a domain or projection object.
     */
    @FunctionalInterface
    public interface SqlRowMapper<T> {
        T mapRow(ResultSet resultSet, int rowNumber) throws SQLException;
    }
}
