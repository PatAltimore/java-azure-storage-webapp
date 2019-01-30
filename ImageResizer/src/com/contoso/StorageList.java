package com.contoso;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.microsoft.azure.storage.blob.ContainerURL;
import com.microsoft.azure.storage.blob.ListBlobsOptions;
import com.microsoft.azure.storage.blob.PipelineOptions;
import com.microsoft.azure.storage.blob.ServiceURL;
import com.microsoft.azure.storage.blob.SharedKeyCredentials;
import com.microsoft.azure.storage.blob.StorageURL;
import com.microsoft.azure.storage.blob.models.BlobItem;
import com.microsoft.azure.storage.blob.models.ContainerCreateResponse;
import com.microsoft.azure.storage.blob.models.ContainerListBlobFlatSegmentResponse;
import com.microsoft.rest.v2.RestException;

import io.reactivex.Single;

@WebServlet(name = "StorageList", urlPatterns = {"/list"}, asyncSupported=true)
public class StorageList extends HttpServlet {

    private static final long serialVersionUID = -3588358829210334965L;
    
    // Read storage environment variables
    private static String AZURE_STORAGE_CONFIG_ACCOUNT_NAME = System.getenv("AzureStorageConfig__AccountName");
    private static String AZURE_STORAGE_CONFIG_ACCOUNT_KEY = System.getenv("AzureStorageConfig__AccountKey");
    private static String AZURE_STORAGE_CONFIG_THUMBNAIL_CONTAINER = System.getenv("AzureStorageConfig__ThumbnailContainer");
    private static String AZURE_STORAGE_SERVICE_URL = "https://" + AZURE_STORAGE_CONFIG_ACCOUNT_NAME + ".blob.core.windows.net";

    private final static Logger LOGGER = Logger.getLogger(StorageList.class.getCanonicalName());

    /**
     * Process file upload request
     */

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        ArrayList<String> blobList = new ArrayList<>();

        // Use async to get a list of blob URLs of the photos

        final AsyncContext aCtx = request.startAsync();

        aCtx.setTimeout(50000);

        aCtx.addListener(new AsyncListener() {

            // On complete, Convert the blob list to JSON response
            @Override
            public void onComplete(AsyncEvent event) throws IOException {
                ServletResponse response = aCtx.getResponse();

                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");

                String json = new Gson().toJson(blobList);

                PrintWriter pw = null;

                try {
                    pw = aCtx.getResponse().getWriter();

                    pw.print(json);
                    pw.flush();

                } catch (IOException e) {
                    LOGGER.log(Level.INFO, "Couldn't get async context repsonse: {0}", new Object[] { e });
                }

                LOGGER.log(Level.INFO, "Response JSON: {0}", new Object[] { json });
            }

            @Override
            public void onTimeout(AsyncEvent event) throws IOException {
                LOGGER.log(Level.INFO, "onTimeout event");

            }

            @Override
            public void onError(AsyncEvent event) throws IOException {
                LOGGER.log(Level.INFO, "onError");

            }

            @Override
            public void onStartAsync(AsyncEvent event) throws IOException {
                LOGGER.log(Level.INFO, "onStartAsync");

            }

        });

        aCtx.start(new Runnable() {

            @Override
            public void run() {

                ContainerURL containerURL = getThumbnailContainerUrl();

                if (containerURL != null) {
                    ListBlobsOptions options = new ListBlobsOptions();
                    options.withMaxResults(10);

                    containerURL.listBlobsFlatSegment(null, options, null)
                            .flatMap(containerListBlobFlatSegmentResponse -> listAllBlobs(containerURL,
                                    containerListBlobFlatSegmentResponse, blobList))
                            .subscribe(response -> {

                                LOGGER.log(Level.INFO, "Completed list blobs request. Status code: {0}",
                                        new Object[] { response.statusCode() });

                                aCtx.complete();

                            });

                } else {
                    aCtx.complete();
                }
            }
        });
    }

    private static Single<ContainerListBlobFlatSegmentResponse> listAllBlobs(ContainerURL url,
            ContainerListBlobFlatSegmentResponse response, ArrayList<String> blobList) {
        
       
        // Process the blobs returned in this result segment (if the segment is empty,
        // blobs() will be null.
        if (response.body().segment() != null) {
            for (BlobItem b : response.body().segment().blobItems()) {

                blobList.add(AZURE_STORAGE_SERVICE_URL + "/" + AZURE_STORAGE_CONFIG_THUMBNAIL_CONTAINER + "/"
                        + b.name());

                LOGGER.log(Level.INFO, "Blob name: {0}", new Object[] { b.name() });
            }
        } else {
            LOGGER.log(Level.INFO, "There are no more blobs to list off.");
        }

        // If there is not another segment, return this response as the final response.
        if (response.body().nextMarker() == null) {
            return Single.just(response);
        } else {
            /*
             * IMPORTANT: ListBlobsFlatSegment returns the start of the next segment; you
             * MUST use this to get the next segment (after processing the current result
             * segment
             */

            String nextMarker = response.body().nextMarker();

            /*
             * The presence of the marker indicates that there are more blobs to list, so we
             * make another call to listBlobsFlatSegment and pass the result through this
             * helper function.
             */

            return url.listBlobsFlatSegment(nextMarker, new ListBlobsOptions().withMaxResults(10), null)
                    .flatMap(containersListBlobFlatSegmentResponse -> listAllBlobs(url,
                            containersListBlobFlatSegmentResponse, blobList));
        }
    }

    /**
     * Handles HTTP GET. Returns a JSON response containing a list of blobs
     */

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);

    }

    /**
     * Handles the HTTP POST
     */

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Servlet that lists files from an Azure Storage container";
    }

    public static ContainerURL getThumbnailContainerUrl()
    {
   
        // Initialize SharedKeyCredentials and create a ServiceURL to call the Blob service. We will also use this to construct the ContainerURL
        SharedKeyCredentials creds;
        try {
            creds = new SharedKeyCredentials(AZURE_STORAGE_CONFIG_ACCOUNT_NAME, AZURE_STORAGE_CONFIG_ACCOUNT_KEY);
    
            // We are using a default pipeline here, you can learn more about it at https://github.com/Azure/azure-storage-java/wiki/Azure-Storage-Java-V10-Overview
            final ServiceURL serviceURL = new ServiceURL(new URL(AZURE_STORAGE_SERVICE_URL), StorageURL.createPipeline(creds, new PipelineOptions()));
    
            // Let's create a container using a blocking call to Azure Storage
            // If container exists, we'll catch and continue
            ContainerURL containerURL = serviceURL.createContainerURL(AZURE_STORAGE_CONFIG_THUMBNAIL_CONTAINER);
    
            try {
                ContainerCreateResponse createResponse = containerURL.create(null, null, null).blockingGet();
                LOGGER.log(Level.INFO, "Container Create Response was {0}",
                        new Object[]{createResponse.statusCode()});
                
            } catch (RestException e){
                if (e instanceof RestException && ((RestException)e).response().statusCode() != 409) {
                    throw e;
                } else {
                    LOGGER.log(Level.INFO, "Storage tutorial container already exists.");
                }
            }
            
            return containerURL;
            
        } catch (InvalidKeyException e) {
    
            LOGGER.log(Level.INFO, "Invalid Azure credential key exception. Error: {0}",
                    new Object[]{e.getMessage()});
        } catch (MalformedURLException e1) {
            LOGGER.log(Level.INFO, "MalformedURL error: {0}",
                    new Object[]{e1.getMessage()});
        }
        
        return null;
    }
}
