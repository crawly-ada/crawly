import java.sql.*;
import java.util.Date;
import java.util.List;

/*
    This program is written for Senior Design Project named
    "Development of Large-scale Web Crawling Platform" by
    ADA University students Nigar Safarova, Nargiz Tahmazli,
    Parvin Hajili, Shola Gulmaliyeva.

    Copyright 2021 Crawly.
 */

public class addToRepository {

    /*
        Saving URLs into the repository, if they do not already exist.
     */

    public addToRepository(List<String> url) throws SQLException {
        seedURL(url);
    }

    String connectionUrl = "jdbc:postgresql://localhost:5432/webcrawler";
    Connection conn = DriverManager.getConnection(connectionUrl, "postgres", "");

    private void seedURL(List<String> url) throws SQLException {

        for (String s : url) {

            String query = "SELECT * FROM repository WHERE seed_url = ? LIMIT 1";
            PreparedStatement pstmt = conn.prepareStatement(query);
            pstmt.setString(1, s);
            ResultSet rs = pstmt.executeQuery();
            if(!rs.next()){
                String query2 = "INSERT INTO repository (seed_url, is_crawled, date_and_time) VALUES(?,?,?)";
                PreparedStatement pstmt2 = conn.prepareStatement(query2);
                pstmt2.setString(1, s);
                pstmt2.setBoolean(2, false);
                pstmt2.setString(3, new Date().toString());
                pstmt2.executeUpdate();
            } else{
                System.out.println("'" + s + "' is already in the repository. Crawling...");
            }
        }

        conn.close();
    }
}
