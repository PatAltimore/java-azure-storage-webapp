<!DOCTYPE html>
<html lang="en">

<head>
<style type="text/css">
#dropdiv {
	width: 300px;
	padding: 20px;
	margin: 10px;
	border: 3px dashed lightblue;
	border-radius: 16px;
	padding: 20px;
}
</style>

<title>Image Resizer</title>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">

<script src="https://code.jquery.com/jquery-latest.min.js"
 type="text/javascript"></script>
<script type="text/javascript">
          
   	       $(document).ready(function() {  // Call the upload function to return JSON response with the blobs, iterate through JSON, create image elements
              $.get("list", function(responseJson) {    
                  $.each(responseJson, function(index, item) {
                      $("#imagediv").prepend("<div><img src='" + item + "' /></div>" );  
                  });
              });
          });
          
          function allowDrop(ev) {
              ev.preventDefault();
            }
          
          function drop(ev) {
              ev.preventDefault();
              document.getElementById("file").files = ev.dataTransfer.files;
            }
  </script>
</head>

<body>
 <h1>Image Resizer</h1>
 <hr>

 <h2>Upload photos</h2>
 <p>Select an image to upload:</p>

 <form method="POST" action="upload" enctype="multipart/form-data">
  <div id="dropdiv" ondrop="drop(event)" ondragover="allowDrop(event)">
   <input type="file" name="file" id="file" value="Choose file"
    accept="image/png, image/jpeg, image/gif" />
  </div>
  <input type="submit" value="Upload image" name="upload" id="upload" />
 </form>

 <h2>Generated thumbnails</h2>

 <div id="imagediv"></div>

 <hr>
 <p>This application has no official privacy policy. Your data is
  uploaded to a service to produce a thumbnail. Your images are public
  after upload. There is no automated way to remove them.</p>
</body>
</html>