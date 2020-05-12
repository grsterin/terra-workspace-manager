package bio.terra.workspace.db;

import static bio.terra.workspace.db.generated.tables.WorkspaceDataReference.*;
import static bio.terra.workspace.db.generated.tables.WorkspaceResource.*;

import bio.terra.workspace.common.exception.DataReferenceNotFoundException;
import bio.terra.workspace.db.generated.tables.WorkspaceDataReference;
import bio.terra.workspace.db.generated.tables.WorkspaceResource;
import bio.terra.workspace.generated.model.DataReferenceDescription;
import bio.terra.workspace.generated.model.DataReferenceList;
import bio.terra.workspace.generated.model.ResourceDescription;
import java.util.List;
import java.util.UUID;
import org.jooq.*;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DataReferenceDao {

  private final DSLContext dslContext;

  private static final WorkspaceDataReference referenceTable = WORKSPACE_DATA_REFERENCE.as("referenceTable");
  private static final WorkspaceResource resourceTable = WORKSPACE_RESOURCE.as("resourceTable");

  @Autowired
  public DataReferenceDao(DSLContext dslContext) {
    this.dslContext = dslContext;
  }

  public String createDataReference(
      UUID referenceId,
      UUID workspaceId,
      String name,
      JsonNullable<UUID> resourceId,
      JsonNullable<String> credentialId,
      String cloningInstructions,
      JsonNullable<String> referenceType,
      JsonNullable<String> reference) {

    dslContext
        .insertInto(referenceTable)
        .columns(
            referenceTable.WORKSPACE_ID,
            referenceTable.REFERENCE_ID,
            referenceTable.NAME,
            referenceTable.CLONING_INSTRUCTIONS,
            referenceTable.CREDENTIAL_ID,
            referenceTable.RESOURCE_ID,
            referenceTable.REFERENCE_TYPE,
            referenceTable.REFERENCE)
        .values(
            workspaceId.toString(),
            referenceId.toString(),
            name,
            cloningInstructions,
            credentialId.orElse(null),
            resourceId.isPresent() ? resourceId.get().toString() : null,
            referenceType.orElse(null),
            JSON.valueOf(reference.orElse(null)))
        .execute();

    return referenceId.toString();
  }

  public DataReferenceDescription getDataReference(UUID referenceId) {
    DataReferenceDescription dataReference =
        dslContext
            .selectFrom(referenceTable)
            .where(referenceTable.REFERENCE_ID.eq(referenceId.toString()))
            .fetchOne(new DataReferenceMapper());

    if (dataReference != null) {
      return dataReference;
    } else {
      throw new DataReferenceNotFoundException("Data Reference not found.");
    }
  }

  public boolean isControlled(UUID referenceId) {
    Record1<String> rec =
        dslContext
            .select(referenceTable.RESOURCE_ID)
            .from(referenceTable)
            .where(referenceTable.REFERENCE_ID.eq(referenceId.toString()))
            .fetchOne();
    if (rec != null) {
      return rec.get(referenceTable.RESOURCE_ID) != null;
    } else {
      throw new DataReferenceNotFoundException("Data Reference not found.");
    }
  }

  public boolean deleteDataReference(UUID referenceId) {
    int rowsAffected =
        dslContext
            .delete(referenceTable)
            .where(referenceTable.REFERENCE_ID.eq(referenceId.toString()))
            .execute();
    return rowsAffected > 0;
  }

  public DataReferenceList enumerateDataReferences(
      String workspaceId, String owner, int offset, int limit) {
    List<DataReferenceDescription> resultList =
        dslContext
            .select()
            .from(referenceTable)
            .leftOuterJoin(resourceTable)
            .on(referenceTable.RESOURCE_ID.eq(resourceTable.RESOURCE_ID))
            .where(referenceTable.WORKSPACE_ID.eq(workspaceId))
            .and(
                referenceTable
                    .RESOURCE_ID
                    .isNull()
                    .or(resourceTable.IS_VISIBLE.eq(true).or(resourceTable.OWNER.eq(owner))))
            .orderBy(referenceTable.REFERENCE_ID)
            .offset(offset)
            .limit(limit)
            .fetch(new DataReferenceMapper());
    return new DataReferenceList().resources(resultList);
  }

  private static class ResourceDescriptionMapper implements RecordMapper<Record, ResourceDescription> {
    @Override
    public ResourceDescription map(Record rec) {
      return new ResourceDescription()
          .workspaceId(UUID.fromString(rec.get(resourceTable.WORKSPACE_ID)))
          .resourceId(UUID.fromString(rec.get(resourceTable.RESOURCE_ID)))
          .isVisible(rec.get(resourceTable.IS_VISIBLE))
          .owner(rec.get(resourceTable.OWNER))
          .attributes(rec.get(resourceTable.ATTRIBUTES).toString());
    }
  }

  private static class DataReferenceMapper
      implements RecordMapper<Record, DataReferenceDescription> {
    @Override
    public DataReferenceDescription map(Record rec) {
      ResourceDescriptionMapper resourceDescriptionMapper = new ResourceDescriptionMapper();
      String resourceId = rec.get(referenceTable.RESOURCE_ID);
      return new DataReferenceDescription()
          .workspaceId(UUID.fromString(rec.get(referenceTable.WORKSPACE_ID)))
          .referenceId(UUID.fromString(rec.get(referenceTable.REFERENCE_ID)))
          .name(rec.get(referenceTable.NAME))
          .resourceDescription(resourceId == null ? null : resourceDescriptionMapper.map(rec))
          .credentialId(rec.get(referenceTable.CREDENTIAL_ID))
          .cloningInstructions(
              DataReferenceDescription.CloningInstructionsEnum.fromValue(
                  rec.get(referenceTable.CLONING_INSTRUCTIONS)))
          .referenceType(
              DataReferenceDescription.ReferenceTypeEnum.fromValue(
                  rec.get(referenceTable.REFERENCE_TYPE)))
          .reference(rec.get(referenceTable.REFERENCE).toString());
    }
  }
}
