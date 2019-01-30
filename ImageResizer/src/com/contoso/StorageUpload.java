package com.contoso;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.AsynchronousFileChannel;
import java.security.InvalidKeyException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import com.microsoft.azure.storage.blob.BlockBlobURL;
import com.microsoft.azure.storage.blob.ContainerURL;
import com.microsoft.azure.storage.blob.PipelineOptions;
import com.microsoft.azure.storage.blob.ServiceURL;
import com.microsoft.azure.storage.blob.SharedKeyCredentials;
import com.microsoft.azure.storage.blob.StorageURL;
import com.microsoft.azure.storage.blob.TransferManager;
import com.microsoft.azure.storage.blob.models.ContainerCreateResponse;
import com.microsoft.rest.v2.RestException;

@WebServlet(name = "StorageUpload", urlPatterns = {"/upload"})
@MultipartConfig(fileSizeThreshold=1024*1024*10,       // 10 MB 
                    maxFileSize=1024*1024*50,          // 50 MB
                    maxRequestSize=1024*1024*100)      // 100 MB
public class StorageUpload extends HttpServlet {

    private static final String IMAGE_DIR = "images";
    private final static Logger LOGGER = Logger.getLogger(StorageUpload.class.getCanonicalName());

    private static final long serialVersionUID = -1512672775341133036L;
    
    // Read storage environment variables
    private static String AZURE_STORAGE_CONFIG_ACCOUNT_NAME = System.getenv("AzureStorageConfig__AccountName");
    private static String AZURE_STORAGE_CONFIG_ACCOUNT_KEY = System.getenv("AzureStorageConfig__AccountKey");
    private static String AZURE_STORAGE_CONFIG_IMAGE_CONTAINER = System.getenv("AzureStorageConfig__ImageContainer");
    private static String AZURE_STORAGE_SERVICE_URL = "https://" + AZURE_STORAGE_CONFIG_ACCOUNT_NAME + ".blob.core.windows.net";

    /**
     * Handles the HTTP POST
     */

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        // Create directory to save images if it doesn't exist
        String imageFilePath = request.getServletContext().getRealPath("") + File.separator + IMAGE_DIR;

        File fileSaveDir = new File(imageFilePath);
        if (!fileSaveDir.exists()) {
            fileSaveDir.mkdirs();
        }

        // Get the Azure Storage container URL
        ContainerURL containerURL = getImageContainerUrl();
 
        if (containerURL != null) {

            // Loop through parts from request and write files on the server
            try {
                for (Part part : request.getParts()) {
                    String fileName = getFileName(part);

                    if (fileName != null && !fileName.isEmpty()) // If there's a file, upload
                    {
                        String filePath = imageFilePath + File.separator + fileName;
                        part.write(filePath);
                        LOGGER.log(Level.INFO, "File uploaded and saved to server: {0}", filePath);

                        // Create a BlockBlobURL to run operations on Blobs
                        BlockBlobURL blobURL = containerURL.createBlockBlobURL(fileName);

                        // Upload to Azure Storage
                        File serverFile = new File(filePath);

                        AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(serverFile.toPath());
                        TransferManager.uploadFileToBlockBlob(fileChannel, blobURL, 8 * 1024 * 1024, null)
                                .subscribe(response2 -> {
                                    LOGGER.log(Level.INFO, "File {0} uploaded to Azure storage. Status code: {1}",
                                            new Object[] { fileSaveDir, response2.response().statusCode() });
                                });
                    }
                }
            } catch (ServletException e) {
                LOGGER.log(Level.INFO, "Invalid upload. Error: {0}", new Object[] { e.getMessage() });
            }
        }
        response.sendRedirect("index.jsp");

    }
    
    /**
     * Handles HTTP GET. Returns a JSON response containing a list of blobs
     */

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendRedirect("index.jsp");
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Servlet that uploads files to an Azure Storage container.";
    }

    private String getFileName(final Part part) {
        final String partHeader = part.getHeader("content-disposition");
        LOGGER.log(Level.INFO, "Part Header = {0}", partHeader);
        for (String content : part.getHeader("content-disposition").split(";")) {
            if (content.trim().startsWith("filename")) {
                String fileName = content.substring(content.indexOf('=') + 1).trim().replace("\"", "");

                if (fileName.contains("\\")) // If local file, remove path
                    fileName = fileName.substring(fileName.lastIndexOf('\\') + 1).trim();

                return fileName;
            }
        }
        return null;
    }
    
    public static ContainerURL getImageContainerUrl()
    {
   
        // Initialize SharedKeyCredentials and create a ServiceURL to call the Blob service. We will also use this to construct the ContainerURL
        SharedKeyCredentials creds;
        try {
            creds = new SharedKeyCredentials(AZURE_STORAGE_CONFIG_ACCOUNT_NAME, AZURE_STORAGE_CONFIG_ACCOUNT_KEY);
    
            // We are using a default pipeline here, you can learn more about it at https://github.com/Azure/azure-storage-java/wiki/Azure-Storage-Java-V10-Overview
            final ServiceURL serviceURL = new ServiceURL(new URL(AZURE_STORAGE_SERVICE_URL), StorageURL.createPipeline(creds, new PipelineOptions()));
    
            // Let's create a container using a blocking call to Azure Storage
            // If container exists, we'll catch and continue
            ContainerURL containerURL = serviceURL.createContainerURL(AZURE_STORAGE_CONFIG_IMAGE_CONTAINER);
    
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
