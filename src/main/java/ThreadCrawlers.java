import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/*
    This program is written for Senior Design Project named
    "Development of Large-scale Web Crawling Platform" by
    ADA University students Nigar Safarova, Nargiz Tahmazli,
    Parvin Hajili, Shola Gulmaliyeva.

    Copyright 2021 Crawly.
 */

public class ThreadCrawlers {
    private static List<String> links = new ArrayList<>();
    private static int numberOfThreads = 10;
    private static int lastLinkIndex = numberOfThreads - 1;

    public static void main(String[] args) throws SQLException {
        Scanner sc = new Scanner(System.in);
        List<String> urlList = new ArrayList<>();
        System.out.print("Enter the number of URLs to add to the repository (Type 0 if you don't want to add a new URL): ");

        /*
            Scanning number of URLs that user wants to insert.
         */
        int numberOfCrawlers = sc.nextInt();
        System.out.print("Enter the URls (e.g. url1 url2 url3): ");

        // Scanning the URLs from console
        for (int i = 0; i < numberOfCrawlers; i++) {
            urlList.add(sc.next());
        }

        // Save inserted URLs into the repository.
        new addToRepository(urlList);


        links = getUrlsFromRepository();

        ArrayList<WebCrawler> crawlers = new ArrayList<>();
        if(links.size()<numberOfThreads) numberOfThreads = links.size();
        /*
            If number of URLs in the repository is more than
            the maximum allowed number of threads, start crawling
            as much as the max. allowed number of threads until
            a thread is done crawling.
         */
        for (int i = 1; i <= numberOfThreads; i++) {
            crawlers.add(new WebCrawler(links.get(i-1), i));
        }

        /*
            Calls the method getThread() from WebCrawler.class and joins them.
         */
        for (WebCrawler w : crawlers) {
            try {
                w.getThread().join();
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /*
        Getting new URL from repository when a thread is idle.
     */
    public String getNewUrl() {
        if(links.size() > numberOfThreads){
            lastLinkIndex++;
            return links.get(lastLinkIndex);
        }
        return null;
    }

    /*
        Get all URLs from repository.
     */
    private static List<String> getUrlsFromRepository() throws SQLException {
        String connectionUrl = "jdbc:postgresql://localhost:5432/webcrawler";
        Connection conn = DriverManager.getConnection(connectionUrl, "postgres", "");
        String query = "SELECT * FROM repository WHERE is_crawled = ?";
        PreparedStatement pstmt = conn.prepareStatement(query);
        pstmt.setBoolean(1, false);
        ResultSet rs = pstmt.executeQuery();
        ArrayList <String> urls = new ArrayList<String>();
        while(rs.next()){
            urls.add(rs.getString("seed_url"));
        }
        conn.close();

        return urls;
    }

}
