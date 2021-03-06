package bio.terra.workspace.service.iam;

import bio.terra.workspace.app.configuration.SamConfiguration;
import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.common.exception.SamUnauthorizedException;
import bio.terra.workspace.common.utils.SamUtils;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SamService {
  private final SamConfiguration samConfig;

  @Autowired
  public SamService(SamConfiguration samConfig) {
    this.samConfig = samConfig;
  }

  private ApiClient getApiClient(String accessToken) {
    ApiClient client = new ApiClient();
    client.setAccessToken(accessToken);
    return client.setBasePath(samConfig.getBasePath());
  }

  private ResourcesApi samResourcesApi(String accessToken) {
    return new ResourcesApi(getApiClient(accessToken));
  }

  public void createWorkspaceWithDefaults(String authToken, UUID id) {
    ResourcesApi resourceApi = samResourcesApi(authToken);
    try {
      resourceApi.createResourceWithDefaults(SamUtils.SAM_WORKSPACE_RESOURCE, id.toString());
    } catch (ApiException apiException) {
      throw new SamApiException(apiException);
    }
  }

  public void deleteWorkspace(String authToken, UUID id) {
    ResourcesApi resourceApi = samResourcesApi(authToken);
    try {
      resourceApi.deleteResource(SamUtils.SAM_WORKSPACE_RESOURCE, id.toString());
    } catch (ApiException apiException) {
      throw new SamApiException(apiException);
    }
  }

  public boolean isAuthorized(
      String accessToken, String iamResourceType, String resourceId, String action) {
    ResourcesApi resourceApi = samResourcesApi(accessToken);
    try {
      return resourceApi.resourceAction(iamResourceType, resourceId, action);
    } catch (ApiException samException) {
      throw new SamApiException(samException);
    }
  }

  public void workspaceAuthz(AuthenticatedUserRequest userReq, String workspaceId, String action) {
    boolean isAuthorized =
        isAuthorized(
            userReq.getRequiredToken(), SamUtils.SAM_WORKSPACE_RESOURCE, workspaceId, action);
    if (!isAuthorized)
      throw new SamUnauthorizedException(
          "User "
              + userReq.getEmail()
              + " is not authorized to "
              + action
              + " workspace "
              + workspaceId
              + " or it doesn't exist.");
  }
}
