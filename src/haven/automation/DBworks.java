package haven.automation;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DBworks {
    private Connection conn;

    public DBworks (){
        this.conn = this.connect();
    }
    private Connection connect() {
        // SQLite connection string
        try {
            Class.forName("org.sqlite.JDBC");
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
        String url = "jdbc:sqlite:salem.db";
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return conn;
    }
    public void insert_resource(String name) {
        String sql = "INSERT OR IGNORE INTO resources(resource_name) VALUES(?)";

        try (PreparedStatement pstmt = this.conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
}
