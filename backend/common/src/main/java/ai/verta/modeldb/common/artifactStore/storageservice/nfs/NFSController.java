package ai.verta.modeldb.common.artifactStore.storageservice.nfs;

import ai.verta.modeldb.common.exceptions.ModelDBException;
import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.protobuf.StatusProto;
import java.io.IOException;
import java.io.InputStream;
import javax.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class NFSController {

  private static final Logger LOGGER = LogManager.getLogger(NFSController.class);

  @Autowired private NFSService nfsService;

  @PutMapping(value = {"${artifactEndpoint.storeArtifact}"})
  public UploadFileResponse storeArtifact(
      HttpServletRequest requestEntity, @RequestParam("artifact_path") String artifactPath)
      throws ModelDBException, IOException {
    try {
      InputStream inputStream = requestEntity.getInputStream();
      String fileName = nfsService.storeFile(artifactPath, inputStream, requestEntity);
      LOGGER.trace("storeArtifact - file name : {}", fileName);
      LOGGER.debug("storeArtifact returned");
      return new UploadFileResponse(fileName, null, null, -1L, null);
    } catch (ModelDBException e) {
      LOGGER.info(e.getMessage(), e);
      var status =
          Status.newBuilder().setCode(e.getCode().value()).setMessage(e.getMessage()).build();
      throw StatusProto.toStatusRuntimeException(status);
    } catch (Exception e) {
      LOGGER.warn(e.getMessage(), e);
      var status =
          Status.newBuilder().setCode(Code.INTERNAL_VALUE).setMessage(e.getMessage()).build();
      throw StatusProto.toStatusRuntimeException(status);
    }
  }

  @GetMapping(value = {"${artifactEndpoint.getArtifact}/{FileName}"})
  public ResponseEntity<Resource> getArtifact(
      @PathVariable(value = "FileName") String fileName,
      @RequestParam("artifact_path") String artifactPath,
      HttpServletRequest request)
      throws ModelDBException {
    try {
      // Load file as Resource
      var resource = nfsService.loadFileAsResource(artifactPath);

      // Try to determine file's content type
      String contentType = getContentType(request, resource);

      // Fallback to the default content type if type could not be determined
      if (contentType == null) {
        contentType = "application/octet-stream";
      }
      LOGGER.trace("getArtifact - file content type : {}", contentType);

      LOGGER.debug("getArtifact returned");
      return ResponseEntity.ok()
          .contentType(MediaType.parseMediaType(contentType))
          .header(
              HttpHeaders.CONTENT_DISPOSITION,
              "attachment; filename=\"" + resource.getFilename() + "\"")
          .header("FileName", fileName)
          .body(resource);
    } catch (ModelDBException e) {
      LOGGER.info(e.getMessage(), e);
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  public String getContentType(HttpServletRequest request, Resource resource) {
    try {
      return request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
    } catch (Exception ex) {
      LOGGER.info("Could not determine file type.");
      return null;
    }
  }
}
