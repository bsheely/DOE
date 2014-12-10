package gov.osti.elink.servlet;

import com.google.gson.Gson;
import gov.osti.elink.database.UtilLookup;
import gov.osti.framework.dbi.Dbi;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Logger;

/**
 * Return the metadata for the given OSTI ID
 * Created by sheelyb on 12/9/2014.
 */
public class Metadata extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = Logger.getLogger(Metadata.class.getName());
    private int ostiId;

    private class Data {
        String description;
        int ostiID;
        String awardIDs;        // multiples delimited by semicolon space
        String title;
        String doi;
        String fileName;
        String journalVolume;
        String journalIssue;
        String journalName;
        String journalSerialID;
        String journalType;
        String journalRange;
        Date journalSunsetDate;
        String language;
        String country;
        String identifyingNumbers;
        String productType;
        Date publicationDate;
        String inputType;
        String sponsorOrg;
        Date systemEntryDate;
        String ackPeerReview;
        String ackFedFund;
        String ackGovLic;
        Date date_first_submitted_to_osti;
        Date date_last_submitted_to_osti;
        Date date_released_record_updated;
        String workflowStage;
        String workflowStatus;
        Author[] authors;
    }

    private class Author {
        String fullName;        // last, first middle
        String orcid;
        int place;
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
        JSONObject json = new JSONObject(stringBuilder.toString());
        if (!json.has("osti_id") || json.getString("osti_id").isEmpty()) {
            response.sendError(206, "JSON missing required content");
            return;
        }
        // OSTI ID
        ostiId = Integer.parseInt(json.getString("osti_id"));
        processRequest(response);
    }

    /**
     * Returns a short description of the servlet.
     */
    public String getServletInfo() {
        return "Gets the metadata for the given OSTI ID";
    }

    private void processRequest(HttpServletResponse response) throws IOException {
        PreparedStatement statement = null;
        Connection connection = null;
        Data data = new Data();
        Gson gson = new Gson();
        try {
            connection = Dbi.getConnection();
            String sql = "select * from nsf.metadata where osti_id = " + ostiId;
            statement = connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                UtilLookup utilLookup = new UtilLookup(connection);
                data.description = resultSet.getString("description");
                data.ostiID = ostiId;
                data.awardIDs = "DOES NOT EXIST";                                           // Does not exist in database
                data.title = resultSet.getString("title");
                data.doi = resultSet.getString("doi");
                data.fileName = resultSet.getString("file_name");
                data.journalVolume = resultSet.getString("journal_volume");
                data.journalIssue = resultSet.getString("journal_issue");
                data.journalName = resultSet.getString("journal_name");
                data.journalSerialID = resultSet.getString("journal_serial_id");
                data.journalType = utilLookup.getJournalType(resultSet.getString("journal_type"));
                data.journalRange = resultSet.getString("product_size");
                data.journalSunsetDate = resultSet.getDate("journal_fulltext_embargo_sunset_date");
                data.language = resultSet.getString("language");
                data.country = utilLookup.getCountry(resultSet.getString("country_publication_code"));
                data.identifyingNumbers = resultSet.getString("other_identifying_nos");
                data.productType = utilLookup.getProductType(resultSet.getString("product_type"));
                data.publicationDate = resultSet.getDate("publication_date");
                data.inputType = utilLookup.getSourceInputType(resultSet.getString("source_input_type"));
                data.sponsorOrg = resultSet.getString("sponsor_org");
                data.systemEntryDate = resultSet.getDate("date_osti_id_assigned");
                data.ackPeerReview = "DOES NOT EXIST";                                      // Does not exist in database
                data.ackFedFund = "DOES NOT EXIST";                                         // Does not exist in database
                data.ackGovLic = "DOES NOT EXIST";                                          // Does not exist in database
                data.date_first_submitted_to_osti = resultSet.getDate("date_first_submitted_to_osti");
                data.date_last_submitted_to_osti = resultSet.getDate("date_last_submitted_to_osti");
                data.date_released_record_updated = null;                                   // Does not exist in database
                data.workflowStage = "DOES NOT EXIST";                                      // Does not exist in database
                data.workflowStatus = utilLookup.getWorkflowStatus(resultSet.getString("workflow_status"));
            }
            resultSet.close();
            sql = "select * from nsf.metadata_author_detail where osti_id = " + ostiId;
            statement = connection.prepareStatement(sql);
            resultSet = statement.executeQuery();
            ArrayList<Author> authors = new ArrayList<>();
            while (resultSet.next()) {
                String middleName = resultSet.getString("middle_name") != null ? " " + resultSet.getString("middle_name") : "";
                Author author = new Author();
                author.fullName = resultSet.getString("last_name") + ", " + resultSet.getString("first_name") + middleName;
                author.orcid = resultSet.getString("orcid_id");
                author.place = resultSet.getInt("place");
                authors.add(author);
            }
            data.authors = authors.toArray(new Author[authors.size()]);
        } catch (Exception e) {
            log.severe(e.getMessage());
            response.sendError(500, "Exception: " + e.getMessage());
        } finally {
            if (connection != null)
                try {
                    connection.close();
                } catch (Exception e) {
                    log.severe(e.getMessage());
                }
            if (statement != null) {
                try {
                    statement.close();
                } catch (Exception e) {
                    log.severe(e.getMessage());
                }
            }
        }
        String output = gson.toJson(data);
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        out.write(output);
        out.flush();
        out.close();
    }
}