ppackage gov.osti.elink.servlet;

import gov.osti.framework.dbi.Dbi;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
//import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * Processes the submitted form data and returns the submitted data if successful
 * Created by sheelyb on 11/26/2014.
 */
public class ProcessSubmitForm extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = Logger.getLogger(ProcessSubmitForm.class.getName());
    private int ostiId;                                                             //Required
    private char releaseFlag;
    private String[] award_ids;                                                     //Required
    private String journal;                                                         //Required
    private String volume;
    private String issue;
    private String doi;
    private String title;                                                           //Required
    private final TreeSet<Author> authors = new TreeSet<>(new AuthorComparator());  //Required
    private Date publicationDate;                                                   //Required (if publicationYearTP is null)
    private String publicationYearTP;                                               //Required (if publicationDate is null)
    private String languages;
    private String countryCode;
    private String sponsors;                                                        //Required
    private String urls;                                                            //Required
    private String availability;                                                    //Required
    private String description;
    private JSONObject json;
    
    class Author {
        private String firstName;
        private String middleName;
        private String lastName;
        private String orcId;
        private int position;
    }
    
    private class AuthorComparator implements Comparator<Author> {
        @Override
        public int compare(Author author1, Author author2) {
            if (author1.position > author2.position)
                return 1;
            else
                return -1;
        }
    }
    
    /**
     * Handles the HTTP <code>POST</code> method. Client posts JSON
     *
     * @param request  servlet request
     * @param response servlet response
     */
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html");
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        try {
            BufferedReader reader = request.getReader();
            while ((line = reader.readLine()) != null)
                stringBuilder.append(line);
        } catch (Exception e) {
            log.severe(e.getMessage());
            response.sendError(500, "Exception: " + e.getMessage());
            return;
        }
        json = new JSONObject(stringBuilder.toString());
        // JSON contains keys with empty values
        if (!json.has("ostiid") || json.getString("ostiid").isEmpty() ||
            !json.has("awardids") || json.getString("awardids").isEmpty() ||
            !json.has("journalname") || json.getString("journalname").isEmpty() ||
            !json.has("title") || json.getString("title").isEmpty() ||
            ((!json.has("allAuthorsString") || json.getString("allAuthorsString").isEmpty()) && !json.has("authors")) ||
            ((!json.has("pubtext") || json.getString("pubtext").isEmpty()) &&
             (!json.has("altPubDtTxt") || json.getString("altPubDtTxt").isEmpty())) ||
            !json.has("sponsor") || json.getString("sponsor").isEmpty() ||
            !json.has("publisher") || json.getString("publisher").isEmpty() ||
            !json.has("availability") || json.getString("availability").isEmpty()) {
            response.sendError(206, "JSON missing required content");
            return;
        }
        // OSTI ID
        ostiId = Integer.parseInt(json.getString("ostiid"));
        // Award IDs
        award_ids = json.getString("awardids").split(";");
        // Journal Name
        journal = json.getString("journalname");
        // Volume
        volume = json.has("volume") && !json.getString("volume").isEmpty() ? json.getString("volume") : null;
        // Issue
        issue = json.has("issue") && !json.getString("issue").isEmpty() ? json.getString("issue") : null;
        // DOI
        doi = json.has("doi") && !json.getString("doi").isEmpty() ? json.getString("doi") : null;
        // Title
        title = json.getString("title");
        // Author(s)
        if (json.has("allAuthorsString") && !json.getString("allAuthorsString").isEmpty()) {
            JSONArray jsonArray = new JSONArray();
            String[] authorsArray = json.getString("allAuthorsString").split(";");
            int position = 1;
            for (String temp : authorsArray) {
                // NOTE names are in the form "last, first middle"
                String[] nameParts = temp.split(",");
                String[] firstMiddle = nameParts[1].trim().split(" ");
                Author author = new Author();
                author.lastName = nameParts[0].trim();
                author.firstName = firstMiddle[0];
                author.middleName = (firstMiddle.length > 1) ? firstMiddle[1] : null;
                author.position = position;
                author.orcId = null;
                authors.add(author);
                JSONObject obj = new JSONObject();
                obj.put("lastName", author.lastName);
                obj.put("firstName", author.firstName);
                obj.put("middleName", author.middleName);
                obj.put("position", author.position);
                obj.put("orcid", author.orcId);
                jsonArray.put(obj);
                ++position;
            }
            json.put("authors", jsonArray);
        } else {
            // No assumption that authors are in 'correct' order in JSON
            JSONArray array = json.getJSONArray("authors");
            int size = array.length();
            for (int i = 0; i < size; ++i) {
                JSONObject obj = array.getJSONObject(i);
                Author author = new Author();
                author.lastName = obj.getString("lastName");
                author.firstName = obj.getString("firstName");
                author.middleName = obj.has("middleName") && !obj.getString("middleName").isEmpty() ? obj.getString("middleName") : null;
                author.position = Integer.valueOf(obj.getString("position"));
                author.orcId = obj.has("orcid") && !obj.getString("orcid").isEmpty() ? obj.getString("orcid") : null;
                authors.add(author);
            }
        }
        // Publication Date
        if (json.has("pubtext") && !json.getString("pubtext").isEmpty()) {
            try {
                publicationDate = new SimpleDateFormat("MM/dd/yyyy").parse(json.getString("pubtext"));
            } catch (ParseException e) {
                log.severe("Publication Date Parse Exception: " + e.getMessage());
                response.sendError(500, "Exception: " + e.getMessage());
                return;
            }
        } else {
            publicationYearTP = json.getString("altPubDtTxt");
        }
        // Language(s)
        languages = json.has("language") && !json.getString("language").isEmpty() ? json.getString("language") : null;
        // Country Code
        countryCode = json.has("country_list") && !json.getString("country_list").isEmpty() ? json.getString("country_list") : null;
        // Sponsor(s)
        sponsors = json.getString("sponsor");
        // URLs to full text
        urls = json.getString("publisher");
        // Availability
        availability = json.getString("availability");
        // Description/Abstract
        description = json.has("description") && !json.getString("description").isEmpty() ? json.getString("description") : null;
        processRequest(response);
    }
    
    /**
     * Returns a short description of the servlet.
     */
    public String getServletInfo() {
        return "Process submitted form data";
    }
    
    private void processRequest(HttpServletResponse response) throws IOException {
        PreparedStatement statement = null;
        Connection connection = null;
        try {
            connection = Dbi.getConnection();
            String sql;
            if (ostiId != 0) {
                sql = "update nsf.metadata set journal_name = ?, journal_volume = ?, journal_issue = ?, doi = ?, " +
                "title = ?, publication_date = ?, publication_date_text = ?, language = ?, " +
                "country_publication_code = ?, sponsor_org = ?, availability = ?, description = ? " +
                "where osti_id = " + ostiId;
            } else {
                sql = "insert into nsf.metadata (osti_id, release_flag, date_osti_id_assigned, source_input_type, " +
                "journal_name, journal_volume, journal_issue, doi, title, publication_date, " +
                "publication_date_text, language, country_publication_code, sponsor_org, availability, description) " +
                "values (nextval('osti_id_seq'), 'N', localtimestamp, 'DOE2411WEB', ?,?,?,?,?,?,?,?,?,?,?,?)";
            }
            statement = connection.prepareStatement(sql);
            // Journal Name
            statement.setString(1, journal);
            // Volume
            if (volume != null) {
                statement.setString(2, volume);
            } else {
                statement.setNull(2, Types.VARCHAR);
            }
            // Issue
            if (issue != null) {
                statement.setString(3, issue);
            } else {
                statement.setNull(3, Types.VARCHAR);
            }
            // DOI
            if (doi != null) {
                statement.setString(4, doi);
            } else {
                statement.setNull(4, Types.VARCHAR);
            }
            // Title
            statement.setString(5, title);
            // Publication Date
            if (publicationDate != null) {
                statement.setDate(6, new java.sql.Date(publicationDate.getTime()));
                statement.setNull(7, Types.VARCHAR);
            } else {
                statement.setNull(6, Types.DATE);
                statement.setString(7, publicationYearTP);
            }
            // Language(s)
            if (languages != null) {
                statement.setString(8, languages);
            } else {
                statement.setNull(8, Types.VARCHAR);
            }
            // Country Code
            if (countryCode != null) {
                statement.setString(9, countryCode);
            } else {
                statement.setNull(9, Types.VARCHAR);
            }
            // Sponsor(s)
            statement.setString(10, sponsors);
            // Availability
            statement.setString(11, availability);
            // Description/Abstract
            if (description != null) {
                /* NOTE: Database field is clob, but this code is not implemented in the driver!
                 Clob clob = connection.createClob();
                 clob.setString(0, description);
                 statement.setClob(14, clob);
                 */
                statement.setString(12, description);
            } else {
                statement.setNull(12, Types.CLOB);
            }
            //TODO process these 2 things when the database is modified
            // URLs to full text
            // Award IDs
            statement.executeUpdate();
            statement.close();
            if (ostiId == 0) {
                // Save the sequence used for the previous insert for the metadata_author_detail inserts
                statement = connection.prepareStatement("select currval('osti_id_seq')");
                ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    ostiId = resultSet.getInt(1);
                }
                statement.close();
                releaseFlag = 'N';
                json.put("ostiid", ostiId);
            } else {
                // Save the release_flag setting prior to deleting records
                sql = "select release_flag from nsf.metadata_author_detail where osti_id = " + ostiId;
                statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    releaseFlag = resultSet.getString("release_flag").charAt(0);
                }
                statement.close();
                // Delete all existing records from metadata_author_detail table with this OSTI ID
                sql = "delete from nsf.metadata_author_detail where osti_id = " + ostiId;
                statement = connection.prepareStatement(sql);
                statement.executeUpdate();
                statement.close();
            }
            // Insert all authors into metadata_author_detail table
            sql = "insert into nsf.metadata_author_detail (osti_id, direct_entry_flag, preferred_flag, " +
            "release_flag, place, first_name, middle_name, last_name, orcid_id, non_human_flag) " +
            "values (" + ostiId + ", true, true, ?,?,?,?,?,?,?)";
            statement = connection.prepareStatement(sql);
            for (Author author : authors) {
                // Release Flag
                statement.setObject(1, releaseFlag, Types.CHAR);
                // Place
                statement.setInt(2, author.position);
                // First name
                statement.setString(3, author.firstName);
                // Middle name
                if (author.middleName != null)
                    statement.setString(4, author.middleName);
                else
                    statement.setNull(4, Types.VARCHAR);
                // Last name
                statement.setString(5, author.lastName);
                // ORC ID
                if (author.orcId != null)
                    statement.setString(6, author.orcId);
                else
                    statement.setNull(6, Types.VARCHAR);
                // Non-human flag
                statement.setBoolean(7, false);
                statement.addBatch();
            }
            statement.executeBatch();
            response.setContentType("application/json");
            PrintWriter out = response.getWriter();
            out.write(json.toString());
            out.flush();
            out.close();
        } catch (Exception e) {
            log.severe(e.getMessage());
            response.sendError(500, "Exception: " + e.getMessage());
        } finally {
            if (connection != null)
                try {
                    connection.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            if (statement != null) {
                try {
                    statement.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}