import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/*
    This program is written for Senior Design Project named
    "Development of Large-scale Web Crawling Platform" by
    ADA University students Nigar Safarova, Nargiz Tahmazli,
    Parvin Hajili, Shola Gulmaliyeva.

    Copyright 2021 Crawly.
 */

public class WebCrawler implements Runnable {
    private static final int MAX_DEPTH = 3;
    private final Thread thread;
    private final String first_link;
    private final ArrayList<String> visitedLinks = new ArrayList<String>();
    private final long ID;
    String connectionUrl = "jdbc:postgresql://localhost:5432/webcrawler";
    java.sql.Connection conn = null;

    public WebCrawler(String link, int num) throws SQLException {
        System.out.println();
        first_link = link;
        ID = num;

        thread = new Thread(this);
        thread.start();

        }

    @Override
    public void run() {

        // Establishing connection with PostgreSQL database.
        try {
            conn = DriverManager.getConnection(connectionUrl, "postgres", "");
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        // Passing URL to crawl() method.
        crawl(first_link);

        // If crawl() method finalizes successfully, save URL as crawled in the repository.
        try {
            saveAsCrawled(first_link);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        // Closing connection with database.
        try {
            conn.close();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        // Getting a new URL from repository after current thread is done with crawling.
        ThreadCrawlers anotherUrl = new ThreadCrawlers();
        String newUrl = anotherUrl.getNewUrl();
        if(newUrl != null){
            try {
                new WebCrawler(newUrl, (int) ID);
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }

    }

    // Setting PreparedStatement to update a URL in the repository as crawled.
    private void saveAsCrawled(String url) throws SQLException {
        String updateQuery = "UPDATE repository SET is_crawled=? WHERE seed_url=?";
        PreparedStatement updateStmt = conn.prepareStatement(updateQuery);
        updateStmt.setBoolean(1, true);
        updateStmt.setString(2, url);
        updateStmt.executeUpdate();
    }

    private void crawl(String url) {
            try {
                /*
                    Getting previous crawled content of a URL if crawling already started before.
                 */
                boolean newLink = true;
                String query = "SELECT * FROM records where url LIKE ?";
                PreparedStatement pstmt = conn.prepareStatement(query);
                pstmt.setString(1,"%"+url+"%");
                ResultSet rs = pstmt.executeQuery();
                /*
                    Printing previously crawled content;
                    Marking previously crawled links as visited so that they do not get crawled again.
                 */
                if (rs.next() != false) {
                    newLink = false;
                    System.out.println("Found previous crawl records of '" + url +"'. Printing them...");
                    do {
                        printCrawler(ID, rs.getString("url"), rs.getString("website_title"), rs.getString("crawled_text"), rs.getString("record_date"));
                        visitedLinks.add(rs.getString("url"));
                    }
                    while(rs.next());
                }

                /*
                    Parsing robots.txt of websites and obeying those rules.
                 */
                String USER_AGENT = "*";
                URL urlObj = new URL(url);
                String hostId = urlObj.getProtocol() + "://" + urlObj.getHost()
                        + (urlObj.getPort() > -1 ? ":" + urlObj.getPort() : "" + "/robots.txt");
                Map<String, BaseRobotRules> robotsTxtRules = new HashMap<String, BaseRobotRules>();
                BaseRobotRules rules;
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(hostId))
                        .build();

                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) {
                    rules = new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);
                } else {
                    SimpleRobotRulesParser robotParser = new SimpleRobotRulesParser();
                    rules = robotParser.parseContent(hostId, response.body().getBytes(StandardCharsets.UTF_8),
                            "text/plain", USER_AGENT);
                }

                robotsTxtRules.put(hostId, rules);
                boolean urlAllowed = rules.isAllowed(url);

                int level = 1;
                long urlCrawlDelay;
                /*
                    Adding crawl delay if defined by website in robots.txt file.
                    Otherwise, adding 2 seconds of niceness delay to each thread.
                 */
                if(rules.getCrawlDelay() > 2) {
                    urlCrawlDelay = rules.getCrawlDelay();
                } else {
                    urlCrawlDelay = 2000;
                }

                // Start crawling if rules in the robots.txt file allow to.
                if(urlAllowed) {
                    request(level, url, newLink, urlCrawlDelay);
                } else{
                    System.out.println(url + " does not allow crawling their content.");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    /*
        Printing Crawler ID and crawled content.
     */
    private void printCrawler(Long id, String url, String title, String text, String date) {
        System.out.println("\n**Crawler " + id + ": Received webpage at " + url);
        System.out.println(title);
        System.out.println(text); // text extracting
        System.out.println(date);

        /*
            Save the crawled data as CSV file into the project folder.
         */
        try {

            CopyManager cm = new CopyManager((BaseConnection) conn);

            String fileName = "src/main/resources/records.csv";

            try (FileOutputStream fos = new FileOutputStream(fileName);
                 OutputStreamWriter osw = new OutputStreamWriter(fos,
                         StandardCharsets.UTF_8)) {

                cm.copyOut("COPY records (url, website_title, crawled_text, record_date, crawled_text_size, url_depth) TO STDOUT WITH CSV HEADER", osw);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    private void request(int level, String url, boolean isNewLink, long crawlDelay) {
        try {
                // Establishing Jsoup connection to parse the HTML of a website.
                Connection con = Jsoup.connect(url).ignoreContentType(true);
                Document doc = con.get();

            /*
                If crawled record is not crawled before, save it into the database.
             */
            if(isNewLink) {
                if (con.response().statusCode() == 200) {
                    String title = doc.title();
                    String text = doc.body().text();

                    String crawlTime = new Date().toString();
                    printCrawler(ID, url, title, text, crawlTime);
                    visitedLinks.add(url);
                    sleep(crawlDelay); // crawl delay obtained from robots.txt

                    String query = "INSERT INTO records (url, website_title, crawled_text, record_date, crawled_text_size, url_depth) VALUES(?,?,?,?,?,?)";
                    PreparedStatement pstmt = conn.prepareStatement(query);
                    pstmt.setString(1, url);
                    pstmt.setString(2, title);
                    pstmt.setString(3, text);
                    pstmt.setString(4, crawlTime);
                    pstmt.setLong(5, text.length());
                    pstmt.setLong(6, level);

                    pstmt.executeUpdate();
                }
            }

            /*
                If the depth of URL is less than or equal to maximum allowed depth length,
                get next link. If next link is not crawled before, start crawling.
             */
                if (level <= MAX_DEPTH) {
                    for (Element link : doc.select("a[href]")) {
                        String next_link = link.absUrl("href");
                        if(next_link.length()>0) {
                            if (!visitedLinks.contains(next_link)) {
                                request(level++, next_link, true, crawlDelay);
                            }
                        }
                    }
                }
        }

            catch (Exception e) {
                e.printStackTrace();
            }
    }

    // Method for returning threads to join them.
    public Thread getThread() {
        return thread;
    }

    // Method for making threads sleep for the given period.
    protected void sleep(long milliSeconds) {
        try {
            Thread.sleep(milliSeconds);
        }
        catch (InterruptedException e) {
            e.getMessage();
        }
    }


}
