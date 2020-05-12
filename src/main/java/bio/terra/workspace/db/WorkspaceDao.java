package bio.terra.workspace.db;

import static bio.terra.workspace.db.generated.tables.Workspace.*;

import bio.terra.workspace.common.exception.DuplicateWorkspaceException;
import bio.terra.workspace.common.exception.WorkspaceNotFoundException;
import bio.terra.workspace.db.generated.tables.records.WorkspaceRecord;
import bio.terra.workspace.generated.model.WorkspaceDescription;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.SQLStateClass;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WorkspaceDao {
  private final DSLContext dslContext;

  @Autowired
  public WorkspaceDao(DSLContext dslContext) {
    this.dslContext = dslContext;
  }

  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public String createWorkspace(UUID workspaceId, JsonNullable<UUID> spendProfile) {
    try {
      dslContext
          .insertInto(WORKSPACE)
          .columns(WORKSPACE.WORKSPACE_ID, WORKSPACE.SPEND_PROFILE, WORKSPACE.PROFILE_SETTABLE)
          .values(
              workspaceId.toString(),
              spendProfile.isPresent() ? spendProfile.get().toString() : null,
              !spendProfile.isPresent())
          .execute();
    } catch (DataAccessException ex) {
      if (ex.sqlStateClass() == SQLStateClass.C23_INTEGRITY_CONSTRAINT_VIOLATION) {
        throw new DuplicateWorkspaceException("Workspace " + workspaceId.toString() + " already exists.", ex);
      }
      throw ex;
    }

    return workspaceId.toString();
  }

  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public boolean deleteWorkspace(UUID workspaceId) {
    int rowsAffected =
        dslContext
            .delete(WORKSPACE)
            .where(WORKSPACE.WORKSPACE_ID.eq(workspaceId.toString()))
            .execute();
    return rowsAffected > 0;
  }

  public WorkspaceDescription getWorkspace(String id) {
    WorkspaceRecord rec =
        dslContext.selectFrom(WORKSPACE).where(WORKSPACE.WORKSPACE_ID.eq(id)).fetchOne();
    if (rec != null) {
      WorkspaceDescription desc = new WorkspaceDescription();
      desc.setId(UUID.fromString(rec.getWorkspaceId()));
      desc.setSpendProfile(
          rec.getSpendProfile() != null
              ? JsonNullable.of(UUID.fromString(rec.getSpendProfile()))
              : JsonNullable.undefined());
      return desc;
    } else {
      throw new WorkspaceNotFoundException("Workspace not found.");
    }
  }
}
