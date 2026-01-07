package tetris;

import java.sql.*;

public class DatabaseManager {
    private static final String URL = "jdbc:sqlite:tetris.db";
    private static DatabaseManager instance;

    private DatabaseManager() {
        init();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private void init() {
        try (Connection conn = DriverManager.getConnection(URL);
                Statement stmt = conn.createStatement()) {
            // Ensure table exists first
            String sql = "CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "nickname TEXT UNIQUE NOT NULL," +
                    "password TEXT NOT NULL" +
                    ");";
            stmt.execute(sql);

            // Add missing columns if they don't exist (Migration)
            String[] settingsColumns = {
                    "das INTEGER DEFAULT 175",
                    "arr INTEGER DEFAULT 50",
                    "sdf REAL DEFAULT 22.5",
                    "ghost INTEGER DEFAULT 1",
                    "sound INTEGER DEFAULT 1",
                    "board_width INTEGER DEFAULT 10",
                    "board_height INTEGER DEFAULT 20"
            };

            for (String colDef : settingsColumns) {
                String colName = colDef.split(" ")[0];
                if (!columnExists(conn, "users", colName)) {
                    stmt.execute("ALTER TABLE users ADD COLUMN " + colDef + ";");
                    System.out.println("[DB] Added missing column: " + colName);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    public boolean register(String nickname, String password) {
        String sql = "INSERT INTO users(nickname, password) VALUES(?, ?)";
        try (Connection conn = DriverManager.getConnection(URL);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nickname);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean login(String nickname, String password) {
        String sql = "SELECT * FROM users WHERE nickname = ? AND password = ?";
        try (Connection conn = DriverManager.getConnection(URL);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nickname);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public void updateSettings(String nickname, long das, long arr, double sdf, boolean ghost, boolean sound, int width,
            int height) {
        String sql = "UPDATE users SET das=?, arr=?, sdf=?, ghost=?, sound=?, board_width=?, board_height=? WHERE nickname=?";
        try (Connection conn = DriverManager.getConnection(URL);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, das);
            pstmt.setLong(2, arr);
            pstmt.setDouble(3, sdf);
            pstmt.setInt(4, ghost ? 1 : 0);
            pstmt.setInt(5, sound ? 1 : 0);
            pstmt.setInt(6, width);
            pstmt.setInt(7, height);
            pstmt.setString(8, nickname);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loadSettingsToManager(String nickname, SettingsManager sm) {
        String sql = "SELECT * FROM users WHERE nickname = ?";
        try (Connection conn = DriverManager.getConnection(URL);
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nickname);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                sm.setDas(rs.getLong("das"));
                sm.setArr(rs.getLong("arr"));
                sm.setSdf(rs.getDouble("sdf"));
                sm.setGhostEnabled(rs.getInt("ghost") == 1);
                sm.setSoundEnabled(rs.getInt("sound") == 1);
                sm.setBoardWidth(rs.getInt("board_width"));
                sm.setBoardHeight(rs.getInt("board_height"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
